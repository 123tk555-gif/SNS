package com.example.sns.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.ApiTestSupport;
import com.example.sns.Browser;
import com.example.sns.post.TestImages;
import com.example.sns.user.Role;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.GenericContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * 画像の置き場所を S3 にしたとき(公開用の設定)の動き。S3 の代わりに、同じ使い方ができる S3Mock(Adobe)を Docker で動かす。
 * 投稿の画像・アイコン画像の保存、/media での配信、削除したときにファイルも消えることを確かめる。
 */
class S3StorageApiTest extends ApiTestSupport {

    private static final String BUCKET = "sns-test";
    private static final GenericContainer<?> S3MOCK = new GenericContainer<>("adobe/s3mock:4.7.0").withExposedPorts(9090);
    private static S3Client s3;

    @BeforeAll
    static void startS3() {
        S3MOCK.start();
        s3 = S3Client.builder()
                .region(Region.AP_NORTHEAST_1)
                .endpointOverride(URI.create(endpoint()))
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .httpClient(UrlConnectionHttpClient.create())
                .build();
        s3.createBucket(request -> request.bucket(BUCKET));
    }

    private static String endpoint() {
        return "http://" + S3MOCK.getHost() + ":" + S3MOCK.getMappedPort(9090);
    }

    @AfterAll
    static void stopS3() {
        s3.close();
        S3MOCK.stop();
    }

    @DynamicPropertySource
    static void s3Properties(DynamicPropertyRegistry registry) {
        if (!S3MOCK.isRunning()) {
            S3MOCK.start();
        }
        registry.add("sns.storage.type", () -> "s3");
        registry.add("sns.storage.s3.bucket", () -> BUCKET);
        registry.add("sns.storage.s3.endpoint", S3StorageApiTest::endpoint);
        registry.add("sns.storage.s3.access-key", () -> "test");
        registry.add("sns.storage.s3.secret-key", () -> "test");
    }

    private Browser taro;

    @BeforeEach
    void setUp() throws Exception {
        createUser("taro", Role.USER);
        taro = loggedInAs("taro");
    }

    private boolean existsInS3(String mediaUrl) {
        String key = S3ImageStore.PREFIX + mediaUrl.substring("/media/".length());
        try {
            s3.headObject(request -> request.bucket(BUCKET).key(key));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Test
    void 投稿の画像はS3に保存され_アプリを通して取れ_投稿を消すとS3からも消える() throws Exception {
        String body = taro.multipart("/api/posts", List.of(TestImages.image("png", 40, 30)), "text", "S3 に保存")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        String url = JsonPath.read(body, "$.images[0].url");
        assertThat(url).startsWith("/media/posts/");
        assertThat(existsInS3(url)).isTrue();

        byte[] expected = TestImages.bytes("png", 40, 30);
        taro.get(url).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(expected));
        // ログインしていない人には渡さない(バケットも公開しない)
        browser().get(url).andExpect(status().isUnauthorized());

        taro.delete("/api/posts/" + id).andExpect(status().isNoContent());
        assertThat(existsInS3(url)).isFalse();
        taro.get(url).andExpect(status().isNotFound());
    }

    @Test
    void アイコン画像を変えると_古い画像はS3から消える() throws Exception {
        String first = JsonPath.read(changeAvatar(10), "$.avatarUrl");
        String second = JsonPath.read(changeAvatar(12), "$.avatarUrl");
        assertThat(existsInS3(first)).isFalse();
        assertThat(existsInS3(second)).isTrue();
        taro.delete("/api/me/avatar").andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").isEmpty());
        assertThat(existsInS3(second)).isFalse();
    }

    @Test
    void 形の違うパスとないファイルは404() throws Exception {
        taro.get("/media/posts/no-such.png").andExpect(status().isNotFound());
        taro.get("/media/posts/2026/11/00000000-0000-0000-0000-000000000000.png").andExpect(status().isNotFound());
    }

    private String changeAvatar(int size) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", TestImages.bytes("png", size, size));
        return taro.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/me/avatar").file(file).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
}
