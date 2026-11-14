# AWS への公開(EC2 + S3)

Leafline(SNS タイムラインアプリ)を AWS(東京リージョン)で公開する手順と、公開したあとの運用。taskboard と同じ AWS アカウント・同じやり方(使うときだけ起動)で動かす。

## 全体の形

| もの | 使うサービス | 料金の目安 |
|---|---|---|
| サーバー(アプリ・データベース・HTTPS の入り口) | EC2 t4g.micro(メモリ 1GB)+ ディスク 12GB | 動かしている間だけ 1 時間 約 2 円。止めている間はディスク分の月 約 1.2 ドル |
| 公開のアドレス | DuckDNS(無料)。例: `https://sns-taro.duckdns.org` | 無料 |
| HTTPS の証明書 | Let's Encrypt(Caddy が自動で取得・更新) | 無料 |
| 画像・バックアップ・ログ | S3 | 月 0.1 ドル未満 |
| 秘密の値(Gmail・DuckDNS・Google ログイン) | Systems Manager のパラメータストア(標準) | 無料 |
| メール送信 | Gmail(アプリパスワード) | 無料(1 日 500 通まで) |
| 料金の通知 | AWS Budgets(月 5 ドルを超えそうならメール) | 無料(2 つまで) |

- 2027 年 3 月ごろまでは、AWS の無料プランのクレジットの範囲です
- サーバーは止めて起動するたびに IP アドレスが変わるが、サーバーが起動するたびに DuckDNS を新しい IP に向け直すので、アドレスは変わらない(Google ログインも使える)

## 安全のための決まり
- 外から入れるのは 80・443 番だけ。SSH(22 番)は開けない。サーバーの中の作業は、SSM のセッションマネージャーで行う
- データベースとアプリには、外から直接つながらない(Caddy を通す)
- アクセスキーを作らない。サーバーの権限は IAM ロール(`sns-ec2-role`)で渡し、できることは最小限にする(`server-policy.json`)
- 秘密の値は、パラメータストア(暗号化)と、サーバーの `.env`(管理者だけが読める)にしか置かない。git には入れない
- S3 のバケットは公開しない。データは暗号化する
- データベースのパスワードは、サーバーの中でランダムに作り、外には出さない

## ファイル

| ファイル | どこで | 内容 |
|---|---|---|
| `setup.ps1` | Windows | S3 バケット・IAM ロール・料金の通知を作る(最初に 1 回) |
| `set-secrets.ps1` | Windows | 秘密の値をパラメータストアに入れる(最初に 1 回。値を変えるときも) |
| `create-server.ps1` | Windows | サーバー(EC2)とセキュリティグループを作る(最初に 1 回) |
| `start.ps1` / `stop.ps1` | Windows | サーバーを起動・停止する。`start.ps1 -Update` で GitHub の最新に更新 |
| `user-data.sh` | サーバー | 最初の起動で 1 回だけ動く準備(Docker・アプリの取得・自動起動の登録) |
| `boot.sh`・`sns-boot.service` | サーバー | 起動のたびに、設定を読んで `.env` を作り、DuckDNS を更新して、アプリを起動する |
| `s3-tasks.sh`・`sns-*.timer`・`sns-s3-tasks@.service`・`install-timers.sh` | サーバー | 毎日のバックアップ・ログの転送と、データベースを戻す作業 |
| `server-policy.json`・`ec2-trust-policy.json`・`lifecycle.json` | — | IAM ロールの権限・S3 の自動削除のルール |

## 公開の手順(最初に 1 回)

### 1. 自分で用意するもの
1. **DuckDNS**: <https://www.duckdns.org> に GitHub か Google のアカウントでログインし、好きな名前(例: `sns-taro`)を追加する。画面に出る **token** を控える
2. **Gmail のアプリパスワード**: Google アカウントの「セキュリティ」で 2 段階認証をオンにし、「アプリ パスワード」で 16 文字のパスワードを作る
3. **Google ログインの戻り先**: Google Cloud の「認証情報」→ 使っている OAuth クライアント →「承認済みのリダイレクト URI」に `https://<DuckDNS の名前>.duckdns.org/login/oauth2/code/google` を足す

### 2. AWS にログインする(Windows の PowerShell)
```powershell
aws sso login --profile taskboard
cd deploy\aws
```

