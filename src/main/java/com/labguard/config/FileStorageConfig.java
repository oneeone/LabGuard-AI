package com.labguard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文件上传存储配置。
 *
 * <p>1. 绑定 {@code labguard.storage.*} 配置项（上传目录、大小限制、允许的扩展名）；
 * 2. 将上传目录映射为静态资源，已上传文件可通过 {@code /uploads/**} 访问。</p>
 */
@ConfigurationProperties(prefix = "labguard.storage")
public class FileStorageConfig implements WebMvcConfigurer {

    /** 上传文件存储目录（相对路径基于工作目录解析） */
    private String uploadDir = "uploads";

    /** 单个文件大小上限（字节），默认 5MB */
    private long maxFileSize = 5 * 1024 * 1024;

    /** 允许上传的图片扩展名（小写、不含点） */
    private List<String> allowedExtensions = new ArrayList<>(
            Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }
}
