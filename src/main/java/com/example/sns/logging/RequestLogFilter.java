package com.example.sns.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * リクエストごとの番号(requestId)とアクセスログ(機能定義書「ログ」)。
 * <ul>
 * <li>リクエストごとに 12 文字の番号を作り、そのリクエストの間に出るすべてのログに付ける(MDC)。応答のヘッダー X-Request-Id にも付ける。
 *     予想外のエラーでは、利用者にもこの番号を見せる(ApiExceptionHandler)ので、問い合わせの番号でログを探せる</li>
 * <li>API と画像(/api/**・/media/**)は、終わったときにアクセスログを 1 行出す。クエリ文字列は残さない(検索の言葉などが入るため)</li>
 * </ul>
 * いちばん外側(Spring Security より外)で動かすので、ログインが必要な API の 401・403 も記録される。
 */
public class RequestLogFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String REQUEST_ID = "requestId";
    public static final String USER_ID = "userId";

    /** アクセスログのロガー(ほかのログと分けて絞り込めるように、名前を決めておく) */
    static final Logger ACCESS = LoggerFactory.getLogger("com.example.sns.access");

    /** ログイン中のユーザーの ID を、このリクエストのすべてのログ(アクセスログも)に付ける(ログインを確かめた所・ログインした所から呼ぶ) */
    public static void setUser(long userId) {
        MDC.put(USER_ID, Long.toString(userId));
    }

    /** 今のリクエストの番号(リクエストの外なら null) */
    public static String currentRequestId() {
        return MDC.get(REQUEST_ID);
    }

    static String newRequestId() {
        byte[] bytes = new byte[6];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = newRequestId();
        MDC.put(REQUEST_ID, requestId);
        response.setHeader(HEADER, requestId);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            try {
                if (isLogged(request.getRequestURI())) {
                    logAccess(request, response, (System.nanoTime() - start) / 1_000_000);
                }
            } finally {
                MDC.remove(REQUEST_ID);
                MDC.remove(USER_ID);
            }
        }
    }

    private static boolean isLogged(String uri) {
        return uri.startsWith("/api/") || uri.startsWith("/media/");
    }

    private void logAccess(HttpServletRequest request, HttpServletResponse response, long millis) {
        int status = response.getStatus();
        boolean async = isAsyncStarted(request);
        var event = ACCESS.atLevel(status >= 500 ? Level.WARN : Level.INFO)
                .setMessage("{} {} {} {}ms")
                .addArgument(request.getMethod())
                .addArgument(request.getRequestURI())
                .addArgument(status)
                .addArgument(millis)
                .addKeyValue("event.action", "http.request")
                .addKeyValue("http.request.method", request.getMethod())
                .addKeyValue("url.path", request.getRequestURI())
                .addKeyValue("http.response.status_code", status)
                .addKeyValue("event.duration_ms", millis)
                .addKeyValue("client.ip", request.getRemoteAddr());
        if (request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) instanceof String route) {
            event = event.addKeyValue("http.route", route);
        }
        if (async) {
            // SSE(/api/stream)など。つないだ時点の記録(終わるのは、ずっとあと)
            event = event.addKeyValue("async", true);
        }
        event.log();
    }
}
