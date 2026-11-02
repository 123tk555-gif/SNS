#!/bin/bash
# 公開用のサーバー(EC2)で 1 回だけ動かす: バックアップとログ転送のタイマーを登録する。
# 使い方(サーバーの中で): sudo /opt/sns/deploy/aws/install-timers.sh
set -euo pipefail
cd "$(dirname "$0")"
chmod +x s3-tasks.sh
install -m 644 sns-s3-tasks@.service sns-backup.timer sns-logs.timer /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now sns-backup.timer sns-logs.timer
systemctl list-timers 'sns-*'
