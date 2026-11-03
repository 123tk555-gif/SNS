# SNSタイムラインアプリ

X(旧 Twitter)や Facebook のような、タイムライン型の SNS の Web アプリです。学習・ポートフォリオ用に作っています。

- 投稿(1000 文字・画像 10 枚まで)、編集・削除、いいね・返信・ブックマーク
- 「すべて」と「フォロー中」のタイムライン、新着・通知の自動反映(SSE)
- フォロー、プロフィール、ハッシュタグ・トレンド・検索(日本語に対応)
- ブロック・ミュート、設定画面(ライト・ダークの表示切り替えを含む)、管理者画面
- メール+パスワードと Google でのログイン

技術は、React(Vite)/ Spring Boot(Java 21)/ PostgreSQL 18 です。同じ構成で作った「トレロ風タスク管理アプリ」と同じ技術を使っています。

## 文書

| 文書 | 内容 |
|---|---|
| [要件定義書](docs/要件定義書.md) | 何を作るか。使う技術とバージョン、起動の方法、受け入れ基準 |
| [機能定義書](docs/機能定義書.md) | 各機能がどう動くか(処理・API・データ・権限) |
| [画面仕様定義書](docs/画面仕様定義書.md) | 画面の表示・操作・メッセージ |
| [受け入れ確認表](docs/受け入れ確認表.md) | 受け入れ基準を、何で確かめたか |
| [API 仕様書](docs/openapi.yaml)(OpenAPI) | 全 API の説明・引数・応答・データの形(日本語)。サーバーを起動すると、Swagger UI(http://localhost:8080/swagger-ui.html)でも読めて、試せる |
| [prototype/](prototype/) | 最初に作った HTML・CSS・JavaScript だけの試作(`index.html` をブラウザで開く) |

## 動かし方(開発用)

必要なもの: Docker Desktop、Java 21、Node.js 24。

```powershell
# 1. データベース(PostgreSQL)とメール確認用サーバー(Mailpit)を起動
docker compose up -d

# 2. サーバー(Spring Boot)を起動  → http://localhost:8080
./mvnw spring-boot:run

# 3. 別の画面で、画面(React)の開発サーバーを起動  → http://localhost:5173
cd frontend
npm install
npm run dev
```

- 確認メールは Mailpit(<http://localhost:8025>)で読めます。
- Google ログインを使うときは、`.env.example` をコピーして `.env` を作り、`GOOGLE_CLIENT_ID` と `GOOGLE_CLIENT_SECRET` を書きます(書かなければ Google のボタンが出ないだけで、他は使えます)。`.env` は git に入れません。
- 動作確認用のテストデータ(ユーザー 5 人・投稿など。パスワードはすべて `password123`、`taro@example.com` は管理者)を入れるには、`./mvnw spring-boot:run "-Dspring-boot.run.profiles=seed"` で起動します。**今までのデータは消えます。**
- 画面を作ってサーバーから配信したいときは、`frontend` で `npm run build` をしてから、サーバーを起動して <http://localhost:8080> を開きます。

## API 仕様書(Swagger)

全 API(53 個)の仕様を、Swagger(OpenAPI)で読めます。**ログイン不要**で開けます。

- **Swagger UI**: サーバーを起動して <http://localhost:8080/swagger-ui.html> を開く。API ごとに、日本語の説明・引数・返るデータ・エラーを読める。先にアプリでログインしておくと、「Try it out」→「Execute」で実際に呼べる(CSRF のヘッダーは、自動で付く)
- **OpenAPI のファイル**: `/v3/api-docs`(JSON)・`/v3/api-docs.yaml`(YAML)。保存用のコピーは [docs/openapi.yaml](docs/openapi.yaml)(Swagger を読めるツールに渡せる)
- **公開したサーバー**でも、同じ場所(`https://〈公開のアドレス〉/swagger-ui.html`)で開ける
- 説明は、コントローラーの `@Operation`・`@ApiResponse` と、データの形(record)の `@Schema` に書いてある。API や説明を変えたら、`./mvnw test -Dtest=OpenApiDocsTest -Dopenapi.update=true` で `docs/openapi.yaml` を作り直す(忘れると、自動テストが失敗して教えてくれる)
- 止めたいとき: 環境変数 `API_DOCS_ENABLED=false`

## テスト

| 何を | どうやって | 内容 |
|---|---|---|
| サーバー | `./mvnw test` | JUnit。本物の PostgreSQL を Testcontainers で起動して確かめる(Docker が必要)。画像の S3 保存は、S3 の代わりの S3Mock で確かめる。100 件 |
| 画面のコード | `cd frontend && npm run lint` | oxlint |
| 画面の操作 | `cd e2e && npm install && npm test` | Playwright で、実際のブラウザ(Edge)を動かす。10 本・217 項目(公開前の点検 `m10-release-flow`・API 仕様書 `m11-apidocs-flow` を含む) |
| 見やすさの点検 | `cd e2e && npm run audit` | 全画面を 375px/1280px・ライト/ダークで開き、横スクロール・名前のない部品・フォーカスの枠などを調べる |

画面の操作テストの前提: データベース・サーバー(8080)・画面(5173)が動いていて、テストデータ(seed)が入っていること。テストの前後に、作ったデータ(投稿・フォロー・通知・`e2e_` で始まるテスト用ユーザー)は消します(`npm run cleanup` だけでも実行できます)。ユーザーはそのまま残ります。

## 公開用の構成

`Dockerfile`(画面とサーバーを 1 つにまとめる)と `compose.prod.yaml`(データベース・アプリ・HTTPS の入り口の Caddy)を使います。外に開くのは 80・443 番だけで、データベースとアプリには外から直接つながりません。

```powershell
# .env に SITE_ADDRESS・DB_PASSWORD・MAIL_* を書いてから(見本は .env.example の「公開用」)
docker compose -f compose.prod.yaml up -d --build
```

### 公開用の構成を手元で試す
`.env` の代わりに別のファイルを使えば、手元でも HTTPS の入り口まで含めて動かせます(`SITE_ADDRESS=localhost` では、自分で作った証明書になるので、ブラウザの警告が出ます)。メール送信サーバーの代わりに Mailpit を足して、`e2e/prod-smoke.mjs` で確認します。

```powershell
# test.env: SITE_ADDRESS=localhost / DB_PASSWORD=(何でもよい) / MAIL_HOST=mail / MAIL_PORT=1025 / MAIL_FROM=no-reply@localhost
# override.yaml: mail という名前で axllent/mailpit:v1 を動かし、127.0.0.1:18025:8025 を公開する
docker compose --env-file test.env -f compose.prod.yaml -f override.yaml up -d --build
cd e2e; $env:BASE_URL="https://localhost"; $env:MAILPIT_URL="http://127.0.0.1:18025"; node prod-smoke.mjs
docker compose --env-file test.env -f compose.prod.yaml -f override.yaml down -v   # 片付け
```

### AWS への公開と S3(画像・バックアップ・ログ)
公開時は、ユーザーがアップロードした画像を Amazon S3 に保存し、データベースのバックアップ(毎日・14 日分)とログ(毎日・30 日分)も S3 に送ります。バケットは公開せず、サーバーには IAM ロールで最小限の権限だけを渡します(アクセスキーは作りません)。準備と運用の手順は [deploy/aws/README.md](deploy/aws/README.md) にあります。

AWS への公開(サーバーの作成・起動・停止)の手順も、同じ [deploy/aws/README.md](deploy/aws/README.md) にあります。サーバーは使うときだけ起動し、公開のアドレスは DuckDNS の無料のアドレス(例: `https://sns-taro.duckdns.org`)です。

## フォルダ

| フォルダ・ファイル | 内容 |
|---|---|
| `src/main/java/com/example/sns/` | サーバー。機能ごとにフォルダを分けている(`post`・`profile`・`notification`・`search`・`admin` など) |
| `src/test/` | サーバーの自動テスト |
| `frontend/` | 画面(React + Vite)。ビルドすると `src/main/resources/static/` に出力される(git に入れない) |
| `e2e/` | 画面の操作テスト(Playwright)と、見やすさの点検 |
| `deploy/` | 公開用の Caddy の設定。`deploy/aws/` は S3 の準備・バックアップ・ログ転送のスクリプト |
| `docs/` | 要件定義書・機能定義書・画面仕様定義書・受け入れ確認表 |
| `uploads/`・`logs/` | 画像の保存先・ログ(git に入れない) |
