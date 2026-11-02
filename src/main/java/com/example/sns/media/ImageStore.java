package com.example.sns.media;

import java.util.Optional;
import org.springframework.core.io.Resource;

/**
 * 画像ファイルの置き場所(NF-IMG-03)。開発中はサーバーのフォルダ(LocalImageStore)、
 * 公開用では Amazon S3(S3ImageStore)を使う。どちらを使うかは sns.storage.type で決める(StorageConfig)。
 *
 * path は「posts/2026/11/xxxx.webp」のような、保存場所の中の相対パス。ImageStorage が形を確かめてから渡す。
 */
interface ImageStore {

    void put(String path, byte[] bytes, String contentType);

    /** ファイルの中身。なければ空 */
    Optional<Resource> get(String path);

    /** ファイルを消す。もともとなくてもよい */
    void delete(String path);
}
