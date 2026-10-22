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

    /**
     * 按页面保存的访问路径读回图片字节，供 AI 复查时把原图作为输入。
     * 只接受本服务生成的 /uploads/ 路径，并做目录穿越防护。
     */
    public byte[] load(String urlPath) {
        if (urlPath == null || !urlPath.startsWith(URL_PREFIX)) {
            throw new IllegalStateException("图片路径不合法，无法读取原图");
        }
        Path resolved = root.resolve(urlPath.substring(URL_PREFIX.length())).normalize();
        if (!resolved.startsWith(root) || !Files.isRegularFile(resolved)) {
            throw new IllegalStateException("图片文件不存在，无法读取原图");
        }
        try {
            return Files.readAllBytes(resolved);
        } catch (IOException ex) {
            throw new IllegalStateException("图片读取失败，请重试", ex);
        }
    }

    private static String extensionOf(String filename) {
        String name = filename == null ? "" : filename.trim();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "jpg" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
