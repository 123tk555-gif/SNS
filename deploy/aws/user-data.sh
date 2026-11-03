#!/bin/bash
# EC2(Amazon Linux 2023)を最初に起動したときに 1 回だけ動く準備(create-server.ps1 が渡す)。
# Docker を入れ、GitHub からアプリを取得し、毎回の起動で動く準備(sns-boot.service)と、
# 毎日のバックアップ・ログ転送のタイマーを登録して、アプリを起動する。
set -euxo pipefail

# メモリ 1GB のサーバーでも、イメージを作るときに足りなくならないよう、2GB の予備(スワップ)を作る
if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

dnf install -y docker git
systemctl enable --now docker

# docker compose(Amazon Linux の docker には入っていないので、公式の配布物を入れる。手元の PC と同じ版)
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL "https://github.com/docker/compose/releases/download/v5.5.1/docker-compose-linux-$(uname -m)" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
# docker buildx(compose がアプリのイメージを作るときに必要。これも Amazon Linux の docker には入っていない)
curl -fsSL "https://github.com/docker/buildx/releases/download/v0.38.0/buildx-v0.38.0.linux-$(uname -m | sed 's/aarch64/arm64/; s/x86_64/amd64/')" \
  -o /usr/local/lib/docker/cli-plugins/docker-buildx
chmod +x /usr/local/lib/docker/cli-plugins/docker-buildx

# アプリを取得(公開リポジトリの main)
if [ ! -d /opt/sns ]; then
  git clone --depth 1 https://github.com/123tk555-gif/SNS.git /opt/sns
fi
cd /opt/sns
chmod +x deploy/aws/*.sh

# 毎回の起動で動く準備と、毎日のタイマーを登録する
install -m 644 deploy/aws/sns-boot.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable sns-boot.service
deploy/aws/install-timers.sh

# 最初の起動: 設定を取得してアプリを作って起動する(イメージを作るので 10〜20 分かかる)
systemctl start sns-boot.service
docker builder prune -af || true
echo "sns: 準備が終わりました"
