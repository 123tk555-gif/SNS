package com.example.sns.post;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.springframework.mock.web.MockMultipartFile;

/** テスト用の小さな画像ファイル */
final class TestImages {

    private TestImages() {
    }

    static MockMultipartFile image(String format, int width, int height) {
        return new MockMultipartFile("images", "photo." + format, "image/" + format, bytes(format, width, height));
    }

    static byte[] bytes(String format, int width, int height) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** WebP(拡張形式 VP8X)の先頭部分だけを作る。サーバーは中身を表示用に作り直さないので、大きさが読めればよい */
    static MockMultipartFile webp(int width, int height) {
        byte[] b = new byte[40];
        put(b, 0, "RIFF");
        put(b, 8, "WEBP");
        put(b, 12, "VP8X");
        b[16] = 10;
        int w = width - 1;
        int h = height - 1;
        b[24] = (byte) w;
        b[25] = (byte) (w >> 8);
        b[26] = (byte) (w >> 16);
        b[27] = (byte) h;
        b[28] = (byte) (h >> 8);
        b[29] = (byte) (h >> 16);
        return new MockMultipartFile("images", "photo.webp", "image/webp", b);
    }

    /** 拡張子は .png だが、中身は画像ではないファイル */
    static MockMultipartFile fake() {
        return new MockMultipartFile("images", "evil.png", "image/png",
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
    }

    private static void put(byte[] b, int offset, String ascii) {
        byte[] text = ascii.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(text, 0, b, offset, text.length);
    }
}
