package com.example.sns.media;

import java.util.Optional;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * 画像を Amazon S3 のバケットに置く。公開用で使う。
 * バケットは公開しない(パブリックアクセスはすべてブロック)。画像はアプリ(/media/…)を通して、ログイン中の人にだけ渡す。
 * キーは「uploads/」+ 相対パス(バケットの中で、バックアップ backups/ やログ logs/ と分けるため)。
 */
class S3ImageStore implements ImageStore {

    static final String PREFIX = "uploads/";

    private final S3Client s3;
    private final String bucket;

    S3ImageStore(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void put(String path, byte[] bytes, String contentType) {
        s3.putObject(request -> request.bucket(bucket).key(PREFIX + path).contentType(contentType),
                RequestBody.fromBytes(bytes));
    }

    @Override
    public Optional<Resource> get(String path) {
        try {
            // 中身は読みながらそのままブラウザへ流す(画像全体をメモリに載せない)
            return Optional.of(new InputStreamResource(
                    s3.getObject(request -> request.bucket(bucket).key(PREFIX + path))));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String path) {
        s3.deleteObject(request -> request.bucket(bucket).key(PREFIX + path));
    }
}
