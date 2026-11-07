# SNS タイムラインアプリ

X(旧 Twitter)のような、タイムライン型の SNS の Web アプリです。学習・ポートフォリオとして、要件定義から設計・実装・テスト・AWS への公開まで、ひと通り自分で作りました。

[![CI](https://github.com/123tk555-gif/SNS/actions/workflows/ci.yml/badge.svg)](https://github.com/123tk555-gif/SNS/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-007396)
![Spring Boot 4.1](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F)
![React 19](https://img.shields.io/badge/React-19-61DAFB)
![PostgreSQL 18](https://img.shields.io/badge/PostgreSQL-18-4169E1)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED)
![AWS](https://img.shields.io/badge/AWS-EC2_%2F_S3-FF9900)
![Tests](https://img.shields.io/badge/tests-JUnit_121_%2B_Vitest_19_%2B_E2E_217-brightgreen)

![ホーム画面](docs/images/home.png)

**公開版**: <https://yarimasunesugi.duckdns.org/>(API 仕様書は [/swagger-ui.html](https://yarimasunesugi.duckdns.org/swagger-ui.html))
費用を抑えるため、サーバーはふだん止めています。開けないときは、止めている時間です。

## デモ

### 新着と通知が、再読み込みなしで届く
左が「たろう」、右が「はなこ」の画面です。はなこが投稿すると、たろうのタイムラインに「新着」付きですぐに出ます。たろうがいいねすると、はなこの「通知」に未読の数が付きます(Server-Sent Events)。

![2 つのブラウザで、投稿といいねがすぐに届く様子](docs/images/realtime.gif)

### 画像つきの投稿と、ダークモード
画像を選ぶと、送る前にブラウザで縮小して WebP に変換します(写真の位置情報も消えます)。設定で、ライト・ダークをすぐに切り替えられます。

![画像つきで投稿して、ダークモードに切り替える様子](docs/images/post-and-dark.gif)

## 画面

| 投稿の詳細と返信 | プロフィール |
|---|---|
| ![投稿の詳細](docs/images/post-detail.png) | ![プロフィール](docs/images/profile.png) |
| **通知** | **トレンド(直近 24 時間のハッシュタグと人気投稿)** |
| ![通知](docs/images/notifications.png) | ![トレンド](docs/images/trends.png) |
| **検索(日本語の文章・全角半角を区別しない)** | **設定(ダークモード)** |
| ![検索](docs/images/search.png) | ![設定](docs/images/settings-dark.png) |

**スマートフォン**(幅 375px でも、横にはみ出さずに全画面を操作できます)

<p>
  <img src="docs/images/phone-home.png" alt="スマートフォンのホーム" width="250">
  <img src="docs/images/phone-profile.png" alt="スマートフォンのプロフィール" width="250">
  <img src="docs/images/phone-notifications-dark.png" alt="スマートフォンの通知(ダークモード)" width="250">
</p>

## 主な機能

| 分類 | できること |
|---|---|
| アカウント | メール+パスワードでの登録(確認メール)、Google ログイン、パスワードの再設定、ログインの連続失敗でのロック |
| 投稿 | 1000 文字・画像 10 枚まで。編集・削除、返信、`#ハッシュタグ`・`@メンション`・URL の自動リンク、画像の代替テキスト |
| タイムライン | 「すべて」と「フォロー中」。無限スクロール(カーソル方式)、新着のリアルタイム反映(読んでいる位置はずらさない) |
| リアクション | いいね・ブックマーク(押した瞬間に切り替わり、失敗したら元に戻る) |
| つながり | フォロー、ブロック(お互いに見えなくなる)、ミュート(自分の画面からだけ消える) |
| 通知 | いいね・返信・フォロー・メンション。未読のバッジがリアルタイムで増える |
| 探す | 投稿・ユーザー・ハッシュタグの検索、トレンド |
| 管理者 | 投稿の非表示、ユーザーの利用停止(管理者以外には、画面も API も「見つかりません」) |
| 表示 | ライト・ダーク・端末に合わせる、スマートフォン対応、キーボードだけでの操作 |

## 技術スタック

| 層 | 使ったもの |
|---|---|
| 画面 | React 19 / Vite 8 / React Router 8 / CSS(ライブラリなし)/ oxlint |
| サーバー | Java 21 / Spring Boot 4.1(Spring Web MVC・Spring Security・Spring Data JPA・Spring Session JDBC)/ Hibernate 7 / Flyway(テーブルの変更管理) |
| データベース | PostgreSQL 18 |
| リアルタイム | Server-Sent Events(`SseEmitter`) |
| API 仕様書 | springdoc-openapi 3.1(Swagger UI・OpenAPI 3.1) |
| テスト | JUnit 6 / Testcontainers(本物の PostgreSQL)/ Playwright(実際のブラウザでの操作テスト) |
| 公開 | Docker Compose / Caddy(HTTPS)/ AWS EC2・S3・Systems Manager / DuckDNS |

## 構成

```mermaid
flowchart LR
    Browser["ブラウザ<br/>React"] -- "HTTPS" --> Caddy["Caddy<br/>HTTPS の入り口"]
    Caddy --> App["Spring Boot<br/>REST API・SSE<br/>Spring Security"]
    App --> DB[("PostgreSQL<br/>データ・セッション")]
    App --> S3[("Amazon S3<br/>画像・バックアップ・ログ")]
    App -. "確認メール" .-> Gmail["Gmail(SMTP)"]
    Browser -. "Google ログイン" .-> Google["Google OAuth"]
    subgraph EC2["AWS EC2(t4g.micro・メモリ 1GB)"]
        Caddy
        App
        DB
    end
```

- 画面(React)はビルドして、Spring Boot が配信します。開発中は Vite の開発サーバーを使います
- ログイン状態はサーバーのセッション(Cookie・データベースに保存)で持ち、データを変える操作には CSRF 対策のヘッダーを付けます
- 公開時は、画像を S3 に保存し、データベースのバックアップ(毎日)とログも S3 に送ります。秘密の値はパラメータストアに置き、サーバーには IAM ロールで最小限の権限だけを渡します(アクセスキーは作りません)

## こだわったところ

- **セキュリティ**: すべての API で、サーバー側でログイン・本人・管理者かを確かめる。CSRF 対策、BCrypt、Cookie の `HttpOnly`・`Secure`・`SameSite`、Content-Security-Policy、ログインの連続失敗のロック、画像は拡張子ではなく中身で形式を確かめる
- **見やすさ**: 文字と背景の色の差を十分に取り(本文は WCAG の AAA 相当)、状態を色だけでなく文字や形でも示す。キーボードだけで操作でき、ダイアログを閉じるとキーボードの位置が元に戻る
- **性能**: 一覧の取得で SQL の回数が件数に比例して増えない(N+1 問題)ことを、テストで SQL の回数を数えて確かめている。カーソル方式で、読み込み中に新着が増えても二重に出ない
- **テスト**: サーバーの自動テスト 121 件(結合テストと単体テスト)、画面の単体テスト 19 件、実際のブラウザでの操作テスト 10 本・217 項目。プッシュのたびに CI で自動で流れる。公開前の点検として、HTML の埋め込み・長い文字列・二度押し・通信の失敗なども確かめている
- **テーブルの変更管理**: テーブルは Flyway が番号付きの SQL から作る・変える。起動のときに Hibernate がコードとテーブルを照らし合わせ(validate)、食い違っていれば起動しない。テストは空の DB に SQL を流してから始まるので、SQL とコードのずれはテストで見つかる
- **ドキュメント**: 要件定義書・機能定義書・画面仕様定義書・受け入れ確認表・API 仕様書をそろえ、実装と食い違ったら文書を直している

## API 仕様書(Swagger)

全 53 個の API を、日本語の説明つきで読めます。サーバーを起動して <http://localhost:8080/swagger-ui.html> を開きます(ログイン不要)。アプリでログインしておくと、「Try it out」で実際に API を呼べます。

| 一覧 | 1 つの API の説明 |
|---|---|
| ![Swagger UI の一覧](docs/images/swagger.png) | ![投稿の作成の API の説明](docs/images/swagger-operation.png) |

OpenAPI のファイルは [docs/openapi.yaml](docs/openapi.yaml) にもあります。コードから作っていて、古くなると自動テストが失敗して教えてくれます(作り直し: `./mvnw test -Dtest=OpenApiDocsTest -Dopenapi.update=true`)。

## 動かし方(開発用)

必要なもの: Docker Desktop、Java 21、Node.js 24

```powershell
# 1. データベース(PostgreSQL)とメール確認用サーバー(Mailpit)を起動
docker compose up -d

# 2. サーバー(Spring Boot)を、テストデータ入りで起動  → http://localhost:8080
./mvnw spring-boot:run "-Dspring-boot.run.profiles=seed"

# 3. 別の画面で、画面(React)の開発サーバーを起動  → http://localhost:5173
cd frontend
npm install
npm run dev
```

- テストデータのユーザー: `taro@example.com`(管理者)・`hanako@example.com` など 5 人。パスワードはすべて `password123`
- `seed` 付きで起動すると、**今までのデータは消えて**入れ直されます。2 回目からは `./mvnw spring-boot:run` で起動します
- 確認メールは Mailpit(<http://localhost:8025>)で読めます
- Google ログインを使うときは、`.env.example` をコピーして `.env` を作り、`GOOGLE_CLIENT_ID` と `GOOGLE_CLIENT_SECRET` を書きます(書かなくても、Google のボタンが出ないだけで他は使えます)。`.env` は git に入れません

## テスト

| 何を | どうやって | 内容 |
|---|---|---|
| サーバー | `./mvnw test` | JUnit 121 件。**結合テスト**(本物の PostgreSQL を Testcontainers で動かす。Docker が必要。S3 への保存は S3Mock で確かめる)と、DB を使わない**単体テスト**(検索の言葉・LIKE の記号の打ち消し・画像の形式とパス・ハッシュタグ。1 クラス 0.01 秒ほど) |
| 画面の単体テスト | `cd frontend && npm test` | Vitest 19 件。日時の表示・本文のリンク化・表示モード・お知らせの文 |
| 画面のコード | `cd frontend && npm run lint` | oxlint |
| 画面の操作 | `cd e2e && npm install && npm test` | Playwright で実際のブラウザ(Edge)を動かす。10 本・217 項目 |
| 見やすさの点検 | `cd e2e && npm run audit` | 全画面を 375px/1280px・ライト/ダークで開き、横スクロール・名前のない部品・フォーカスの枠などを調べる |

画面の操作テストは、データベース・サーバー(8080)・画面(5173)が動いていて、テストデータが入っている状態で流します。作ったデータは、テストの前後に消します。

**CI(GitHub Actions)**: GitHub にプッシュするたびに、サーバーのテストと画面の lint・ビルドが自動で流れます。main へのプッシュでは、画面の操作テストと公開用のイメージの作成も流れます(`.github/workflows/ci.yml`)。結果は冒頭のバッジと、GitHub の「Actions」で見られます。

**Dependabot**: 使っている部品(Maven・npm・GitHub Actions・Docker のイメージ)の新しい版と、既知の脆弱性を、毎週月曜に確かめて PR を作ります。出たばかりの版は 14 日待ってから提案します(`.github/dependabot.yml`)。

## 公開(AWS)

`Dockerfile`(画面とサーバーを 1 つにまとめる)と `compose.prod.yaml`(データベース・アプリ・Caddy)で動かします。外に開くのは 80・443 番だけです。
AWS では、EC2(t4g.micro)を**使うときだけ起動**し(止めている間は、月 1 ドル台)、アドレスは DuckDNS の無料のアドレス(<https://yarimasunesugi.duckdns.org/>)を使っています。手順は [deploy/aws/README.md](deploy/aws/README.md) にあります。

## 文書

| 文書 | 内容 |
|---|---|
| [要件定義書](docs/要件定義書.md) | 何を作るか。技術とバージョン、起動の方法、受け入れ基準 |
| [機能定義書](docs/機能定義書.md) | 各機能がどう動くか(処理・API・データ・権限) |
| [画面仕様定義書](docs/画面仕様定義書.md) | 画面の表示・操作・メッセージ |
| [受け入れ確認表](docs/受け入れ確認表.md) | 受け入れ基準を、何で確かめたか |
| [API 仕様書](docs/openapi.yaml) | OpenAPI(Swagger UI でも読める) |
| [AWS の手順書](deploy/aws/README.md) | 公開・停止・S3・バックアップ |
| [prototype/](prototype/) | 最初に作った、HTML・CSS・JavaScript だけの試作 |

## フォルダ

| フォルダ | 内容 |
|---|---|
| `src/main/java/com/example/sns/` | サーバー。機能ごとに分けている(`auth`・`post`・`profile`・`notification`・`search`・`realtime`・`admin` など) |
| `src/main/resources/db/migration/` | テーブルの定義と変更(Flyway。`V1__〜.sql` から番号の順に流れる) |
| `src/test/` | サーバーの自動テスト |
| `frontend/` | 画面(React + Vite)。ビルドすると `src/main/resources/static/` に出力する(git に入れない) |
| `e2e/` | 画面の操作テスト(Playwright)と見やすさの点検 |
| `deploy/` | 公開用の設定(Caddy)と、AWS のスクリプト |
| `docs/` | 文書と、README の画像(`docs/images/`) |

README の画面と動画は、テストデータのユーザーで撮ったものです(画像は、写真の代わりにプログラムで描いた絵です)。
