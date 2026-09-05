package com.wrongquestion.backend.question.image.model;

import java.util.Arrays;

public enum QuestionImageFormat {

    PNG("png", "image/png"),
    JPEG("jpg", "image/jpeg"),
    WEBP("webp", "image/webp"),
    GIF("gif", "image/gif");

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] JPEG_SIGNATURE = {
            (byte) 0xFF, (byte) 0xD8
    };
    private static final byte[] RIFF_SIGNATURE = {
            0x52, 0x49, 0x46, 0x46
    };
    private static final byte[] WEBP_SIGNATURE = {
            0x57, 0x45, 0x42, 0x50
    };
    private static final byte[] GIF87A_SIGNATURE = {
            0x47, 0x49, 0x46, 0x38, 0x37, 0x61
    };
    private static final byte[] GIF89A_SIGNATURE = {
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61
    };

    private final String extension;
    private final String contentType;

    QuestionImageFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String getExtension() {
        return extension;
    }

    public String getContentType() {
        return contentType;
    }

    public static QuestionImageFormat detect(byte[] header) {
        if (startsWith(header, PNG_SIGNATURE)) {
            return PNG;
        }
        if (startsWith(header, JPEG_SIGNATURE)) {
            return JPEG;
        }
        if (isWebp(header)) {
            return WEBP;
        }
        if (
                startsWith(header, GIF87A_SIGNATURE)
                        || startsWith(header, GIF89A_SIGNATURE)
        ) {
            return GIF;
        }
        return null;
    }

    public static QuestionImageFormat fromExtension(String extension) {
        return Arrays.stream(values())
                .filter(format -> format.extension.equals(extension))
                .findFirst()
                .orElse(null);
    }

    private static boolean isWebp(byte[] header) {
        return startsWith(header, RIFF_SIGNATURE)
                && matchesAt(header, 8, WEBP_SIGNATURE);
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        return matchesAt(value, 0, prefix);
    }

    private static boolean matchesAt(
            byte[] value,
            int offset,
            byte[] expected
    ) {
        if (value.length < offset + expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (value[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }
}
