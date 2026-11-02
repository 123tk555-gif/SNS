package com.example.sns.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sns.ApiTestSupport;
import com.example.sns.Browser;
import com.example.sns.post.TestImages;
import com.example.sns.user.Role;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** プロフィールの編集(F-PROF-02。機能定義書 3.2)。 */
class SettingsApiTest extends ApiTestSupport {

    private Browser taro;

    @BeforeEach
    void setUp() throws Exception {
        createUser("taro", Role.USER);
        taro = loggedInAs("taro");
    }

    private String upload(String url, MockMultipartFile file) throws Exception {
        return taro.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, url).file(file)
                        .with(csrf()))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void 表示名と自己紹介を変えられる_自己紹介は空なら未設定() throws Exception {
        taro.put("/api/me/profile", "{\"displayName\":\"  たろう  \",\"bio\":\"Java を勉強中\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("たろう"))
                .andExpect(jsonPath("$.bio").value("Java を勉強中"));
        taro.put("/api/me/profile", "{\"displayName\":\"たろう\",\"bio\":\"   \"}")
                .andExpect(jsonPath("$.bio").isEmpty());
        taro.get("/api/auth/me").andExpect(jsonPath("$.displayName").value("たろう"));
    }

    @Test
    void 表示名は1から50文字_自己紹介は160文字まで() throws Exception {
        taro.put("/api/me/profile", "{\"displayName\":\"  \",\"bio\":\"\"}").andExpect(status().isBadRequest());
        taro.put("/api/me/profile", "{\"displayName\":\"" + "あ".repeat(51) + "\",\"bio\":\"\"}")
                .andExpect(status().isBadRequest());
        taro.put("/api/me/profile", "{\"displayName\":\"たろう\",\"bio\":\"" + "あ".repeat(161) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("自己紹介は160文字以内で入力してください"));
        taro.put("/api/me/profile", "{\"displayName\":\"" + "😀".repeat(50) + "\",\"bio\":\"\"}")
                .andExpect(status().isOk());
    }

    @Test
    void アイコン画像とヘッダー画像を変え_消すと古いファイルも消える() throws Exception {
        MockMultipartFile first = new MockMultipartFile("file", "a.png", "image/png", TestImages.bytes("png", 8, 8));
        String body = upload("/api/me/avatar", first);
        String firstUrl = JsonPath.read(body, "$.avatarUrl");
        assertThat(firstUrl).startsWith("/media/avatars/");
        taro.get(firstUrl).andExpect(status().isOk());
        taro.get("/api/auth/me").andExpect(jsonPath("$.avatarUrl").value(firstUrl));

        MockMultipartFile second = new MockMultipartFile("file", "b.png", "image/png", TestImages.bytes("png", 9, 9));
        String secondUrl = JsonPath.read(upload("/api/me/avatar", second), "$.avatarUrl");
        assertThat(secondUrl).isNotEqualTo(firstUrl);
        taro.get(firstUrl).andExpect(status().isNotFound());

        taro.delete("/api/me/avatar").andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").isEmpty());
        taro.get(secondUrl).andExpect(status().isNotFound());

        String headerUrl = JsonPath.read(upload("/api/me/header",
                new MockMultipartFile("file", "h.png", "image/png", TestImages.bytes("png", 30, 10))), "$.headerUrl");
        assertThat(headerUrl).startsWith("/media/headers/");
        // 投稿のカードにも、新しいアイコンが出る
        upload("/api/me/avatar", new MockMultipartFile("file", "c.png", "image/png", TestImages.bytes("png", 8, 8)));
        taro.multipart("/api/posts", List.of(), "text", "アイコンを変えました")
                .andExpect(jsonPath("$.author.avatarUrl").isNotEmpty());
    }

    @Test
    void 画像でないファイルはアイコンにできない() throws Exception {
        taro.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/me/avatar")
                        .file(new MockMultipartFile("file", "x.png", "image/png", "not image".getBytes()))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
