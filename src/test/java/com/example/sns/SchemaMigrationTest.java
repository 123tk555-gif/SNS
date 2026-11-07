package com.example.sns;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * テーブルの変更管理(Flyway)。
 * ほかの結合テストも、すべて「空の DB に V1 から流す → Hibernate がコードとテーブルを照らし合わせる(validate)」で
 * 起動するので、SQL とコードが食い違えば、どのテストも起動の段階で失敗する。
 * ここでは、Flyway の動き方そのものを確かめる。
 */
class SchemaMigrationTest extends ApiTestSupport {

    @Autowired
    Flyway flyway;

    @Test
    void 空のDBでは_V1のSQLが本当に流れる() {
        // 「済みとして扱った(BASELINE)」ではなく、SQL を実行したこと
        List<Map<String, Object>> history = jdbc.queryForList(
                "select version, type, success from flyway_schema_history where version is not null order by installed_rank");
        assertThat(history).first().satisfies(row -> {
            assertThat(row.get("version")).isEqualTo("1");
            assertThat(row.get("type")).isEqualTo("SQL");
            assertThat(row.get("success")).isEqualTo(true);
        });
        assertThat(flyway.info().pending()).as("流していない SQL が残っていない").isEmpty();
    }

    @Test
    void Flywayを入れる前からテーブルがあるDBは_V1を流さず済みとして扱う() {
        // 本番・開発用の DB と同じ状況を、別のスキーマで作る(アプリと同じ Flyway の設定を使う)
        jdbc.execute("drop schema if exists legacy cascade");
        jdbc.execute("create schema legacy");
        jdbc.execute("create table legacy.users (id bigint primary key)");
        try {
            Flyway legacy = Flyway.configure()
                    .configuration(flyway.getConfiguration())
                    .schemas("legacy")
                    .defaultSchema("legacy")
                    .load();
            legacy.migrate();

            List<Map<String, Object>> history = jdbc.queryForList(
                    "select version, type from legacy.flyway_schema_history where version is not null");
            assertThat(history).singleElement().satisfies(row -> {
                assertThat(row.get("version")).isEqualTo("1");
                assertThat(row.get("type")).isEqualTo("BASELINE");
            });
            // V1 は流れていない(流れていれば posts などのテーブルができている)
            Integer posts = jdbc.queryForObject(
                    "select count(*) from information_schema.tables where table_schema = 'legacy' and table_name = 'posts'",
                    Integer.class);
            assertThat(posts).isZero();
        } finally {
            jdbc.execute("drop schema legacy cascade");
        }
    }
}
