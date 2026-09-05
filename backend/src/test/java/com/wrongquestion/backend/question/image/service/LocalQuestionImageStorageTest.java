package com.wrongquestion.backend.question.image.service;

import com.wrongquestion.backend.question.image.config.QuestionImageStorageProperties;
import com.wrongquestion.backend.question.image.exception.QuestionImageNotFoundException;
import com.wrongquestion.backend.question.image.exception.QuestionImageStorageException;
import com.wrongquestion.backend.question.image.exception.QuestionImageValidationException;
import com.wrongquestion.backend.question.image.model.QuestionImageContent;
import com.wrongquestion.backend.question.image.model.StoredQuestionImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalQuestionImageStorageTest {

    private static final UUID FIXED_UUID = UUID.fromString(
            "123e4567-e89b-12d3-a456-426614174000"
    );
    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A,
            0x01, 0x02, 0x03, 0x04
    };
    private static final byte[] JPEG_BYTES = {
            (byte) 0xFF, (byte) 0xD8,
            (byte) 0xFF, (byte) 0xE0,
            0x01, 0x02
    };
    private static final byte[] WEBP_BYTES = {
            0x52, 0x49, 0x46, 0x46,
            0x04, 0x00, 0x00, 0x00,
            0x57, 0x45, 0x42, 0x50,
            0x01
    };
    private static final byte[] GIF_BYTES = {
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61,
            0x01, 0x02
    };

    @TempDir
    private Path temporaryDirectory;

    private LocalQuestionImageStorage storage;

    @BeforeEach
    void setUp() {
        QuestionImageStorageProperties properties =
                new QuestionImageStorageProperties();
        properties.setQuestionImageDirectory(
                temporaryDirectory.resolve("images").toString()
        );
        storage = new LocalQuestionImageStorage(
                properties,
                () -> FIXED_UUID
        );
    }

    @ParameterizedTest
    @MethodSource("supportedImages")
    void shouldStoreEverySupportedImageFormat(
            byte[] content,
            String expectedExtension,
            String expectedContentType
    ) {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "client-name.bin",
                "application/octet-stream",
                content
        );

        StoredQuestionImage stored = storage.store(42L, file);

        assertEquals(
                "questions/42/" + FIXED_UUID + "." + expectedExtension,
                stored.relativePath()
        );
        assertEquals(expectedContentType, stored.contentType());
        assertEquals(content.length, stored.size());
        assertTrue(Files.isRegularFile(resolve(stored.relativePath())));
    }

    @Test
    void shouldUseDetectedFormatAndIgnoreClientFilenameAndMime() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../unsafe.svg",
                "image/svg+xml",
                PNG_BYTES
        );

        StoredQuestionImage stored = storage.store(7L, file);

        assertEquals(
                "questions/7/" + FIXED_UUID + ".png",
                stored.relativePath()
        );
        assertEquals("image/png", stored.contentType());
        assertFalse(stored.relativePath().contains("unsafe"));
    }

    @Test
    void shouldRejectEmptyFile() {
        QuestionImageValidationException exception = assertThrows(
                QuestionImageValidationException.class,
                () -> storage.store(
                        1L,
                        new MockMultipartFile(
                                "file",
                                "empty.png",
                                "image/png",
                                new byte[0]
                        )
                )
        );

        assertEquals("QUESTION_IMAGE_EMPTY", exception.getCode());
    }

    @Test
    void shouldRejectUnsupportedSignature() {
        QuestionImageValidationException exception = assertThrows(
                QuestionImageValidationException.class,
                () -> storage.store(
                        1L,
                        new MockMultipartFile(
                                "file",
                                "fake.png",
                                "image/png",
                                "not-an-image".getBytes()
                        )
                )
        );

        assertEquals(
                "QUESTION_IMAGE_UNSUPPORTED_FORMAT",
                exception.getCode()
        );
    }

    @Test
    void shouldRejectSvgEvenWhenDeclaredAsImage() {
        QuestionImageValidationException exception = assertThrows(
                QuestionImageValidationException.class,
                () -> storage.store(
                        1L,
                        new MockMultipartFile(
                                "file",
                                "image.svg",
                                "image/svg+xml",
                                "<svg></svg>".getBytes()
                        )
                )
        );

        assertEquals(
                "QUESTION_IMAGE_UNSUPPORTED_FORMAT",
                exception.getCode()
        );
    }

    @Test
    void shouldRejectFileAboveTwentyMebibytes() {
        byte[] oversized = new byte[
                Math.toIntExact(
                        LocalQuestionImageStorage.MAX_IMAGE_SIZE_BYTES + 1
                )
        ];

        QuestionImageValidationException exception = assertThrows(
                QuestionImageValidationException.class,
                () -> storage.store(
                        1L,
                        new MockMultipartFile(
                                "file",
                                "large.png",
                                "image/png",
                                oversized
                        )
                )
        );

        assertEquals("QUESTION_IMAGE_TOO_LARGE", exception.getCode());
        assertFalse(Files.exists(temporaryDirectory.resolve("images")));
    }

    @Test
    void shouldEnforceStreamingLimitAndRemovePartialFile()
            throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1L);
        when(file.getInputStream()).thenReturn(
                new OversizedPngInputStream()
        );

        QuestionImageValidationException exception = assertThrows(
                QuestionImageValidationException.class,
                () -> storage.store(1L, file)
        );

        assertEquals("QUESTION_IMAGE_TOO_LARGE", exception.getCode());
        Path questionDirectory = temporaryDirectory.resolve("images")
                .resolve("questions")
                .resolve("1");
        assertFalse(Files.exists(questionDirectory));
    }

    @Test
    void shouldRejectInvalidQuestionIdBeforeWriting() {
        QuestionImageValidationException exception = assertThrows(
                QuestionImageValidationException.class,
                () -> storage.store(
                        0L,
                        multipart("image.png", "image/png", PNG_BYTES)
                )
        );

        assertEquals(
                "QUESTION_IMAGE_INVALID_QUESTION_ID",
                exception.getCode()
        );
        assertFalse(Files.exists(temporaryDirectory.resolve("images")));
    }

    @Test
    void shouldLoadStoredBytesAndMetadata() throws IOException {
        StoredQuestionImage stored = storage.store(
                12L,
                multipart("image.png", "image/png", PNG_BYTES)
        );

        QuestionImageContent loaded = storage.load(stored.relativePath());

        assertEquals("image/png", loaded.contentType());
        assertEquals(PNG_BYTES.length, loaded.size());
        try (var input = loaded.resource().getInputStream()) {
            assertArrayEquals(PNG_BYTES, input.readAllBytes());
        }
    }

    @Test
    void shouldRejectStoredFileWhoseContentDoesNotMatchExtension()
            throws IOException {
        StoredQuestionImage stored = storage.store(
                12L,
                multipart("image.png", "image/png", PNG_BYTES)
        );
        Files.write(resolve(stored.relativePath()), GIF_BYTES);

        assertThrows(
                QuestionImageStorageException.class,
                () -> storage.load(stored.relativePath())
        );
    }

    @Test
    void shouldRejectPathOutsideGeneratedPathShape() {
        assertThrows(
                QuestionImageStorageException.class,
                () -> storage.load("../outside.png")
        );
        assertThrows(
                QuestionImageStorageException.class,
                () -> storage.delete("questions/1/not-a-uuid.png")
        );
    }

    @Test
    void shouldReportMissingStoredImage() {
        String missing = "questions/1/"
                + FIXED_UUID
                + ".png";

        assertThrows(
                QuestionImageNotFoundException.class,
                () -> storage.load(missing)
        );
    }

    @Test
    void shouldDeleteStoredFileAndEmptyQuestionDirectory() {
        StoredQuestionImage stored = storage.store(
                19L,
                multipart("image.png", "image/png", PNG_BYTES)
        );
        Path storedPath = resolve(stored.relativePath());
        Path questionDirectory = storedPath.getParent();

        storage.delete(stored.relativePath());

        assertFalse(Files.exists(storedPath));
        assertFalse(Files.exists(questionDirectory));
    }

    @Test
    void shouldAllowDeletingAlreadyMissingGeneratedPath() {
        String missing = "questions/21/"
                + FIXED_UUID
                + ".gif";

        storage.delete(missing);

        assertFalse(Files.exists(resolve(missing)));
    }

    private Path resolve(String relativePath) {
        return temporaryDirectory.resolve("images")
                .resolve(relativePath.replace('/', java.io.File.separatorChar));
    }

    private static MockMultipartFile multipart(
            String filename,
            String contentType,
            byte[] content
    ) {
        return new MockMultipartFile(
                "file",
                filename,
                contentType,
                content
        );
    }

    private static Stream<Arguments> supportedImages() {
        return Stream.of(
                Arguments.of(PNG_BYTES, "png", "image/png"),
                Arguments.of(JPEG_BYTES, "jpg", "image/jpeg"),
                Arguments.of(WEBP_BYTES, "webp", "image/webp"),
                Arguments.of(GIF_BYTES, "gif", "image/gif")
        );
    }

    private static final class OversizedPngInputStream extends InputStream {

        private static final long CONTENT_SIZE =
                LocalQuestionImageStorage.MAX_IMAGE_SIZE_BYTES + 1;

        private long position;

        @Override
        public int read() {
            if (position >= CONTENT_SIZE) {
                return -1;
            }
            int value = position < PNG_BYTES.length
                    ? Byte.toUnsignedInt(PNG_BYTES[Math.toIntExact(position)])
                    : 0;
            position++;
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (position >= CONTENT_SIZE) {
                return -1;
            }

            int count = Math.toIntExact(Math.min(
                    length,
                    CONTENT_SIZE - position
            ));
            for (int index = 0; index < count; index++) {
                long contentIndex = position + index;
                buffer[offset + index] = contentIndex < PNG_BYTES.length
                        ? PNG_BYTES[Math.toIntExact(contentIndex)]
                        : 0;
            }
            position += count;
            return count;
        }
    }
}
