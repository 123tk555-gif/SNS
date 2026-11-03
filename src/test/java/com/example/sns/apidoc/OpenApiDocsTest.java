package com.example.sns.apidoc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * API 仕様書(OpenAPI。/v3/api-docs と Swagger UI)のテスト。
 * ・コードにある API が、全部載っている(説明を書き忘れた API・新しく足した API に気づく)
 * ・全部の API と、データの形の項目に、日本語の説明がある
 * ・保存してあるファイル docs/openapi.yaml が、今のコードから作った内容と同じ(古くなったら、ここで失敗する)
 * ・ログインなしで見られる(決めた方針。API の一覧を隠すことには頼らず、守りは各 API のログイン・権限で行う)
 *
 * docs/openapi.yaml を作り直すとき: ./mvnw test -Dtest=OpenApiDocsTest -Dopenapi.update=true
 */
class OpenApiDocsTest extends ApiTestSupport {

    private static final Path SAVED = Path.of("docs", "openapi.yaml");
    private static final Pattern JAPANESE = Pattern.compile("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]");
    private static final List<String> METHODS = List.of("get", "put", "post", "delete", "patch");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private String json() throws Exception {
        return browser().get("/v3/api-docs").andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private String yaml() throws Exception {
        return browser().get("/v3/api-docs.yaml").andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    /** 仕様書に載っている API を「GET /api/…」の形で */
    private Set<String> documented(String json) {
        Set<String> found = new TreeSet<>();
        Map<String, Map<String, Object>> paths = JsonPath.read(json, "$.paths");
        paths.forEach((path, operations) -> operations.keySet().stream().filter(METHODS::contains)
                .forEach(method -> found.add(method.toUpperCase() + " " + path)));
        return found;
    }

    /** コードにある API(/api/… と /media/…)を「GET /api/…」の形で */
    private Set<String> implemented() {
        Set<String> found = new TreeSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            // このアプリのコントローラーだけ(Spring Boot の /error などは対象外)
            if (!entry.getValue().getBeanType().getPackageName().startsWith("com.example.sns")) {
                continue;
            }
            RequestMappingInfo info = entry.getKey();
            Set<String> patterns = info.getPathPatternsCondition().getPatternValues();
            for (String pattern : patterns) {
                if (!pattern.startsWith("/api/") && !pattern.startsWith("/media/")) {
                    continue;
                }
                for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                    found.add(method.name() + " " + pattern);
                }
            }
        }
        return found;
    }

    @Test
    void コードにあるAPIは_すべて仕様書に載っている() throws Exception {
        Set<String> code = implemented();
        Set<String> doc = documented(json());
        assertThat(code).as("コードにある API").hasSizeGreaterThan(40);
        assertThat(doc).as("仕様書に載っていない API(説明の書き忘れ・springdoc.paths-to-match の対象外)").containsAll(code);
        assertThat(code).as("コードにない API が、仕様書に載っている").containsAll(doc);
    }

    @Test
    void すべてのAPIに_日本語の見出しと説明と_成功の応答がある() throws Exception {
        String json = json();
        List<String> problems = new ArrayList<>();
        Map<String, Map<String, Map<String, Object>>> paths = JsonPath.read(json, "$.paths");
        Set<String> definedTags = new LinkedHashSet<>(JsonPath.<List<String>>read(json, "$.tags[*].name"));
        Set<String> usedTags = new LinkedHashSet<>();
        paths.forEach((path, operations) -> operations.forEach((method, operation) -> {
            if (!METHODS.contains(method)) {
                return;
            }
            String name = method.toUpperCase() + " " + path;
            for (String field : List.of("summary", "description")) {
                Object text = operation.get(field);
                if (!(text instanceof String s) || s.isBlank() || !JAPANESE.matcher(s).find()) {
                    problems.add(name + ": 日本語の " + field + " がない");
                }
            }
            List<String> tags = (List<String>) operation.get("tags");
            if (tags == null || tags.isEmpty()) {
                problems.add(name + ": タグがない");
            } else {
                usedTags.addAll(tags);
            }
            Map<String, Map<String, Object>> responses = (Map<String, Map<String, Object>>) operation.get("responses");
            if (responses.keySet().stream().noneMatch(code -> code.startsWith("2"))) {
                problems.add(name + ": 成功(2xx)の応答がない");
            }
            responses.forEach((code, response) -> {
                Object description = response.get("description");
                if (!(description instanceof String s) || s.isBlank() || !JAPANESE.matcher(s).find()) {
                    problems.add(name + ": 応答 " + code + " に日本語の説明がない");
                }
            });
            // 画面に出さない引数(ログイン中のユーザー)が、API の引数として出ていない
            List<Map<String, Object>> parameters = (List<Map<String, Object>>) operation.getOrDefault("parameters", List.of());
            parameters.forEach(parameter -> {
                if (parameter.get("description") == null) {
                    problems.add(name + ": 引数 " + parameter.get("name") + " に説明がない");
                }
                if (List.of("me", "login", "admin").contains(parameter.get("name"))) {
                    problems.add(name + ": ログイン中のユーザーが、引数として出ている(" + parameter.get("name") + ")");
                }
            });
        }));
        assertThat(usedTags).as("定義していないタグが使われている").isSubsetOf(definedTags);
        assertThat(definedTags).as("使われていないタグがある").isSubsetOf(usedTags);
        assertThat(problems).as("説明が足りない API").isEmpty();
    }

