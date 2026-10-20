package com.labguard.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

/**
 * 本地上传文件存储。目录与访问 URL 规则集中在这里，页面和数据库只保存 URL。
 * 实现遵循 .gitignore 约定：上传目录不进版本库。
 */
@Component
public class LocalFileStorage {

    private static final String URL_PREFIX = "/uploads/";

    private final Path root;

    public LocalFileStorage(@Value("${labguard.storage.root:uploads}") String rootDirectory) {
        this.root = Paths.get(rootDirectory).toAbsolutePath().normalize();
    }

    /** 保存图片并返回可直接用于 img src 的访问路径。 */
    public String store(byte[] bytes, String filename) {
        String name = UUID.randomUUID().toString().replace("-", "") + "." + extensionOf(filename);
        try {
            Files.createDirectories(root);
            Files.write(root.resolve(name), bytes);
        } catch (IOException ex) {
            throw new IllegalStateException("图片保存失败，请重试", ex);
        }
        return URL_PREFIX + name;
    }

    /** 供静态资源映射使用，指向同一个上传目录。 */
    public Path root() {
        return root;
    }

    private static String extensionOf(String filename) {
        String name = filename == null ? "" : filename.trim();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "jpg" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
