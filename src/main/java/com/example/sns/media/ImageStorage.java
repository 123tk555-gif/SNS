package com.example.sns.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 画像ファイルの保存・削除(NF-IMG-01〜03)。中身で形式を確かめ、推測できない名前(UUID)を付けて保存する。
 * データベースには、保存場所の中の相対パス(例: posts/2026/11/xxxx.webp)だけを保存する。
 * 実際の置き場所(フォルダか S3 か)は ImageStore が受け持つ。
 */
@Component
public class ImageStorage {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final String TOO_LARGE = "5MB以下の画像を選んでください";
    public static final String WRONG_FORMAT = "JPEG・PNG・WebP・GIF の画像を選んでください";

    /** このアプリが作るパスの形(フォルダは英小文字と数字、ファイル名は UUID)。これ以外は受け付けない */
    private static final Pattern PATH = Pattern.compile("[a-z0-9]+(/[a-z0-9]+)*/[0-9a-f-]{36}\\.[a-z]{3,4}");

    private static final Logger log = LoggerFactory.getLogger(ImageStorage.class);

    /** 保存した画像。path は保存場所の中の相対パス(例: posts/2026/11/xxxx.webp) */
    public record StoredImage(String path, int width, int height) {
    }

    private final ImageStore store;

    ImageStorage(ImageStore store) {
        this.store = store;
    }

    /** 中身を確かめてから folder(例: posts/2026/11)に保存する。形式が違えば 400、大きすぎれば 413 */
    public StoredImage store(MultipartFile file, String folder) {
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, TOO_LARGE);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ImageFormat format = ImageFormat.detect(bytes);
        if (format == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, WRONG_FORMAT);
        }
        ImageFormat.Size size = format.size(bytes);
        if (size == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "画像を読み込めませんでした。別の画像を選んでください");
        }
        String path = folder + "/" + UUID.randomUUID() + "." + format.extension;
        store.put(path, bytes, format.contentType);
        return new StoredImage(path, size.width(), size.height());
    }

    /** 画像の中身。形の違うパス(../ を含むなど)や、ないファイルは空 */
    public Optional<Resource> open(String path) {
        return isValid(path) ? store.get(path) : Optional.empty();
    }

    public String contentType(String path) {
        ImageFormat format = ImageFormat.fromExtension(path);
        return format == null ? "application/octet-stream" : format.contentType;
    }

    static boolean isValid(String path) {
        return path != null && PATH.matcher(path).matches();
    }

    /** データベースの変更が確定したあとでファイルを消す(取り消されたら消さない) */
    public void deleteAfterCommit(List<String> paths) {
        runAfter(paths, TransactionSynchronization.STATUS_COMMITTED);
    }

    /** データベースの変更が取り消されたら、保存したファイルを消す(使われないファイルを残さない) */
    public void deleteOnRollback(List<String> paths) {
        runAfter(paths, TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    private void runAfter(List<String> paths, int expectedStatus) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == expectedStatus) {
                    paths.forEach(ImageStorage.this::deleteQuietly);
                }
            }
        });
    }

    /** 消せなくても、利用者の操作は失敗にしない(使われないファイルが残るだけ)。ログに残す */
    private void deleteQuietly(String path) {
        if (!isValid(path)) {
            return;
        }
        try {
            store.delete(path);
        } catch (RuntimeException e) {
            log.warn("画像ファイルを消せませんでした: {}", path, e);
        }
    }
}
