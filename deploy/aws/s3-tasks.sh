#!/bin/bash
# 公開用のサーバー(EC2)で動かす、S3 を使う作業。リポジトリの一番上の .env に S3_BUCKET が書いてあること。
#
#   deploy/aws/s3-tasks.sh backup            データベースを書き出して S3 の backups/ に送る(毎日、タイマーで自動)
#   deploy/aws/s3-tasks.sh sync-logs         アプリのログを S3 の logs/ に写す(毎日、タイマーで自動)
#   deploy/aws/s3-tasks.sh list              S3 にあるバックアップの一覧
#   deploy/aws/s3-tasks.sh restore [名前]    バックアップからデータベースを戻す(名前を省くと一番新しいもの)。今のデータは消える
#
# S3 の操作には AWS CLI の公式イメージ(amazon/aws-cli)を使う。認証は EC2 に付けた IAM ロール
# (アクセスキーはどこにも書かない)。コンテナから IAM ロールを使うため、EC2 のメタデータの
# 「ホップ数の上限」を 2 にしておく(README の手順)。
#
# 手元で試すときは、環境変数 AWS_ENDPOINT_URL(S3 の代わりのサーバー)と AWS_ACCESS_KEY_ID・AWS_SECRET_ACCESS_KEY を渡す。
set -euo pipefail

cd "$(dirname "$0")/../.."
COMPOSE=(docker compose -f compose.prod.yaml ${COMPOSE_EXTRA:-})
PROJECT=sns-prod
AWS_IMAGE=amazon/aws-cli:2.38.1
REGION=${AWS_REGION:-ap-northeast-1}

# .env から 1 つの値を読む(.env 全体は読み込まない。パスワードなどを、この画面の環境に出さないため)
env_value() {
  grep -E "^$1=" .env 2>/dev/null | tail -n 1 | cut -d= -f2- || true
}
BUCKET=${S3_BUCKET:-$(env_value S3_BUCKET)}
if [ -z "$BUCKET" ]; then
  echo "S3_BUCKET が .env に書かれていません" >&2
  exit 1
fi

# AWS CLI をコンテナで動かす。渡すのは、決めた環境変数だけ(値がないものは渡らない)
aws_cli() {
  docker run --rm -i \
    -e AWS_REGION="$REGION" -e AWS_ENDPOINT_URL -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY \
    "${AWS_VOLUME[@]}" "$AWS_IMAGE" "$@"
}
AWS_VOLUME=()

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

backup() {
  local name
  name="sns-$(date -u '+%Y%m%d-%H%M%S').dump"
  log "データベースを書き出して s3://$BUCKET/backups/$name に送ります"
  # 圧縮した形式(custom)で書き出し、ファイルを作らずにそのまま S3 へ流す
  "${COMPOSE[@]}" exec -T db pg_dump -U sns -d sns --format=custom --no-owner \
    | aws_cli s3 cp - "s3://$BUCKET/backups/$name" --only-show-errors
  log "バックアップが終わりました"
}

list_backups() {
  aws_cli s3 ls "s3://$BUCKET/backups/" | sort
}

latest_backup() {
  aws_cli s3 ls "s3://$BUCKET/backups/" | awk '{print $4}' | grep -E '^sns-.*\.dump$' | sort | tail -n 1
}

restore() {
  local name=${1:-$(latest_backup)}
  if [ -z "$name" ]; then
    echo "バックアップがありません" >&2
    exit 1
  fi
  log "s3://$BUCKET/backups/$name から戻します。アプリを止めます"
  "${COMPOSE[@]}" stop app
  # 戻せなかったときも、アプリは動かし直す
  trap '"${COMPOSE[@]}" start app' EXIT
  aws_cli s3 cp "s3://$BUCKET/backups/$name" - --only-show-errors \
    | "${COMPOSE[@]}" exec -T db pg_restore -U sns -d sns --clean --if-exists --no-owner --single-transaction
  log "戻しました。アプリを起動します"
}

sync_logs() {
  log "ログを s3://$BUCKET/logs/ に写します"
  # ログは Docker のボリューム(sns-prod_logs)にあるので、AWS CLI のコンテナに読み取り専用でつなぐ
  AWS_VOLUME=(-v "${PROJECT}_logs:/logs:ro")
  aws_cli s3 sync /logs "s3://$BUCKET/logs/" --only-show-errors
  AWS_VOLUME=()
  log "ログを写しました"
}

case "${1:-}" in
  backup) backup ;;
  restore) restore "${2:-}" ;;
  list) list_backups ;;
  sync-logs) sync_logs ;;
  *)
    sed -n '2,8p' "$0"
    exit 2
    ;;
esac
