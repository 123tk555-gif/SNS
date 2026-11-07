# 負荷テスト(k6)

同時に何人まで、どのくらいの速さで使えるかを測る。**本番(AWS)には流さない**(料金と、ほかの利用者への影響のため)。
手元の PC で、公開用の構成(Caddy の HTTPS・アプリ・PostgreSQL)を、本番のサーバー(t4g.micro: 2 vCPU・メモリ 1GB)に近づけて動かし、
そこへ [k6](https://k6.io/)(Docker のイメージ `grafana/k6:2.3.0`。PC には入れない)で流す。

| ファイル | 内容 |
|---|---|
| `compose.load.yaml` | `compose.prod.yaml` に重ねる設定。CPU とメモリの制限、テストデータ(seed)、k6 |
| `load.env` | 負荷テスト専用の設定(本物ではない値)。これを渡すと、リポジトリの `.env`(秘密の値)は読まれない |
| `data.sql` | 負荷テスト用のデータ(ユーザー 30 人・投稿 5,000 件・いいね・フォロー・ハッシュタグ) |
| `timeline.js` | k6 の台本 |
| `analyze.cjs` | 1 件ずつの記録(CSV)を、API ごと・30 秒ごとにまとめる |

## 流し方(PowerShell / Git Bash。リポジトリの一番上で)

```bash
# 1. 公開用の構成を、負荷テスト用の設定で起動(初回はイメージを作るので数分)。プロジェクト名 sns-load で、ふだんの DB とは別
docker compose -p sns-load --env-file load/load.env -f compose.prod.yaml -f load/compose.load.yaml up -d --build --wait db app caddy

# 2. 負荷テスト用のデータを足す(アプリは起動のたびにテストデータを入れ直すので、起動するたびに流す)
docker compose -p sns-load --env-file load/load.env -f compose.prod.yaml -f load/compose.load.yaml exec -T db psql -U sns -d sns -q < load/data.sql

# 3. 流す(約 3 分半)。人数は -e VUS=60 などで変えられる(初期値 30)。--out で 1 件ずつの記録を load/results/ に残す
docker compose -p sns-load --env-file load/load.env -f compose.prod.yaml -f load/compose.load.yaml run --rm k6 run -e VUS=30 --out csv=/results/run.csv /scripts/timeline.js
node load/analyze.cjs load/results/run.csv

# 4. 片付ける(コンテナとデータを消す)
docker compose -p sns-load --env-file load/load.env -f compose.prod.yaml -f load/compose.load.yaml down -v
```

Git Bash では、3 の前に `export MSYS_NO_PATHCONV=1`(`/scripts/...` を Windows のパスに書き換えさせない)。
CPU の割り当て(`cpuset`)は 8 コアの PC 向け。コアが少ない PC では `compose.load.yaml` の k6 の `cpuset` を直す。

## 台本の中身

- 仮想ユーザー(VU)を 1 → 30 人に 1 分かけて増やし、30 人のまま 2 分保ち、15 秒で 0 人に戻す
- 各ユーザーは最初にログインし、そのあとくり返す: ホームを開く(`me`・タイムライン「すべて」か「フォロー中」・トレンド・未読の数)→ 1〜3 秒読む →
  30% 続きを読む / 15% 検索 / 30% いいね / 10% 投稿 / 15% 何もしない → 1〜3 秒待つ
- **合格の目安(しきい値)**: 読み込みの 95% が 800ms 以内、書き込み(投稿・いいね)の 95% が 1 秒以内、エラーが 1% 未満、チェックの成功が 99% 以上
- 入っていないもの: 画像のアップロード、リアルタイム配信(SSE)の接続(k6 の標準の機能では、つなぎっぱなしにできない)

## 結果(2026-11-07)

データ: ユーザー 35 人・投稿 5,076 件・いいね 約 17,600 件・フォロー 346 件。

### 本番に近い形: DB・アプリ・Caddy が同じ 2 コアを分け合う

| 人数 | 1 秒あたりの処理 | 読み込みの 95% | 書き込みの 95% | エラー | メモリの最大(アプリ / DB / Caddy) |
|---|---|---|---|---|---|
| 30 人 | 28.7 件 | 37ms | 56ms | 0 件(5,746 件中) | 399MB / 72MB / 21MB |
| 60 人 | 58.2 件 | 29ms | 42ms | 0 件(11,547 件中) | 401MB / 78MB / 24MB |

- しきい値はすべて満たした。人数を 2 倍にすると処理の数もほぼ 2 倍で、60 人でもまだ頭打ちではない
- API ごとの 95%(30 人): タイムライン 34〜40ms、トレンド 38ms、検索 76ms、いいね 54ms、投稿 66ms、ログイン 142ms(パスワードの照合はわざと重い)
- メモリは 3 つで最大 約 500MB。本番の 1GB に収まる(アプリの Java のヒープは 256MB まで)

### 厳しめ: コンテナごとに CPU を仕切る(アプリ 1 コア・DB 0.5 コア・Caddy 0.5 コア)

| 人数 | 読み込みの 95% | 書き込みの 95% | エラー | 結果 |
|---|---|---|---|---|
| 30 人(1 回目) | 893ms | 1.05 秒 | 0 件 | 不合格。起動直後の 2 分(Java のウォームアップとログインが重なる)が遅い |
| 30 人(2 回目) | 438ms | 688ms | 0 件 | 合格。2 分を過ぎてからは 95% が 92〜169ms |
| 60 人 | 1.69 秒 | 1.99 秒 | 0 件 | 不合格。DB が 0.5 コアの上限に張り付き、すべての API が順番待ちで遅くなる(1 秒あたり約 35 件で頭打ち) |

### わかったこと

- 30〜60 人が同時に使っても、エラーは出ず、ふだんの速さは 1 件 数十ms。メモリにも余裕がある
- **起動直後の 1〜2 分は遅い**(Java が、よく使う処理を速い形に直している途中)。アプリを更新した直後は、すぐに大勢で使わない
- CPU が足りなくなると、まず DB が詰まる。そのときは、すべての API が一様に遅くなる
- **この結果は楽観的**。手元の PC の CPU は、本番の t4g.micro(Graviton2)より 1 コアあたり速い。また t4g.micro は、ふだん使える CPU が
  1 コアあたり 10% までで、それを超える分は「CPU クレジット」を使う(使い切ると、追加の料金がかかる設定)。長く負荷が続く使い方なら、
  本番と同じ種類のサーバーを別に用意して測る
