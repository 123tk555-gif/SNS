package com.example.sns;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * テスト用の「ブラウザ」。受け取った Cookie(SESSION など)を覚えておき、次のリクエストに付ける。
 * 2 つ作れば、別々の端末でログインした状態を再現できる。データを変える操作には CSRF 対策の値を付ける。
 */
public class Browser {

    private final MockMvc mvc;
    private final Map<String, Cookie> cookies = new LinkedHashMap<>();

    public Browser(MockMvc mvc) {
        this.mvc = mvc;
    }

    public ResultActions get(String url) throws Exception {
        return perform(MockMvcRequestBuilders.get(url));
    }

    public ResultActions post(String url, String json) throws Exception {
        return perform(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(json).with(csrf()));
    }

    public ResultActions put(String url, String json) throws Exception {
        return perform(MockMvcRequestBuilders.put(url).contentType(MediaType.APPLICATION_JSON).content(json).with(csrf()));
    }

    /** CSRF 対策の値を付けずに送る(断られることを確かめる用) */
    public ResultActions postWithoutCsrf(String url, String json) throws Exception {
        return perform(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        if (!cookies.isEmpty()) {
            request.cookie(cookies.values().toArray(Cookie[]::new));
        }
        ResultActions result = mvc.perform(request);
        for (Cookie cookie : result.andReturn().getResponse().getCookies()) {
            if (cookie.getMaxAge() == 0) {
                cookies.remove(cookie.getName());
            } else {
                cookies.put(cookie.getName(), cookie);
            }
        }
        return result;
    }
}
