package com.labguard.service;

import java.util.Locale;
import java.util.Set;

/**
 * 视觉服务的图片输入。构造时即完成非空与格式校验，两种实现都不会绕过。
 * 首版只接受 JPG、JPEG、PNG（与任务卡和 README 的边界一致）。
 */
public record ImageInput(byte[] bytes, String filename, String mediaType) {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    public ImageInput {
        if (bytes == null || bytes.length == 0) {
            throw new AiVisionException(AiErrorType.REQUEST_FAILED, "图片内容为空");
        }
        String name = (filename == null || filename.isBlank()) ? "upload" : filename.trim();
        String extension = extensionOf(name);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new AiVisionException(AiErrorType.UNSUPPORTED_IMAGE, "不支持的文件扩展名：" + extension);
        }
        bytes = bytes.clone();
        filename = name;
        mediaType = "png".equals(extension) ? "image/png" : "image/jpeg";
    }

    public static ImageInput of(byte[] bytes, String filename) {
        return new ImageInput(bytes, filename, null);
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
