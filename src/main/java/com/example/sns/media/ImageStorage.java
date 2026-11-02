package com.example.sns.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 画像ファイルの保存・削除(NF-IMG-01〜03)。保存先は uploads/(環境変数 UPLOAD_DIR で変えられる)。
 * ファイル名は推測できない名前(UUID)にする。データベースには uploads/ からの相対パスだけを保存する。
 */
@Component
public class ImageStorage {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final String TOO_LARGE = "5MB以下の画像を選んでください";
    public static final String WRONG_FORMAT = "JPEG・PNG・WebP・GIF の画像を選んでください";

    private static final Logger log = LoggerFactory.getLogger(ImageStorage.class);

    /** 保存した画像。path は uploads/ からの相対パス(例: posts/2026/11/xxxx.webp) */
    public record StoredImage(String path, int width, int height) {
    }

    private final Path root;

    public ImageStorage(@Value("${sns.upload-dir}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    /** 中身を確かめてから folder(例: posts/2026/11)に保存する。形式が違えば 400、大きすぎれば 413 */
    public StoredImage store(MultipartFile file, String folder) {
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, TOO_LARGE);
        }
        try {
            byte[] bytes = file.getBytes();
            ImageFormat format = ImageFormat.detect(bytes);
            if (format == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, WRONG_FORMAT);
            }
            ImageFormat.Size size = format.size(bytes);
            if (size == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "画像を読み込めませんでした。別の画像を選んでください");
            }
            String path = folder + "/" + UUID.randomUUID() + "." + format.extension;
            Path target = resolve(path);
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            return new StoredImage(path, size.width(), size.height());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** uploads/ の中のファイルを指す Path。外を指す(../ を含むなど)ときは null */
    public Path resolve(String path) {
        Path resolved = root.resolve(path).normalize();
        return resolved.startsWith(root) && !resolved.equals(root) ? resolved : null;
    }

    public String contentType(Path file) {
        ImageFormat format = ImageFormat.fromExtension(file.getFileName().toString());
        return format == null ? "application/octet-stream" : format.contentType;
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

    private void deleteQuietly(String path) {
        Path file = resolve(path);
        try {
            if (file != null) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            log.warn("画像ファイルを消せませんでした: {}", path, e);
        }
    }
}
