package com.example.sns.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/** 画像をサーバーのフォルダ(sns.upload-dir。初期値 uploads/)に置く。開発中に使う */
class LocalImageStore implements ImageStore {

    private final Path root;

    LocalImageStore(String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public void put(String path, byte[] bytes, String contentType) {
        Path target = resolve(path);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<Resource> get(String path) {
        Path file = resolve(path);
        return Files.isRegularFile(file) ? Optional.of(new FileSystemResource(file)) : Optional.empty();
    }

    @Override
    public void delete(String path) {
        try {
            Files.deleteIfExists(resolve(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 念のため、保存場所の外を指していないことも確かめる(パスの形は ImageStorage で確かめ済み) */
    private Path resolve(String path) {
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalArgumentException("保存場所の外を指しています: " + path);
        }
        return resolved;
    }
}