### 3. S3・IAM ロール・料金の通知を作る
```powershell
.\setup.ps1 -BudgetEmail あなたのメールアドレス
```

### 4. 秘密の値を入れる(画面には表示されない)
```powershell
.\set-secrets.ps1
```
DuckDNS の名前とトークン、Gmail のアドレスとアプリパスワードを聞かれる。Google ログインの値は、リポジトリの `.env` から読む。

### 5. サーバーを作る(15〜25 分)
```powershell
.\create-server.ps1
```
最後に `公開しました: https://<名前>.duckdns.org/` と出れば完了。

### 6. 管理者を決める
公開したアプリで自分のアカウントを登録してから、AWS の画面の「Systems Manager → セッションマネージャー」でサーバーに入り、次を実行する(画面からは管理者にできない。F-ADMIN-06)。
```bash
cd /opt/sns
sudo docker compose -f compose.prod.yaml exec db psql -U sns -d sns -c "update users set role = 'ADMIN' where email = 'あなたのメールアドレス';"
```

## ふだんの使い方
```powershell
aws sso login --profile taskboard
.\start.ps1           # 起動(1〜2 分で開ける)
.\stop.ps1            # 使い終わったら止める
.\start.ps1 -Update   # GitHub の最新に更新して起動(10〜20 分)
```

**テーブルを変える更新**(`src/main/resources/db/migration/` に `V2__〜.sql` などを足した更新)は、アプリが起動するときに Flyway が本番の DB に SQL を流す。元に戻せないので、`-Update` の前に `.\start.ps1` で起動し、下の `s3-tasks.sh backup` でバックアップをとってから更新する。うまくいかなければ `s3-tasks.sh restore` で戻せる。

## S3(画像・バックアップ・ログ)

| 何を | S3 の場所 | いつ | どのくらい残すか |
|---|---|---|---|
| ユーザーがアップロードした画像 | `uploads/` | 投稿・アイコン変更のたびに、アプリが直接保存 | 消すまで(消した・上書きした画像も、古い版として 14 日残る) |
| データベースのバックアップ | `backups/` | 毎日 3:00(日本時間)。止めていたら、次に起動したとき | 14 日(+ 古い版として 14 日) |
| アプリのログ | `logs/` | 毎日 3:30(日本時間)。止めていたら、次に起動したとき | 30 日 |

サーバーの中(セッションマネージャー)で:
```bash
sudo /opt/sns/deploy/aws/s3-tasks.sh backup       # その場でバックアップ
sudo /opt/sns/deploy/aws/s3-tasks.sh list         # バックアップの一覧
sudo /opt/sns/deploy/aws/s3-tasks.sh sync-logs    # その場でログを写す
systemctl list-timers 'sns-*'                     # 次に動く時刻
```

### ログの見方
アプリのログ(`logs/sns.log`)は 1 行 1 件の JSON です(項目の一覧は機能定義書の「1.9 ログ」)。サーバーの中で、`jq` を使って項目で絞り込めます。
```bash
sudo dnf install -y jq                            # 最初の 1 回だけ
cd /opt/sns
alias snslog='sudo docker compose -f compose.prod.yaml exec -T app cat logs/sns.log'

snslog | jq -c 'select(.log.level == "ERROR")'                          # エラーだけ
snslog | jq -c 'select(.requestId == "933f0259d3c5")'                   # 利用者が見た「エラー番号」から、そのときのログ全部
snslog | jq -r 'select(.event.action == "auth.login_failed") | [."@timestamp", .reason, .accountId] | @tsv'  # ログインの失敗
snslog | jq -r 'select(.event.action | startswith("admin.")) | [."@timestamp", .event.action, .adminId] | @tsv'  # 管理者の操作
snslog | jq -r 'select(.event.duration_ms > 1000) | .message'          # 1 秒より遅かったリクエスト
snslog | jq -r 'select(.http.response.status_code >= 500) | .message'  # 5xx を返したリクエスト
```
人が読む形のログは `sudo docker compose -f compose.prod.yaml logs --tail 100 app`(コンテナごとに 30MB まで残る)。
S3 の `logs/` に写したログも同じ JSON なので、手元に落として同じように調べられます。

