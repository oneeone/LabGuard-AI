package com.labguard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 把上传目录映射为 /uploads/** 静态资源，供分析结果页与工单详情页预览图片。
 * 目录由 labguard.storage.root 统一配置，与 {@code LocalFileStorage} 保持一致。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final String uploadsLocation;

    public WebMvcConfig(@Value("${labguard.storage.root:uploads}") String rootDirectory) {
        String location = Paths.get(rootDirectory).toAbsolutePath().normalize().toUri().toString();
        this.uploadsLocation = location.endsWith("/") ? location : location + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**").addResourceLocations(uploadsLocation);
    }
}
