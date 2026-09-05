package com.wrongquestion.backend.question.image.service;

import com.wrongquestion.backend.question.image.config.QuestionImageStorageProperties;
import com.wrongquestion.backend.question.image.exception.QuestionImageNotFoundException;
import com.wrongquestion.backend.question.image.exception.QuestionImageStorageException;
import com.wrongquestion.backend.question.image.exception.QuestionImageValidationException;
import com.wrongquestion.backend.question.image.model.QuestionImageContent;
import com.wrongquestion.backend.question.image.model.QuestionImageFormat;
import com.wrongquestion.backend.question.image.model.StoredQuestionImage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LocalQuestionImageStorage {

    public static final long MAX_IMAGE_SIZE_BYTES = 20L * 1024L * 1024L;

    private static final int HEADER_LENGTH = 12;
    private static final int COPY_BUFFER_SIZE = 8192;
    private static final String EMPTY_CODE = "QUESTION_IMAGE_EMPTY";
    private static final String TOO_LARGE_CODE = "QUESTION_IMAGE_TOO_LARGE";
    private static final String UNSUPPORTED_FORMAT_CODE =
            "QUESTION_IMAGE_UNSUPPORTED_FORMAT";
    private static final String INVALID_QUESTION_ID_CODE =
            "QUESTION_IMAGE_INVALID_QUESTION_ID";
    private static final Pattern STORED_PATH_PATTERN = Pattern.compile(
            "^questions/([1-9]\\d*)/"
                    + "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
                    + "[0-9a-f]{4}-[0-9a-f]{12})"
                    + "\\.(png|jpg|webp|gif)$"
    );

    private final Path configuredRoot;
    private final Supplier<UUID> uuidSupplier;

    @Autowired
    public LocalQuestionImageStorage(
            QuestionImageStorageProperties properties
    ) {
        this(properties, UUID::randomUUID);
    }

    LocalQuestionImageStorage(
            QuestionImageStorageProperties properties,
            Supplier<UUID> uuidSupplier
    ) {
        Objects.requireNonNull(properties, "properties must not be null");
        this.uuidSupplier = Objects.requireNonNull(
                uuidSupplier,
                "uuidSupplier must not be null"
        );
        this.configuredRoot = Path.of(
                properties.getQuestionImageDirectory()
        ).toAbsolutePath().normalize();
    }

    public StoredQuestionImage store(
            Long questionId,
            MultipartFile file
    ) {
        validateQuestionId(questionId);
        validateDeclaredFile(file);

        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(HEADER_LENGTH);
            QuestionImageFormat format = QuestionImageFormat.detect(header);
            if (format == null) {
                throw new QuestionImageValidationException(
                        UNSUPPORTED_FORMAT_CODE,
                        "仅支持 PNG、JPEG、WebP 或 GIF 图片"
                );
            }

            Path root = prepareRoot();
            Path questionDirectory = prepareQuestionDirectory(
                    root,
                    questionId
            );
            UUID generatedId = Objects.requireNonNull(
                    uuidSupplier.get(),
                    "uuidSupplier returned null"
            );
            String generatedName = generatedId
                    + "."
                    + format.getExtension();
            Path target = resolveWithinRoot(
                    root,
                    questionDirectory.resolve(generatedName)
            );
            Path temporary = Files.createTempFile(
                    questionDirectory,
                    ".upload-",
                    ".tmp"
            );

            try {
                long size = writeWithLimit(input, header, temporary);
                moveToTarget(temporary, target);
                return new StoredQuestionImage(
                        toRelativePath(root, target),
                        format.getContentType(),
                        size
                );
            }
            catch (QuestionImageValidationException exception) {
                cleanUpFailedWrite(temporary, questionDirectory);
                throw exception;
            }
            catch (IOException exception) {
                cleanUpFailedWrite(temporary, questionDirectory);
                throw storageFailure(exception);
            }
        }
        catch (QuestionImageValidationException exception) {
            throw exception;
        }
        catch (IOException exception) {
            throw storageFailure(exception);
        }
    }

    public QuestionImageContent load(String relativePath) {
        try {
            ResolvedStoredPath resolved = resolveStoredPath(relativePath);
            rejectSymbolicLinks(resolved.root(), resolved.path());
            if (!Files.isRegularFile(
                    resolved.path(),
                    LinkOption.NOFOLLOW_LINKS
            )) {
                throw new QuestionImageNotFoundException(
                        "题目图片不存在"
                );
            }
            Path realPath = resolved.path().toRealPath();
            if (!realPath.startsWith(resolved.root())) {
                throw invalidStoredPath();
            }

            QuestionImageFormat actualFormat;
            try (InputStream input = Files.newInputStream(realPath)) {
                actualFormat = QuestionImageFormat.detect(
                        input.readNBytes(HEADER_LENGTH)
                );
            }

            if (
                    actualFormat == null
                            || actualFormat != resolved.expectedFormat()
            ) {
                throw new QuestionImageStorageException(
                        "题目图片存储内容无效"
                );
            }

            long size = Files.size(realPath);
            if (size <= 0 || size > MAX_IMAGE_SIZE_BYTES) {
                throw new QuestionImageStorageException(
                        "题目图片存储内容无效"
                );
            }

            return new QuestionImageContent(
                    new FileSystemResource(realPath),
                    actualFormat.getContentType(),
                    size
            );
        }
        catch (
                QuestionImageNotFoundException
                        | QuestionImageStorageException exception
        ) {
            throw exception;
        }
        catch (IOException exception) {
            throw storageFailure(exception);
        }
    }

    public void delete(String relativePath) {
        try {
            ResolvedStoredPath resolved = resolveStoredPath(relativePath);
            if (!Files.exists(resolved.path(), LinkOption.NOFOLLOW_LINKS)) {
                return;
            }

            rejectSymbolicLinks(resolved.root(), resolved.path());
            if (!Files.isRegularFile(
                    resolved.path(),
                    LinkOption.NOFOLLOW_LINKS
            )) {
                throw invalidStoredPath();
            }
            Files.delete(resolved.path());
            deleteDirectoryIfEmpty(resolved.path().getParent());
        }
        catch (QuestionImageStorageException exception) {
            throw exception;
        }
        catch (IOException exception) {
            throw storageFailure(exception);
        }
    }

    private void validateQuestionId(Long questionId) {
        if (questionId == null || questionId <= 0) {
            throw new QuestionImageValidationException(
                    INVALID_QUESTION_ID_CODE,
                    "错题ID必须为正整数"
            );
        }
    }

    private void validateDeclaredFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new QuestionImageValidationException(
                    EMPTY_CODE,
                    "题目图片不能为空"
            );
        }
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw tooLarge();
        }
    }

    private Path prepareRoot() throws IOException {
        Files.createDirectories(configuredRoot);
        if (Files.isSymbolicLink(configuredRoot)) {
            throw invalidStoredPath();
        }
        return configuredRoot.toRealPath();
    }

    private Path prepareQuestionDirectory(Path root, Long questionId)
            throws IOException {
        Path questionsDirectory = createSecureDirectory(
                root,
                root,
                "questions"
        );
        return createSecureDirectory(
                root,
                questionsDirectory,
                questionId.toString()
        );
    }

    private Path createSecureDirectory(
            Path root,
            Path parent,
            String name
    )
            throws IOException {
        Path directory = parent.resolve(name).normalize();
        resolveWithinRoot(root, directory);

        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (
                    Files.isSymbolicLink(directory)
                            || !Files.isDirectory(
                                    directory,
                                    LinkOption.NOFOLLOW_LINKS
                    )
            ) {
                throw invalidStoredPath();
            }
        } else {
            Files.createDirectory(directory);
        }
        return directory;
    }

    private long writeWithLimit(
            InputStream input,
            byte[] header,
            Path temporary
    ) throws IOException {
        long total = header.length;

        try (OutputStream output = Files.newOutputStream(
                temporary,
                StandardOpenOption.TRUNCATE_EXISTING
        )) {
            output.write(header);
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (total + read > MAX_IMAGE_SIZE_BYTES) {
                    throw tooLarge();
                }
                output.write(buffer, 0, read);
                total += read;
            }
        }
        return total;
    }

    private void moveToTarget(Path temporary, Path target)
            throws IOException {
        try {
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE
            );
        }
        catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target);
        }
    }

    private ResolvedStoredPath resolveStoredPath(String relativePath)
            throws IOException {
        Matcher matcher = STORED_PATH_PATTERN.matcher(
                relativePath == null ? "" : relativePath
        );
        if (!matcher.matches()) {
            throw invalidStoredPath();
        }

        QuestionImageFormat expectedFormat =
                QuestionImageFormat.fromExtension(matcher.group(3));
        if (expectedFormat == null) {
            throw invalidStoredPath();
        }

        Path root = prepareRoot();
        String platformRelativePath = relativePath.replace(
                '/',
                java.io.File.separatorChar
        );
        Path resolved = resolveWithinRoot(
                root,
                root.resolve(platformRelativePath)
        );
        return new ResolvedStoredPath(root, resolved, expectedFormat);
    }

    private Path resolveWithinRoot(Path root, Path candidate) {
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw invalidStoredPath();
        }
        return normalized;
    }

    private void rejectSymbolicLinks(Path root, Path candidate)
            throws IOException {
        Path current = root;
        for (Path part : root.relativize(candidate)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw invalidStoredPath();
            }
        }
    }

    private String toRelativePath(Path root, Path target) {
        return root.relativize(target)
                .toString()
                .replace(java.io.File.separatorChar, '/');
    }

    private void cleanUpFailedWrite(
            Path temporary,
            Path questionDirectory
    ) {
        try {
            Files.deleteIfExists(temporary);
        }
        catch (IOException ignored) {
            // The original storage failure remains the primary error.
        }
        try {
            deleteDirectoryIfEmpty(questionDirectory);
        }
        catch (IOException ignored) {
            // The original storage failure remains the primary error.
        }
    }

    private void deleteDirectoryIfEmpty(Path directory) throws IOException {
        try {
            Files.deleteIfExists(directory);
        }
        catch (DirectoryNotEmptyException ignored) {
            // Another valid image may still exist during replacement cleanup.
        }
    }

    private QuestionImageValidationException tooLarge() {
        return new QuestionImageValidationException(
                TOO_LARGE_CODE,
                "题目图片不能超过20 MiB"
        );
    }

    private QuestionImageStorageException invalidStoredPath() {
        return new QuestionImageStorageException(
                "题目图片存储路径无效"
        );
    }

    private QuestionImageStorageException storageFailure(Throwable cause) {
        return new QuestionImageStorageException(
                "题目图片存储失败",
                cause
        );
    }

    private record ResolvedStoredPath(
            Path root,
            Path path,
            QuestionImageFormat expectedFormat
    ) {
    }
}
