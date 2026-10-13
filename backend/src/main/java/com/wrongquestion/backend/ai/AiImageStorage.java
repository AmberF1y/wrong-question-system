package com.wrongquestion.backend.ai;

import com.wrongquestion.backend.question.image.config.QuestionImageStorageProperties;
import com.wrongquestion.backend.question.image.model.QuestionImageFormat;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class AiImageStorage {
    public static final int MAX_BYTES = 20 * 1024 * 1024;
    private final Path root;

    public record Input(
            byte[] bytes, String contentType, String extension, String sha256, String fileName) {}

    public AiImageStorage(QuestionImageStorageProperties properties) {
        root =
                Path.of(properties.getQuestionImageDirectory())
                        .toAbsolutePath()
                        .normalize()
                        .resolve("ai-tasks");
    }

    public Input validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new AiException("AI_IMAGE_EMPTY", "请选择题目图片", 400);
        if (file.getSize() > MAX_BYTES)
            throw new AiException("AI_IMAGE_TOO_LARGE", "题目图片不能超过20 MiB", 413);
        try (InputStream stream = file.getInputStream()) {
            byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES)
                throw new AiException("AI_IMAGE_TOO_LARGE", "题目图片不能超过20 MiB", 413);
            var format = QuestionImageFormat.detect(bytes);
            if (format == null)
                throw new AiException("AI_IMAGE_UNSUPPORTED", "仅支持 PNG、JPEG、WebP 或 GIF 图片", 400);
            String name = file.getOriginalFilename();
            name = name == null ? "题目图片" : name.replaceAll("[\\p{Cntrl}]", "");
            if (name.length() > 255) name = name.substring(0, 255);
            return new Input(
                    bytes,
                    format.getContentType(),
                    format.getExtension(),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                    name);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw storageError();
        }
    }

    public String newKey(Input input) {
        return UUID.randomUUID() + "." + input.extension();
    }

    public void store(String key, byte[] bytes) {
        try {
            assertParents(root);
            Files.createDirectories(root);
            assertParents(root);
            Files.write(path(key), bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw storageError();
        }
    }

    public byte[] read(String key) {
        try {
            Path path = path(key);
            assertParents(path);
            long size = Files.size(path);
            if (size <= 0 || size > MAX_BYTES) throw storageError();
            try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES || QuestionImageFormat.detect(bytes) == null)
                    throw storageError();
                return bytes;
            }
        } catch (IOException e) {
            throw storageError();
        }
    }

    public void delete(String key) {
        try {
            Path path = path(key);
            assertParents(path);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw storageError();
        }
    }

    private Path path(String key) {
        if (key == null || !key.matches("[0-9a-f-]{36}\\.(png|jpg|webp|gif)")) throw storageError();
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw storageError();
        return path;
    }

    private void assertParents(Path path) throws IOException {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw new IOException("symbolic link");
        }
    }

    private AiException storageError() {
        return new AiException("AI_IMAGE_STORAGE_ERROR", "识别图片读取或保存失败，请核对图片存储目录", 500);
    }
}
