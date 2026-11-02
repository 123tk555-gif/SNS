# 公開用のサーバー(EC2)を作る。最初に 1 回だけ動かす。setup.ps1 と set-secrets.ps1 のあとで動かす。
# 使い方: aws sso login --profile taskboard のあとで  .\create-server.ps1
#
# 作るもの:
#  - セキュリティグループ sns-web: 外から入れるのは 80・443 番(HTTP・HTTPS)だけ。
#    SSH(22 番)は開けない。サーバーの中で作業するときは、SSM の「セッションマネージャー」を使う
#  - EC2 t4g.micro(Amazon Linux 2023・ARM)・ディスク 12GB。IAM ロール sns-ec2-profile を付ける。
#    コンテナの中から IAM ロールを使えるよう、メタデータの「ホップ数の上限」を 2 にする
#  - 最初の起動で user-data.sh が動き、Docker を入れて、アプリを作って起動する(15〜25 分)
#
# 料金: 動かしている間だけ 1 時間あたり約 2 円(サーバー + IP アドレス)。止めている間はディスクの分だけ(月 約 1.2 ドル)
param(
    [string]$Profile_ = 'taskboard',
    [string]$Region = 'ap-northeast-1'
)
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$aws = (Get-Command aws -ErrorAction SilentlyContinue).Source
if (-not $aws) { $aws = 'C:\Program Files\Amazon\AWSCLIV2\aws.exe' }

function Invoke-Aws {
    $out = & $aws @args --profile $Profile_ --region $Region --output json
    if ($LASTEXITCODE -ne 0) { throw "aws $($args -join ' ') が失敗しました" }
    if ($out) { $out | Out-String | ConvertFrom-Json }
}

# すでにあれば作らない(2 台にしない)
$existing = Invoke-Aws ec2 describe-instances --filters 'Name=tag:Project,Values=sns' `
    'Name=instance-state-name,Values=pending,running,stopping,stopped' --query 'Reservations[].Instances[].InstanceId'
if ($existing) { throw "SNS のサーバーはすでにあります: $($existing -join ', ')(start.ps1 で起動する)" }

# 設定が入っているか(boot.sh が読む)
$names = (Invoke-Aws ssm get-parameters-by-path --path /sns --query 'Parameters[].Name')
foreach ($required in 's3-bucket', 'duckdns-domain', 'duckdns-token', 'mail-username', 'mail-password') {
    if ($names -notcontains "/sns/$required") { throw "パラメータストアに /sns/$required がありません(setup.ps1・set-secrets.ps1 を先に動かす)" }
}
$domain = (Invoke-Aws ssm get-parameter --name /sns/duckdns-domain --query 'Parameter.Value')

# ---- セキュリティグループ(既定の VPC に作る) ----
$vpc = Invoke-Aws ec2 describe-vpcs --filters 'Name=is-default,Values=true' --query 'Vpcs[0].VpcId'
if (-not $vpc) { throw '既定の VPC がありません' }
$sg = Invoke-Aws ec2 describe-security-groups --filters 'Name=group-name,Values=sns-web' "Name=vpc-id,Values=$vpc" `
    --query 'SecurityGroups[0].GroupId'
if (-not $sg) {
    $sg = Invoke-Aws ec2 create-security-group --group-name sns-web --vpc-id $vpc `
        --description 'SNS: HTTP and HTTPS only' --query 'GroupId'
    foreach ($port in 80, 443) {
        Invoke-Aws ec2 authorize-security-group-ingress --group-id $sg --protocol tcp --port $port --cidr '0.0.0.0/0' | Out-Null
    }
    Write-Host "セキュリティグループ sns-web($sg)を作りました(80・443 番だけ)"
}

# ---- サーバー ----
$ami = Invoke-Aws ssm get-parameter --name '/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64' `
    --query 'Parameter.Value'
Write-Host "サーバーを作ります(Amazon Linux 2023 $ami / t4g.micro)..."
$id = Invoke-Aws ec2 run-instances --image-id $ami --instance-type t4g.micro `
    --iam-instance-profile 'Name=sns-ec2-profile' --security-group-ids $sg `
    --metadata-options 'HttpTokens=required,HttpPutResponseHopLimit=2,HttpEndpoint=enabled' `
    --block-device-mappings 'DeviceName=/dev/xvda,Ebs={VolumeSize=12,VolumeType=gp3,DeleteOnTermination=true,Encrypted=true}' `
    --user-data "file://$here\user-data.sh" `
    --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=sns},{Key=Project,Value=sns}]' `
    'ResourceType=volume,Tags=[{Key=Name,Value=sns},{Key=Project,Value=sns}]' `
    --query 'Instances[0].InstanceId'
Write-Host "サーバー $id を作りました。起動を待っています..."
& $aws ec2 wait instance-running --instance-ids $id --profile $Profile_ --region $Region
if ($LASTEXITCODE -ne 0) { throw '起動の確認に失敗しました' }
$ip = Invoke-Aws ec2 describe-instances --instance-ids $id --query 'Reservations[0].Instances[0].PublicIpAddress'
Write-Host "IP アドレス: $ip"

# ---- 準備が終わるのを待つ(Docker を入れて、アプリのイメージを作るので時間がかかる) ----
$url = "https://$domain.duckdns.org"
Write-Host "$url が開けるようになるまで待ちます(15〜25 分。途中で止めても、サーバーでは準備が続きます)..."
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 30
    try {
        $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 "$url/api/auth/config"
        if ($r.StatusCode -eq 200) { Write-Host ''; Write-Host "公開しました: $url/"; exit 0 }
    } catch { Write-Host -NoNewline '.' }
}
Write-Host ''
Write-Host "まだ開けません。しばらく待ってから $url/ を開くか、README の「うまくいかないとき」を見てください。"