### データベースを戻すとき
```bash
sudo /opt/sns/deploy/aws/s3-tasks.sh restore sns-20261102-180000.dump  # 名前を省くと一番新しいもの
```
戻している間はアプリを止め、終わったら自動で起動し直す。**今のデータベースの中身は、バックアップの時点に置き換わる。** 画像は S3 にそのまま残っている(消した画像は、14 日以内なら S3 の画面の「バージョンを表示」から戻せる)。

## うまくいかないとき
- **開けない**: サーバーの中で `sudo systemctl status sns-boot` と `sudo journalctl -u sns-boot -n 50` を見る。最初の作成のときは `sudo tail -n 50 /var/log/cloud-init-output.log`
- **「パラメータストアに /sns/… がありません」**: `set-secrets.ps1` をもう一度動かす
- **DuckDNS の更新に失敗**: 名前とトークンを確かめて `set-secrets.ps1` で入れ直し、`start.ps1` で起動し直す
- **メールが届かない**: アプリのログ(`sudo docker compose -f /opt/sns/compose.prod.yaml logs app`)を見る。エラーがなければ、アプリからは送れていて、宛先の側で遅れている・止まっている。**携帯会社のアドレス(`i.softbank.jp`・`docomo.ne.jp`・`ezweb.ne.jp` など)は、知らない送信元からのメールが遅れたり届かなかったりしやすい**ので、Gmail などで登録する。送信元の Gmail の受信トレイに「Mail Delivery Subsystem」の配信エラーが来ていないかも見る。Gmail のアプリパスワードを作り直したら `set-secrets.ps1` で入れ直す
- **最初の起動が「compose build requires buildx」で止まった**: 最新の docker compose がイメージを作るときに buildx が必要。`user-data.sh` で入れるようにしてある(古い版で作ったサーバーは、SSM のセッションで `docker-buildx` を `/usr/local/lib/docker/cli-plugins/` に入れ、`sudo systemctl start sns-boot` を実行する)
- **`create-server.ps1` が「text contents could not be decoded」で止まった**: `user-data.sh` を `fileb://` で渡すように直してある(日本語のコメントを含むため)
- **Google ログインで「redirect_uri_mismatch」**: 手順 1-3 のリダイレクト URI を確かめる

## API 仕様書(Swagger)
公開したサーバーでも、`https://〈公開のアドレス〉/swagger-ui.html` で、ログインなしで開ける(OpenAPI は `/v3/api-docs`)。
止めたいとき(メモリが足りないなど)は、パラメータストアに入れて、サーバーを起動し直す(`boot.sh` が、起動のたびに `.env` に書く):
```powershell
aws ssm put-parameter --name /sns/api-docs-enabled --type String --value false --overwrite --profile taskboard --region ap-northeast-1
# 戻すとき: aws ssm delete-parameter --name /sns/api-docs-enabled --profile taskboard --region ap-northeast-1
```

## 料金の確認
- 「請求とコスト管理」で、料金とクレジットの残りを見る
- AWS Budgets のメール(月 5 ドルの 80% を超えた・超えそう)が届いたら、サーバーを止め忘れていないか確かめる

## 手元で試す(AWS につながずに)
S3 の代わりに、同じ使い方ができる S3Mock(`adobe/s3mock`)を Docker で動かして試せる(アプリの自動テスト `S3StorageApiTest` もこの方法)。
`s3-tasks.sh` は、環境変数 `AWS_ENDPOINT_URL`(S3Mock の URL)・`AWS_ACCESS_KEY_ID`・`AWS_SECRET_ACCESS_KEY`・`S3_BUCKET` を渡すと、S3Mock に対して動く
(Windows の Git Bash では `MSYS_NO_PATHCONV=1` も付ける)。

## 公開したあとの確認(2026-11-03 に実施)
公開したサーバーで、次を確かめた。
- 正式な証明書の HTTPS(HTTP は HTTPS へ転送される)、Google ログイン、メールでの登録(Gmail 経由)
- 管理者の設定(サーバーの中でデータベースの `users.role` を `ADMIN` にした)と、管理者画面
- 画像つきの投稿が S3 の `uploads/` に保存され、ログインなしでは見られない(アプリ経由は 401、S3 に直接アクセスすると 403)
- バックアップが `backups/`、ログが `logs/` に保存される。毎日のタイマーも登録済み
- メモリ: 1GB のサーバーで、アプリ・データベース・HTTPS の入り口が動く(スワップ 2GB を用意)
