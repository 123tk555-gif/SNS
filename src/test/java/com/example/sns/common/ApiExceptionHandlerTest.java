package com.example.sns.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.sns.logging.RequestLogFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

/** 予想外のエラーの受け皿(単体テスト。Spring は起動しない) */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);

    @BeforeEach
    void setUp() {
        appender.start();
        logger.addAppender(appender);
        MDC.put(RequestLogFilter.REQUEST_ID, "a1b2c3d4e5f6");
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        MDC.clear();
    }

    @Test
    void 予想外の例外は500とエラー番号を返し_スタックトレースごとERRORで残す() throws Exception {
        ResponseEntity<ApiError> response = handler.unexpected(new IllegalStateException("バグ"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo(ApiExceptionHandler.UNEXPECTED + "(エラー番号: a1b2c3d4e5f6)");
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("バグ");
            assertThat(event.getMDCPropertyMap()).containsEntry(RequestLogFilter.REQUEST_ID, "a1b2c3d4e5f6");
        });
    }

    @Test
    void Springの決まったエラーは_その状態のまま返しERRORにしない() throws Exception {
        ResponseEntity<ApiError> response = handler.unexpected(new HttpRequestMethodNotSupportedException("PUT"));

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getHeaders().getAllow()).isNotNull();
        assertThat(appender.list).isEmpty();
    }

    @Test
    void ログイン_権限の例外は投げ直して_Spring_Securityに任せる() {
        AccessDeniedException denied = new AccessDeniedException("だめ");
        assertThatThrownBy(() -> handler.unexpected(denied)).isSameAs(denied);
        assertThat(appender.list).isEmpty();
    }
}
