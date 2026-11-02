# AWS で使う S3 の準備と運用(画像・バックアップ・ログ)

公開用のサーバー(EC2)から Amazon S3 を使い、次の 3 つを保存する。

| 何を | S3 の場所 | いつ | どのくらい残すか |
|---|---|---|---|
| ユーザーがアップロードした画像 | `uploads/` | 投稿・アイコン変更のたびに、アプリが直接保存 | 消すまで(消した・上書きした画像も、古い版として 14 日残る) |
| データベースのバックアップ | `backups/` | 毎日 3:00(日本時間) | 14 日(+ 古い版として 14 日) |
| アプリのログ | `logs/` | 毎日 3:30(日本時間) | 30 日 |

古いものは S3 の「ライフサイクルルール」で自動で消えるので、たまり続けず料金も増えない(合計 1GB 未満の見込みで、月 0.1 ドル未満)。

## ファイル

| ファイル | 内容 |
|---|---|
| `setup-s3.ps1` | バケットと IAM ロールを作る(Windows から、最初に 1 回) |
| `s3-policy.json` | IAM ロールに許すこと: このバケットの `uploads/`・`backups/`・`logs/` の読み書きだけ |
| `ec2-trust-policy.json` | IAM ロールを EC2 に使わせるための設定 |
| `lifecycle.json` | 自動で消すルール |
| `s3-tasks.sh` | サーバーで動かす: バックアップ・戻す・一覧・ログの転送 |
| `sns-backup.timer`・`sns-logs.timer`・`sns-s3-tasks@.service` | 毎日自動で動かすための設定(systemd のタイマー) |
| `install-timers.sh` | タイマーをサーバーに登録する(サーバーで、最初に 1 回) |

## 安全のための決まり
- **バケットは公開しない**(パブリックアクセスをすべてブロック)。画像は、アプリ(`/media/…`)を通して、ログイン中の人にだけ渡す
- **アクセスキーを作らない・どこにも書かない**。サーバーの権限は、EC2 に付けた IAM ロールで渡す(`.env` にもキーは書かない)
- IAM ロールに許すのは、このバケットの決まった場所の読み書きだけ(最小限の権限)
- 保存するデータは暗号化する(S3 が管理する鍵。追加の料金なし)

## 手順

### 1. バケットと IAM ロールを作る(Windows で、最初に 1 回)
前提: AWS CLI を入れ、`aws configure sso --profile sns` でログインの設定をしてあること(taskboard と同じやり方)。
taskboard で使っているプロファイルをそのまま使うときは `.\setup-s3.ps1 -Profile_ taskboard` とする。

```powershell
aws sso login --profile sns
cd deploy\aws
.\setup-s3.ps1
```

最後に表示される `STORAGE_TYPE=s3` と `S3_BUCKET=sns-<アカウントID>` を控える。何度動かしても同じ結果になる(すでにあるものは作り直さない)。

### 2. EC2 に IAM ロールを付ける(サーバーを作るとき)
- サーバー(EC2)を作るときに、IAM インスタンスプロファイル `sns-ec2-profile` を付ける
- **メタデータの「ホップ数の上限」を 2 にする。** アプリと AWS CLI はコンテナの中で動くので、1(初期値)のままだとコンテナから IAM ロールの権限を受け取れない

```powershell
aws ec2 modify-instance-metadata-options --instance-id <サーバーのID> `
  --http-tokens required --http-put-response-hop-limit 2 --profile sns
```

### 3. サーバーの設定(サーバーの中で、最初に 1 回)
リポジトリを `/opt/sns` に置き、`.env` に次を足してからアプリを起動する(ほかの値は `.env.example` の「公開用」を見る)。

```
STORAGE_TYPE=s3
S3_BUCKET=sns-<アカウントID>
```

```bash
cd /opt/sns
docker compose -f compose.prod.yaml up -d --build
sudo deploy/aws/install-timers.sh      # 毎日のバックアップとログ転送を登録
```

### 4. 動いていることを確かめる
```bash
sudo deploy/aws/s3-tasks.sh backup       # その場でバックアップを 1 回とる
sudo deploy/aws/s3-tasks.sh list         # S3 のバックアップの一覧
sudo deploy/aws/s3-tasks.sh sync-logs    # その場でログを写す
systemctl list-timers 'sns-*'            # 次に動く時刻
journalctl -u 'sns-s3-tasks@*' -n 20     # 前回の結果
```
画像を付けて投稿し、AWS の画面(S3 → バケット → `uploads/`)にファイルができていることも確かめる。

## データベースを戻すとき
```bash
sudo deploy/aws/s3-tasks.sh list                              # 戻したい日のものを選ぶ
sudo deploy/aws/s3-tasks.sh restore sns-20261102-180000.dump  # 名前を省くと一番新しいもの
```
- 戻している間はアプリを止め、終わったら自動で起動し直す。**今のデータベースの中身は、バックアップの時点に置き換わる**
- 画像は S3 にそのまま残っている。バックアップのあとに消した画像は、14 日以内なら S3 の画面の「バージョンを表示」から戻せる

## 料金の確認
- 「請求とコスト管理」で、S3 の料金とクレジットの残りを見る
- AWS Budgets で、月の上限(例: 5 ドル)を超えたらメールが届くようにしておく

## 手元で試す(AWS につながずに)
S3 の代わりに、同じ使い方ができる S3Mock(`adobe/s3mock`)を Docker で動かして試せる。
アプリの自動テスト `S3StorageApiTest` は、この方法で S3 への保存・配信・削除を確かめている。
`s3-tasks.sh` は、環境変数 `AWS_ENDPOINT_URL`(S3Mock の URL)・`AWS_ACCESS_KEY_ID`・`AWS_SECRET_ACCESS_KEY`・`S3_BUCKET` を渡すと、S3Mock に対して動く
(Windows の Git Bash では `MSYS_NO_PATHCONV=1` も付ける)。
