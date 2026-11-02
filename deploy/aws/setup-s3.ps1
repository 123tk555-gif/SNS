# S3 のバケットと、サーバー(EC2)に付ける IAM ロールを用意する。最初に 1 回だけ動かす(何度動かしても同じ結果になる)。
# 使い方: aws sso login --profile sns のあとで  .\setup-s3.ps1
#
# 作るもの:
#  - S3 バケット sns-<アカウントID>(東京)。公開しない・暗号化・古い版を 14 日残す・
#    backups/ は 14 日、logs/ は 30 日で自動で消す(lifecycle.json)
#  - IAM ロール sns-ec2-role と、EC2 に付けるための sns-ec2-profile。
#    できることは、このバケットの uploads/・backups/・logs/ の読み書き(s3-policy.json)と、
#    サーバーへの指示(SSM。AmazonSSMManagedInstanceCore)だけ
param(
    [string]$Profile_ = 'sns',
    [string]$Region = 'ap-northeast-1'
)
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
# AWS CLI が PATH にない画面でも動くように、標準のインストール先も探す
$aws = (Get-Command aws -ErrorAction SilentlyContinue).Source
if (-not $aws) { $aws = 'C:\Program Files\Amazon\AWSCLIV2\aws.exe' }

function Invoke-Aws {
    $out = & $aws @args --profile $Profile_ --region $Region --output json
    if ($LASTEXITCODE -ne 0) { throw "aws $($args -join ' ') が失敗しました" }
    if ($out) { $out | Out-String | ConvertFrom-Json }
}

# 失敗しても止めずに、成功したかどうかだけを返す(「すでにあるか」を確かめるため)
function Test-Aws {
    & $aws @args --profile $Profile_ --region $Region --output json 2>$null | Out-Null
    return $LASTEXITCODE -eq 0
}

# JSON を一時ファイルにして渡す(PowerShell から JSON をそのまま渡すと、引用符が崩れるため)
function Write-TempJson([string]$name, [string]$json) {
    $path = Join-Path ([IO.Path]::GetTempPath()) "sns-$name.json"
    [IO.File]::WriteAllText($path, $json, (New-Object Text.UTF8Encoding $false))
    return "file://$path"
}

$account = (Invoke-Aws sts get-caller-identity).Account
$bucket = "sns-$account"
Write-Host "アカウント $account / バケット $bucket / リージョン $Region"

# ---- S3 バケット ----
if (Test-Aws s3api head-bucket --bucket $bucket) {
    Write-Host "バケット $bucket はすでにあります"
} else {
    Write-Host "バケット $bucket を作ります"
    Invoke-Aws s3api create-bucket --bucket $bucket --create-bucket-configuration "LocationConstraint=$Region" | Out-Null
}
# 公開しない(パブリックアクセスをすべてブロック)
Invoke-Aws s3api put-public-access-block --bucket $bucket --public-access-block-configuration `
    'BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true' | Out-Null
# 保存するデータを暗号化する(S3 が管理する鍵。追加の料金なし)
$encryption = Write-TempJson 'encryption' '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
Invoke-Aws s3api put-bucket-encryption --bucket $bucket --server-side-encryption-configuration $encryption | Out-Null
# 上書き・削除した画像も 14 日間は古い版として残す(間違って消したときに戻せるように)
Invoke-Aws s3api put-bucket-versioning --bucket $bucket --versioning-configuration 'Status=Enabled' | Out-Null
# 古いバックアップ・ログ・古い版を自動で消す(たまり続けないので、料金も増えない)
Invoke-Aws s3api put-bucket-lifecycle-configuration --bucket $bucket `
    --lifecycle-configuration "file://$here\lifecycle.json" | Out-Null
Write-Host 'バケットの設定(非公開・暗号化・版の保存・自動削除)を入れました'

# ---- IAM ロール(EC2 に付ける) ----
$role = 'sns-ec2-role'
$instanceProfile = 'sns-ec2-profile'
if (Test-Aws iam get-role --role-name $role) {
    Write-Host "ロール $role はすでにあります"
} else {
    Write-Host "ロール $role を作ります"
    Invoke-Aws iam create-role --role-name $role `
        --assume-role-policy-document "file://$here\ec2-trust-policy.json" `
        --description 'SNS app server: S3 (uploads/backups/logs) and SSM' | Out-Null
}
Invoke-Aws iam attach-role-policy --role-name $role `
    --policy-arn 'arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore' | Out-Null
$policy = (Get-Content "$here\s3-policy.json" -Raw) -replace 'BUCKET_NAME', $bucket
Invoke-Aws iam put-role-policy --role-name $role --policy-name 'sns-s3' `
    --policy-document (Write-TempJson 'policy' $policy) | Out-Null

if (-not (Test-Aws iam get-instance-profile --instance-profile-name $instanceProfile)) {
    Invoke-Aws iam create-instance-profile --instance-profile-name $instanceProfile | Out-Null
}
$attached = (Invoke-Aws iam get-instance-profile --instance-profile-name $instanceProfile).InstanceProfile.Roles |
    Where-Object { $_.RoleName -eq $role }
if (-not $attached) {
    Invoke-Aws iam add-role-to-instance-profile --instance-profile-name $instanceProfile --role-name $role | Out-Null
}
Write-Host "ロール $role(EC2 に付ける名前: $instanceProfile)を用意しました"

Remove-Item (Join-Path ([IO.Path]::GetTempPath()) 'sns-*.json') -ErrorAction SilentlyContinue
Write-Host ''
Write-Host '終わりました。公開用のサーバーの .env に、次を書きます:'
Write-Host "  STORAGE_TYPE=s3"
Write-Host "  S3_BUCKET=$bucket"
