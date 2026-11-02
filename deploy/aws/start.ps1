# 公開用のサーバー(EC2)を起動する。起動すると、サーバーが自分で DuckDNS のアドレスを新しい IP に向け、アプリを起動する。
# 使い方: aws sso login --profile taskboard のあとで  .\start.ps1
#         アプリを GitHub の最新にするときは      .\start.ps1 -Update(イメージを作り直すので 10〜20 分)
# 動かしている間は料金がかかる(1 時間あたり約 2 円)。使い終わったら stop.ps1 で止める。
param(
    [string]$Profile_ = 'taskboard',
    [string]$Region = 'ap-northeast-1',
    [switch]$Update
)
$ErrorActionPreference = 'Stop'
$aws = (Get-Command aws -ErrorAction SilentlyContinue).Source
if (-not $aws) { $aws = 'C:\Program Files\Amazon\AWSCLIV2\aws.exe' }

function Invoke-Aws {
    $out = & $aws @args --profile $Profile_ --region $Region --output json
    if ($LASTEXITCODE -ne 0) { throw "aws $($args -join ' ') が失敗しました" }
    if ($out) { $out | Out-String | ConvertFrom-Json }
}

$id = Invoke-Aws ec2 describe-instances --filters 'Name=tag:Project,Values=sns' `
    'Name=instance-state-name,Values=pending,running,stopping,stopped' --query 'Reservations[0].Instances[0].InstanceId'
if (-not $id) { throw 'SNS のサーバーが見つかりません(create-server.ps1 で作る)' }
$domain = Invoke-Aws ssm get-parameter --name /sns/duckdns-domain --query 'Parameter.Value'
$url = "https://$domain.duckdns.org"

Write-Host "サーバー $id を起動します..."
Invoke-Aws ec2 start-instances --instance-ids $id | Out-Null
& $aws ec2 wait instance-running --instance-ids $id --profile $Profile_ --region $Region
if ($LASTEXITCODE -ne 0) { throw '起動の確認に失敗しました' }

if ($Update) {
    # サーバーへ指示を送れる(SSM)ようになるまで待つ
    for ($i = 0; $i -lt 30; $i++) {
        $ping = Invoke-Aws ssm describe-instance-information --filters "Key=InstanceIds,Values=$id" `
            --query 'InstanceInformationList[0].PingStatus'
        if ($ping -eq 'Online') { break }
        Start-Sleep -Seconds 10
    }
    if ($ping -ne 'Online') { throw 'サーバーに指示を送れません(しばらく待ってから、もう一度実行してください)' }
    Write-Host 'GitHub の最新を取得して、アプリを作り直します(10〜20 分)...'
    $commands = @(
        'set -e',
        'cd /opt/sns',
        'git pull --ff-only',
        'chmod +x deploy/aws/*.sh',
        'docker compose -f compose.prod.yaml build app',
        'deploy/aws/boot.sh',
        'docker image prune -f',
        'docker builder prune -af'
    )
    $params = @{ commands = $commands; executionTimeout = @('3600') } | ConvertTo-Json -Compress
    $tmp = Join-Path ([IO.Path]::GetTempPath()) 'sns-ssm.json'
    [IO.File]::WriteAllText($tmp, $params, (New-Object Text.UTF8Encoding $false))
    try {
        $cmdId = Invoke-Aws ssm send-command --instance-ids $id --document-name AWS-RunShellScript `
            --timeout-seconds 3600 --parameters "file://$tmp" --query 'Command.CommandId'
    } finally { Remove-Item $tmp }
    do {
        Start-Sleep -Seconds 20
        $status = Invoke-Aws ssm get-command-invocation --command-id $cmdId --instance-id $id --query 'Status'
        Write-Host -NoNewline '.'
    } while ($status -in 'Pending', 'InProgress', 'Delayed')
    Write-Host ''
    if ($status -ne 'Success') { throw "アプリの作り直しに失敗しました($status)。AWS の画面の Systems Manager → Run Command で内容を見られます" }
}

Write-Host "アプリが開けるようになるまで待ちます..."
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Seconds 15
    try {
        $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 "$url/api/auth/config"
        if ($r.StatusCode -eq 200) { Write-Host ''; Write-Host "開けます: $url/"; exit 0 }
    } catch { Write-Host -NoNewline '.' }
}
Write-Host ''
Write-Host "まだ開けません。1〜2 分待ってから $url/ を開いてください。"
