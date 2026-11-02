# 公開用のサーバーが使う秘密の値を、AWS のパラメータストア(/sns/…。暗号化して保存。無料)に入れる。
# 入れた値は画面に表示しない。サーバーは起動するたびに、ここから読んで .env を作る(deploy/aws/boot.sh)。
# 使い方: aws sso login --profile taskboard のあとで  .\set-secrets.ps1
#   値を変えたいときも、もう一度動かす(空のまま Enter を押した項目は、今の値のまま)
#
# 入れるもの:
#   duckdns-domain        DuckDNS で作った名前(例: sns-taro。.duckdns.org は付けない)
#   duckdns-token         DuckDNS のトークン
#   mail-username         メールを送る Gmail のアドレス
#   mail-password         Gmail のアプリパスワード(16 文字。空白は取り除く)
#   google-client-id      Google ログイン(リポジトリの .env の GOOGLE_CLIENT_ID をそのまま使う)
#   google-client-secret  同じく GOOGLE_CLIENT_SECRET
param(
    [string]$Profile_ = 'taskboard',
    [string]$Region = 'ap-northeast-1'
)
$ErrorActionPreference = 'Stop'
$aws = (Get-Command aws -ErrorAction SilentlyContinue).Source
if (-not $aws) { $aws = 'C:\Program Files\Amazon\AWSCLIV2\aws.exe' }

# 値はコマンドの引数に書かず、本人だけが読める一時ファイルで渡す(ほかのプログラムから見えないように)
function Set-Parameter([string]$name, [string]$value, [string]$type = 'SecureString') {
    $path = Join-Path ([IO.Path]::GetTempPath()) "sns-param-$([guid]::NewGuid()).json"
    $json = @{ Name = "/sns/$name"; Value = $value; Type = $type; Overwrite = $true } | ConvertTo-Json -Compress
    try {
        [IO.File]::WriteAllText($path, $json, (New-Object Text.UTF8Encoding $false))
        & $aws ssm put-parameter --cli-input-json "file://$path" --profile $Profile_ --region $Region --output text | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "/sns/$name を入れられませんでした" }
    } finally {
        Remove-Item $path -ErrorAction SilentlyContinue
    }
    Write-Host "  /sns/$name を入れました"
}

function Read-Plain([string]$prompt) { (Read-Host $prompt).Trim() }
function Read-Secret([string]$prompt) {
    $secure = Read-Host $prompt -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr).Trim() }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

Write-Host '空のまま Enter を押した項目は、変えません。'
Write-Host ''
Write-Host '--- DuckDNS(https://www.duckdns.org で作った名前とトークン)'
$domain = (Read-Plain '名前(例: sns-taro。.duckdns.org は付けない)') -replace '\.duckdns\.org$', ''
if ($domain) {
    if ($domain -notmatch '^[a-z0-9-]+$') { throw '名前は英小文字・数字・ハイフンだけです' }
    Set-Parameter 'duckdns-domain' $domain 'String'
}
$token = Read-Secret 'トークン(画面には表示されません)'
if ($token) { Set-Parameter 'duckdns-token' $token }

Write-Host ''
Write-Host '--- Gmail(登録の確認メール・パスワード再設定のメールを送る)'
$mail = Read-Plain 'Gmail のアドレス'
if ($mail) {
    if ($mail -notmatch '^[^@\s]+@[^@\s]+$') { throw 'メールアドレスの形ではありません' }
    Set-Parameter 'mail-username' $mail
}
$appPassword = (Read-Secret 'アプリパスワード 16 文字(画面には表示されません)') -replace '\s', ''
if ($appPassword) {
    if ($appPassword.Length -ne 16) { throw 'アプリパスワードは 16 文字です(空白を除いて)' }
    Set-Parameter 'mail-password' $appPassword
}

Write-Host ''
Write-Host '--- Google ログイン(リポジトリの .env から読む)'
$envFile = Join-Path $PSScriptRoot '..\..\.env'
if (Test-Path $envFile) {
    $values = @{}
    foreach ($line in Get-Content $envFile) {
        if ($line -match '^\s*(GOOGLE_CLIENT_ID|GOOGLE_CLIENT_SECRET)\s*=\s*(.*)$') { $values[$Matches[1]] = $Matches[2].Trim().Trim('"', "'") }
    }
    if ($values['GOOGLE_CLIENT_ID'] -and $values['GOOGLE_CLIENT_SECRET']) {
        Set-Parameter 'google-client-id' $values['GOOGLE_CLIENT_ID']
        Set-Parameter 'google-client-secret' $values['GOOGLE_CLIENT_SECRET']
    } else {
        Write-Host '  .env に GOOGLE_CLIENT_ID・GOOGLE_CLIENT_SECRET がないので、Google ログインは使わない設定になります'
    }
} else {
    Write-Host '  .env がないので、Google ログインは使わない設定になります'
}

Write-Host ''
Write-Host '入っている設定の名前(値は表示しない):'
& $aws ssm get-parameters-by-path --path /sns --profile $Profile_ --region $Region --output text --query 'Parameters[].Name'
