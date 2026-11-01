package com.example.sns.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.stereotype.Component;

/**
 * Google ログインの設定(.env の GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET)。
 * 両方が設定されているときだけ Google ログインを有効にする(設定がなくてもアプリは起動できる)。
 */
@Component
public class GoogleSettings {

    private final String clientId;
    private final String clientSecret;

    public GoogleSettings(@Value("${sns.google.client-id:}") String clientId,
            @Value("${sns.google.client-secret:}") String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public boolean enabled() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    ClientRegistrationRepository registrations() {
        // スコープ(openid・profile・email)と、戻り先 {baseUrl}/login/oauth2/code/google は Spring Security の標準のまま
        return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build());
    }
}
