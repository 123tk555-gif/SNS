package com.example.sns.logging;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
class LoggingConfig {

    /**
     * リクエスト番号とアクセスログのフィルター。Spring Security(順番 -100)より外側で動かす。
     * プロキシの X-Forwarded-For を読むフィルター(いちばん先)のあとなので、接続元 IP は本当の利用者のものになる
     */
    @Bean
    FilterRegistrationBean<RequestLogFilter> requestLogFilter() {
        FilterRegistrationBean<RequestLogFilter> registration = new FilterRegistrationBean<>(new RequestLogFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
