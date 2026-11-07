package com.example.sns.logging;

import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * 大事な出来事のログ(機能定義書「ログ」)。出来事の種類を、項目 event.action に決まった名前(auth.login など)で残す。
 * 使い方: {@code Events.info(log, "auth.login", "ログインしました").addKeyValue("accountId", id).log();}
 *
 * <p>項目名の約束:
 * <ul>
 * <li>userId … リクエストを送った、ログイン中の人。MDC から自動で付くので、ここでは付けない(付けると項目が重なる)</li>
 * <li>accountId … 出来事の対象のアカウント(ログイン前の出来事でも使える)</li>
 * <li>パスワード・トークン・メールアドレス・投稿の本文・検索の言葉は、付けない</li>
 * </ul>
 */
public final class Events {

    public static final String ACTION = "event.action";

    private Events() {
    }

    public static LoggingEventBuilder info(Logger log, String action, String message) {
        return log.atInfo().setMessage(message).addKeyValue(ACTION, action);
    }

    public static LoggingEventBuilder warn(Logger log, String action, String message) {
        return log.atWarn().setMessage(message).addKeyValue(ACTION, action);
    }

    public static LoggingEventBuilder error(Logger log, String action, String message) {
        return log.atError().setMessage(message).addKeyValue(ACTION, action);
    }
}
