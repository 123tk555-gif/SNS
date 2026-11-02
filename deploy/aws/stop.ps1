# 公開用のサーバー(EC2)を止める。止めている間はサーバーと IP アドレスの料金はかからない
# (ディスク 12GB の分だけ、月に約 1.2 ドル。S3 の画像・バックアップはそのまま残る)。
# 使い方: aws sso login --profile taskboard のあとで  .\stop.ps1
param(
    [string]$Profile_ = 'taskboard',
    [string]$Region = 'ap-northeast-1'
)
$ErrorActionPreference = 'Stop'
$aws = (Get-Command aws -ErrorAction SilentlyContinue).Source
if (-not $aws) { $aws = 'C:\Program Files\Amazon\AWSCLIV2\aws.exe' }

$id = & $aws ec2 describe-instances --profile $Profile_ --region $Region --output text `
    --filters 'Name=tag:Project,Values=sns' 'Name=instance-state-name,Values=pending,running' `
    --query 'Reservations[0].Instances[0].InstanceId'
if ($LASTEXITCODE -ne 0) { throw 'サーバーの情報を取れませんでした' }
if (-not $id -or $id -eq 'None') { Write-Host '動いている SNS のサーバーはありません'; exit 0 }

Write-Host "サーバー $id を止めます..."
& $aws ec2 stop-instances --instance-ids $id --profile $Profile_ --region $Region | Out-Null
& $aws ec2 wait instance-stopped --instance-ids $id --profile $Profile_ --region $Region
if ($LASTEXITCODE -ne 0) { throw '停止の確認に失敗しました' }
Write-Host '止まりました。'
