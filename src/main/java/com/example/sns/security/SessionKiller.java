package com.example.sns.security;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/** あるユーザーのセッションをすべて消す(パスワードの再設定・利用停止のとき)。 */
@Component
public class SessionKiller {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionKiller(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /** exceptSessionId のセッション(今の操作をしている画面)だけは残す。null ならすべて消す */
    public void killAll(long userId, String exceptSessionId) {
        sessions.findByPrincipalName(Long.toString(userId)).keySet().stream()
                .filter(id -> !id.equals(exceptSessionId))
                .forEach(sessions::deleteById);
    }
}
