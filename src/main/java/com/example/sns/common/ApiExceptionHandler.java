package com.example.sns.common;

import com.example.sns.logging.Events;
import com.example.sns.logging.RequestLogFilter;
import com.example.sns.media.ImageStorage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** エラーのときも、画面側が読みやすい {"message": "...", "errors": [...]} の形で返す(taskboard と同じ形)。 */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    static final String UNEXPECTED = "サーバーでエラーが起きました。時間をおいて、もう一度お試しください";
    static final String BAD_REQUEST = "リクエストが正しくありません";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException e) {
        List<ApiError.FieldProblem> problems = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldProblem(f.getField(), f.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ApiError("入力内容に誤りがあります", problems));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ApiError.of("リクエストの形式が正しくありません"));
    }

    /** 画像が 5MB を超えた(application.properties の spring.servlet.multipart.max-file-size) */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(ApiError.of(ImageStorage.TOO_LARGE));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> invalidParameter(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(ApiError.of("パラメータ「" + e.getName() + "」の値が正しくありません"));
    }

    /** 存在しない URL。管理者以外が管理者の API を呼んだときと同じ応答にする(SecurityConfig) */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(ApiError.NOT_FOUND_MESSAGE));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> status(ResponseStatusException e) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        String message = e.getReason() != null ? e.getReason() : status.getReasonPhrase();
        return ResponseEntity.status(status).body(ApiError.of(message));
    }

    /**
     * 上のどれにも当たらない例外。
     * - Spring の決まったエラー(405 メソッドが違う・415 形式が違う など)は、その状態のまま返す
     * - Spring Security の例外は投げ直して、今までどおり 401・403 にする
     * - 通信が切れただけ(SSE の切断など)は、何もしない
     * - それ以外は予想外のエラー(バグ)。スタックトレースごと ERROR で記録し、利用者には 500 とエラー番号を返す。
     *   エラー番号はリクエスト番号(RequestLogFilter)なので、問い合わせの番号から、そのときのログを探せる
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) throws Exception {
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e;
        }
        if (e instanceof AsyncRequestNotUsableException) {
            return null;
        }
        if (e instanceof ErrorResponse known && known.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = known.getStatusCode();
            return ResponseEntity.status(status).headers(known.getHeaders()).body(ApiError.of(BAD_REQUEST));
        }
        Events.error(log, "http.unexpected_error", "予想外のエラーが起きました").setCause(e).log();
        String requestId = RequestLogFilter.currentRequestId();
        String message = requestId == null ? UNEXPECTED : UNEXPECTED + "(エラー番号: " + requestId + ")";
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of(message));
    }
}
