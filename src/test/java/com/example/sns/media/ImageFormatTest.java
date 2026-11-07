package com.example.sns.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sns.post.TestImages;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * 画像の形式と大きさを、ファイルの中身から読む部品の単体テスト(NF-IMG-01)。
 * 拡張子や送られてきた種類(Content-Type)は信じず、先頭のバイトで見分ける。
 */
class ImageFormatTest {

    @Test
    void 形式を中身から見分け_縦横の大きさを読む() throws Exception {
        assertSize(TestImages.bytes("png", 30, 20), ImageFormat.PNG, 30, 20);
        assertSize(TestImages.bytes("jpg", 64, 48), ImageFormat.JPEG, 64, 48);
        assertSize(TestImages.bytes("gif", 12, 34), ImageFormat.GIF, 12, 34);
        assertSize(TestImages.webp(1600, 900).getBytes(), ImageFormat.WEBP, 1600, 900);
    }

    @Test
    void 画像でない中身は_拡張子が画像でも受け付けない() throws Exception {
        assertThat(ImageFormat.detect(TestImages.fake().getBytes())).isNull();
        assertThat(ImageFormat.detect("<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(ImageFormat.detect(new byte[0])).isNull();
        assertThat(ImageFormat.detect(new byte[] {(byte) 0xFF})).isNull(); // 途中で切れている
    }

    @Test
    void 先頭は画像でも_大きさを読めないものは受け付けない() {
        byte[] pngHeaderOnly = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        assertThat(ImageFormat.detect(pngHeaderOnly)).isEqualTo(ImageFormat.PNG);
        assertThat(ImageFormat.PNG.size(pngHeaderOnly)).isNull();
    }

    @Test
    void 縦か横が20000ピクセルを超える画像と_0ピクセルの画像は受け付けない() {
        assertThat(ImageFormat.PNG.size(pngHeader(20000, 100))).isNotNull();
        assertThat(ImageFormat.PNG.size(pngHeader(20001, 100))).isNull();
        assertThat(ImageFormat.PNG.size(pngHeader(100, 30000))).isNull();
        assertThat(ImageFormat.PNG.size(pngHeader(0, 100))).isNull();
    }

    @Test
    void 保存したファイルの拡張子から種類を決める() {
        assertThat(ImageFormat.fromExtension("posts/2026/11/a.webp")).isEqualTo(ImageFormat.WEBP);
        assertThat(ImageFormat.fromExtension("a.jpg").contentType).isEqualTo("image/jpeg");
        assertThat(ImageFormat.fromExtension("a.exe")).isNull();
    }

    private static void assertSize(byte[] bytes, ImageFormat expected, int width, int height) {
        ImageFormat format = ImageFormat.detect(bytes);
        assertThat(format).isEqualTo(expected);
        assertThat(format.size(bytes)).isEqualTo(new ImageFormat.Size(width, height));
    }

    /** PNG の先頭(署名と IHDR の幅・高さ)だけを作る */
    private static byte[] pngHeader(int width, int height) {
        return ByteBuffer.allocate(24)
                .put(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})
                .putInt(13).put("IHDR".getBytes(StandardCharsets.US_ASCII))
                .putInt(width).putInt(height)
                .array();
    }
}
