package com.example.sns.media;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 受け付ける画像の形式(NF-IMG-01)。拡張子ではなく、ファイルの先頭のバイトで見分け、縦横の大きさも中身から読む。
 * 画像を表示用に作り直す(デコードする)ことはしないので、壊れた画像や巨大な画像でもサーバーの負担にならない。
 */
enum ImageFormat {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    GIF("gif", "image/gif"),
    WEBP("webp", "image/webp");

    /** これより大きい(縦か横が超える)画像は受け付けない */
    static final int MAX_DIMENSION = 20000;

    record Size(int width, int height) {
    }

    final String extension;
    final String contentType;

    ImageFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    static ImageFormat detect(byte[] b) {
        if (startsWith(b, 0, 0xFF, 0xD8, 0xFF)) {
            return JPEG;
        }
        if (startsWith(b, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return PNG;
        }
        if (ascii(b, 0, "GIF87a") || ascii(b, 0, "GIF89a")) {
            return GIF;
        }
        if (ascii(b, 0, "RIFF") && ascii(b, 8, "WEBP")) {
            return WEBP;
        }
        return null;
    }

    static ImageFormat fromExtension(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1);
        return Arrays.stream(values()).filter(f -> f.extension.equals(extension)).findFirst().orElse(null);
    }

    /** 縦横の大きさ。読み取れない・大きすぎるときは null */
    Size size(byte[] b) {
        Size size = switch (this) {
            case PNG -> b.length >= 24 && ascii(b, 12, "IHDR") ? new Size(int32be(b, 16), int32be(b, 20)) : null;
            case GIF -> b.length >= 10 ? new Size(int16le(b, 6), int16le(b, 8)) : null;
            case JPEG -> jpegSize(b);
            case WEBP -> webpSize(b);
        };
        if (size == null || size.width() <= 0 || size.height() <= 0
                || size.width() > MAX_DIMENSION || size.height() > MAX_DIMENSION) {
            return null;
        }
        return size;
    }

    /** JPEG: マーカーを順にたどり、SOF(画像の大きさが書かれた部分)を探す */
    private static Size jpegSize(byte[] b) {
        int i = 2;
        while (i + 9 < b.length) {
            if ((b[i] & 0xFF) != 0xFF) {
                return null;
            }
            int marker = b[i + 1] & 0xFF;
            if (marker == 0xFF) {
                i++;
                continue;
            }
            int length = int16be(b, i + 2);
            boolean isSof = marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            if (isSof) {
                return new Size(int16be(b, i + 7), int16be(b, i + 5));
            }
            if (length < 2) {
                return null;
            }
            i += 2 + length;
        }
        return null;
    }

    /** WebP: 最初のチャンクの種類(VP8 / VP8L / VP8X)ごとに、大きさの書かれた場所が違う */
    private static Size webpSize(byte[] b) {
        if (b.length < 30) {
            return null;
        }
        if (ascii(b, 12, "VP8 ")) {
            return new Size(int16le(b, 26) & 0x3FFF, int16le(b, 28) & 0x3FFF);
        }
        if (ascii(b, 12, "VP8L")) {
            int bits = (b[21] & 0xFF) | (b[22] & 0xFF) << 8 | (b[23] & 0xFF) << 16 | (b[24] & 0xFF) << 24;
            return new Size((bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1);
        }
        if (ascii(b, 12, "VP8X")) {
            return new Size(int24le(b, 24) + 1, int24le(b, 27) + 1);
        }
        return null;
    }

    private static boolean startsWith(byte[] b, int offset, int... expected) {
        if (b.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((b[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean ascii(byte[] b, int offset, String text) {
        byte[] expected = text.getBytes(StandardCharsets.US_ASCII);
        return b.length >= offset + expected.length
                && Arrays.equals(b, offset, offset + expected.length, expected, 0, expected.length);
    }

    private static int int16be(byte[] b, int i) {
        return (b[i] & 0xFF) << 8 | (b[i + 1] & 0xFF);
    }

    private static int int16le(byte[] b, int i) {
        return (b[i] & 0xFF) | (b[i + 1] & 0xFF) << 8;
    }

    private static int int24le(byte[] b, int i) {
        return (b[i] & 0xFF) | (b[i + 1] & 0xFF) << 8 | (b[i + 2] & 0xFF) << 16;
    }

    private static int int32be(byte[] b, int i) {
        return (b[i] & 0xFF) << 24 | (b[i + 1] & 0xFF) << 16 | (b[i + 2] & 0xFF) << 8 | (b[i + 3] & 0xFF);
    }
}
