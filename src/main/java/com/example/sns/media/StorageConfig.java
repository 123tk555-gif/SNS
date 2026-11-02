package com.example.sns.media;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * 画像の置き場所を選ぶ(application.properties の sns.storage.*)。
 * - local(初期値): サーバーのフォルダ sns.upload-dir
 * - s3: Amazon S3 のバケット sns.storage.s3.bucket。
 *   AWS の認証は、EC2 に付けた IAM ロールを使う(アクセスキーをファイルに書かない)。
 *   endpoint・access-key・secret-key は、手元で S3 の代わりの S3Mock などを使って試すときだけ書く
 */
@Configuration
class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    ImageStore imageStore(
            @Value("${sns.storage.type:local}") String type,
            @Value("${sns.upload-dir}") String uploadDir,
            @Value("${sns.storage.s3.bucket:}") String bucket,
            @Value("${sns.storage.s3.region:ap-northeast-1}") String region,
            @Value("${sns.storage.s3.endpoint:}") String endpoint,
            @Value("${sns.storage.s3.access-key:}") String accessKey,
            @Value("${sns.storage.s3.secret-key:}") String secretKey) {
        if (!"s3".equals(type)) {
            log.info("画像はフォルダ {} に保存します", uploadDir);
            return new LocalImageStore(uploadDir);
        }
        if (bucket.isBlank()) {
            throw new IllegalStateException("sns.storage.type=s3 のときは、S3_BUCKET(バケット名)を設定してください");
        }
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .httpClient(UrlConnectionHttpClient.create());
        if (!endpoint.isBlank()) {
            // S3Mock など、S3 と同じ使い方ができる別のサーバー(手元での確認用)
            builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
        }
        if (!accessKey.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        }
        log.info("画像は S3 のバケット {}(リージョン {})に保存します", bucket, region);
        return new S3ImageStore(builder.build(), bucket);
    }
}
