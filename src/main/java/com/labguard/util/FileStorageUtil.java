package com.labguard.util;

import com.labguard.config.FileStorageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/**
 * 图片文件存储工具：负责上传文件的校验、保存与删除。
 * 文件名由 UUID 重新生成，存储路径与原始文件名隔离，防止路径穿越。
 */
@Component
public class FileStorageUtil {

    private static final Logger log = LoggerFactory.getLogger(FileStorageUtil.class);
    /** 上传文件对外的 URL 前缀（与 FileStorageConfig 中的资源映射一致） */
    public static final String UPLOAD_URL_PREFIX = "/uploads/";

    private final FileStorageConfig config;
    private final Path uploadRoot;

    public FileStorageUtil(FileStorageConfig config) {
        this.config = config;
        this.uploadRoot = Paths.get(config.getUploadDir()).toAbsolutePath().normalize();
    }

    /**
     * 保存上传的图片。
     *
     * @return 可访问的相对路径，如 {@code /uploads/xxx.png}
     * @throws IllegalArgumentException 文件为空、类型不允许或超出大小限制
     * @throws IllegalStateException    文件写入磁盘失败
     */
    public String saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String extension = extractExtension(file.getOriginalFilename());
        if (!config.getAllowedExtensions().contains(extension)) {
            throw new IllegalArgumentException("不支持的文件类型: ." + extension);
        }
        if (file.getSize() > config.getMaxFileSize()) {
            throw new IllegalArgumentException(
                    "文件大小超过限制，最大允许 " + config.getMaxFileSize() / 1024 / 1024 + "MB");
        }

        String filename = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(uploadRoot);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, uploadRoot.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("文件保存失败: " + filename, e);
        }
        return UPLOAD_URL_PREFIX + filename;
    }

    /**
     * 删除已存储的文件。
     *
     * @param storedPath {@link #saveImage} 返回的路径（或纯文件名）
     * @return 是否成功删除
     */
    public boolean delete(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return false;
        }
        try {
            // 只取文件名部分，禁止任何路径穿越
            String filename = Paths.get(storedPath).getFileName().toString();
            return Files.deleteIfExists(uploadRoot.resolve(filename));
        } catch (IOException e) {
            log.warn("删除文件失败: {}", storedPath, e);
            return false;
        }
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);
    }
}
