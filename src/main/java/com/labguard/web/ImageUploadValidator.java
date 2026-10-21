package com.labguard.web;

import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

/**
 * 上传图片的统一校验：非空、仅 JPG/PNG、不超过 5MB。
 * 页面脚本只做即时提示，最终判定以这里为准。
 */
public final class ImageUploadValidator {

    public static final long MAX_SIZE = 5L * 1024 * 1024;
    public static final String EMPTY_MESSAGE = "请选择图片（仅支持 JPG / PNG，且大小不超过 5MB）";
    public static final String TYPE_MESSAGE = "仅支持 JPG 或 PNG 图片，请重新选择";
    public static final String SIZE_MESSAGE = "图片大小不能超过 5MB，请压缩后重新上传";

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");

    private ImageUploadValidator() {
    }

    /** @return 错误提示；校验通过时返回 null */
    public static String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return EMPTY_MESSAGE;
        }
        if (!extensionAllowed(file.getOriginalFilename()) || !contentTypeAllowed(file.getContentType())) {
            return TYPE_MESSAGE;
        }
        if (file.getSize() > MAX_SIZE) {
            return SIZE_MESSAGE;
        }
        return null;
    }

    private static boolean extensionAllowed(String filename) {
        String name = filename == null ? "" : filename.trim();
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return ALLOWED_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static boolean contentTypeAllowed(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        return ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT));
    }
}