    @Test
    void データの形の項目には_すべて日本語の説明がある() throws Exception {
        String json = json();
        List<String> problems = new ArrayList<>();
        Map<String, Map<String, Object>> schemas = JsonPath.read(json, "$.components.schemas");
        schemas.forEach((name, schema) -> {
            Object description = schema.get("description");
            if (!(description instanceof String s) || !JAPANESE.matcher(s).find()) {
                problems.add(name + ": 日本語の説明がない");
            }
            Map<String, Map<String, Object>> properties = (Map<String, Map<String, Object>>) schema.get("properties");
            if (properties != null) {
                properties.forEach((property, definition) -> {
                    Object text = definition.get("description");
                    if (!(text instanceof String s) || !JAPANESE.matcher(s).find()) {
                        problems.add(name + "." + property + ": 日本語の説明がない");
                    }
                });
            }
        });
        assertThat(schemas).as("同じ名前のデータの形が 1 つにまとめられていない(Page など)")
                .containsKeys("PostPage", "UserPage", "NotificationPage", "PostResponse", "ApiError");
        assertThat(schemas).doesNotContainKeys("Page", "LoginUser");
        assertThat(problems).as("説明が足りないデータの形・項目").isEmpty();
    }

    @Test
    void 保存してあるdocsのopenapi_yamlは_今のコードから作った内容と同じ() throws Exception {
        String generated = normalize(yaml());
        if (Boolean.getBoolean("openapi.update")) {
            Files.writeString(SAVED, generated, StandardCharsets.UTF_8);
        }
        assertThat(SAVED).as("docs/openapi.yaml がない。作るには: ./mvnw test -Dtest=OpenApiDocsTest -Dopenapi.update=true")
                .exists();
        String saved = normalize(read(SAVED));
        assertThat(saved)
                .as("docs/openapi.yaml が、今のコードから作った内容と違う(API や説明を変えたのに、更新を忘れている)。"
                        + "作り直すには: ./mvnw test -Dtest=OpenApiDocsTest -Dopenapi.update=true")
                .isEqualTo(generated);
    }

    @Test
    void 仕様書もSwaggerUIも_ログインなしで開ける() throws Exception {
        browser().get("/v3/api-docs").andExpect(status().isOk());
        browser().get("/v3/api-docs.yaml").andExpect(status().isOk());
        // Swagger UI 本体(React の画面ではなく、Swagger UI が返っていること)
        String page = browser().get("/swagger-ui/index.html").andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("Swagger UI").doesNotContain("id=\"root\"");
        browser().get("/swagger-ui.html").andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/swagger-ui/index.html")));
        // 一覧に載っている API 自体は、ログインしていないと使えない
        browser().get("/api/timeline").andExpect(status().isUnauthorized());
    }

    @Test
    void 仕様書に_秘密の値や本物らしいパスワードが入っていない() throws Exception {
        String doc = json();
        List<Pattern> forbidden = List.of(
                Pattern.compile("password123"), // 開発用のテストデータのパスワード
                Pattern.compile("GOCSPX"), Pattern.compile("googleusercontent\\.com"), // Google ログインの鍵
                Pattern.compile("AKIA[0-9A-Z]{12,}"), // AWS のアクセスキー
                Pattern.compile("BEGIN [A-Z ]*PRIVATE KEY"),
                Pattern.compile("duckdns\\.org"), Pattern.compile("[0-9]{12}")); // 公開先のアドレス・AWS のアカウント番号
        forbidden.forEach(pattern -> assertThat(doc).as("仕様書に入ってはいけない値: " + pattern).doesNotContainPattern(pattern));
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** 改行コード(Windows の CRLF と Linux の LF の違い)と、最後の空の行の違いを無視する */
    private static String normalize(String text) {
        return text.replace("\r\n", "\n").stripTrailing() + "\n";
    }
}
