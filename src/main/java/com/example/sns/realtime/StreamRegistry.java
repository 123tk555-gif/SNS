package com.example.sns.realtime;

import java.io.IOException;
import java.time.Duration;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE の接続を、ログイン中のユーザーごとにメモリで管理する(機能定義書 3.8。サーバーは 1 台を前提とする)。
 * 1 人が複数のタブで開いてもよい(1 人 5 接続まで。超えたら古いものから切る)。
 */
@Component
public class StreamRegistry {

    static final int MAX_PER_USER = 5;
    /** 接続はこの時間で切れる。ブラウザ(EventSource)が自動でつなぎ直す */
    static final long TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();

    private final Map<Long, Deque<SseEmitter>> streams = new ConcurrentHashMap<>();

    public SseEmitter open(long userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        Deque<SseEmitter> userStreams = streams.computeIfAbsent(userId, id -> new ConcurrentLinkedDeque<>());
        userStreams.add(emitter);
        while (userStreams.size() > MAX_PER_USER) {
            SseEmitter oldest = userStreams.pollFirst();
            if (oldest != null) {
                oldest.complete();
            }
        }
        Runnable remove = () -> remove(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        // すぐに 1 行送り、ブラウザに「つながった」ことを知らせる
        send(userId, emitter, SseEmitter.event().comment("connected"));
        return emitter;
    }

    /** ある人の接続すべてに送る */
    public void sendTo(long userId, String name, Object data) {
        Deque<SseEmitter> userStreams = streams.get(userId);
        if (userStreams != null) {
            userStreams.forEach(emitter -> send(userId, emitter, SseEmitter.event().name(name).data(data)));
        }
    }

    /** 接続中の全員に送る。dataFor(ユーザーの ID) が null を返した人には送らない */
    public void broadcast(String name, Function<Long, Object> dataFor) {
        streams.forEach((userId, userStreams) -> {
            Object data = dataFor.apply(userId);
            if (data != null) {
                userStreams.forEach(emitter -> send(userId, emitter, SseEmitter.event().name(name).data(data)));
            }
        });
    }

    /** ある人の接続をすべて切る(利用停止にしたとき) */
    public void disconnect(long userId) {
        Deque<SseEmitter> userStreams = streams.remove(userId);
        if (userStreams != null) {
            userStreams.forEach(SseEmitter::complete);
        }
    }

    public int connections(long userId) {
        Deque<SseEmitter> userStreams = streams.get(userId);
        return userStreams == null ? 0 : userStreams.size();
    }

    /** 30 秒ごとに中身のない合図(コメント行)を送る。届かない接続はここで見つけて片付ける */
    @Scheduled(fixedRate = 30_000)
    void heartbeat() {
        streams.forEach((userId, userStreams) ->
                userStreams.forEach(emitter -> send(userId, emitter, SseEmitter.event().comment("keep-alive"))));
    }

    private void send(long userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // ブラウザが閉じられたなど。この接続は片付ける
            remove(userId, emitter);
        }
    }

    private void remove(long userId, SseEmitter emitter) {
        streams.computeIfPresent(userId, (id, userStreams) -> {
            userStreams.remove(emitter);
            return userStreams.isEmpty() ? null : userStreams;
        });
    }
}
