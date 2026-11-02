#!/bin/bash
# サーバー(EC2)が起動するたびに動く(sns-boot.service)。
# 1. AWS のパラメータストアから設定(Gmail・Google ログイン・DuckDNS など)を読み、.env を作り直す
# 2. DuckDNS のアドレスを、このサーバーの今の IP アドレスに向ける(起動のたびに IP が変わるため)
# 3. アプリを起動する
# 秘密の値は、パラメータストアと、このサーバーの .env(本人だけが読める)にしか置かない。
set -euo pipefail
cd "$(dirname "$0")/../.."
REGION=ap-northeast-1

param() {
  aws ssm get-parameter --region "$REGION" --name "/sns/$1" --with-decryption \
    --query Parameter.Value --output text 2>/dev/null || true
}
need() {
  local value
  value=$(param "$1")
  if [ -z "$value" ] || [ "$value" = "None" ]; then
    echo "パラメータストアに /sns/$1 がありません(set-secrets.ps1 で入れる)" >&2
    exit 1
  fi
  printf '%s' "$value"
}

umask 077
# データベースのパスワードは、このサーバーで 1 回だけ作り、外には出さない
if [ ! -f .env.db ]; then
  printf "DB_PASSWORD='%s'\n" "$(openssl rand -hex 24)" > .env.db
fi

DOMAIN=$(need duckdns-domain)
DUCKDNS_TOKEN=$(need duckdns-token)
MAIL_USER=$(need mail-username)
{
  cat .env.db
  echo "SITE_ADDRESS=${DOMAIN}.duckdns.org"
  echo "STORAGE_TYPE=s3"
  echo "S3_BUCKET=$(need s3-bucket)"
  echo "MAIL_HOST=smtp.gmail.com"
  echo "MAIL_PORT=587"
  echo "MAIL_USERNAME='${MAIL_USER}'"
  echo "MAIL_PASSWORD='$(need mail-password)'"
  echo "MAIL_FROM='${MAIL_USER}'"
  echo "GOOGLE_CLIENT_ID='$(param google-client-id)'"
  echo "GOOGLE_CLIENT_SECRET='$(param google-client-secret)'"
} > .env.new
mv .env.new .env

# このサーバーの公開 IP アドレス(EC2 のメタデータ。IMDSv2)
IMDS_TOKEN=$(curl -fsS -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')
IP=$(curl -fsS -H "X-aws-ec2-metadata-token: $IMDS_TOKEN" http://169.254.169.254/latest/meta-data/public-ipv4)
RESULT=$(curl -fsS "https://www.duckdns.org/update?domains=${DOMAIN}&token=${DUCKDNS_TOKEN}&ip=${IP}" || true)
if [ "$RESULT" != "OK" ]; then
  echo "DuckDNS の更新に失敗しました(ドメイン名とトークンを確かめる)" >&2
  exit 1
fi
echo "${DOMAIN}.duckdns.org を ${IP} に向けました"

docker compose -f compose.prod.yaml up -d
echo "アプリを起動しました: https://${DOMAIN}.duckdns.org/"
