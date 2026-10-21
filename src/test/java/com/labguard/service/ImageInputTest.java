package com.labguard.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageInputTest {

    @Test
    @DisplayName("A-10 不支持的图片类型返回 UNSUPPORTED_IMAGE")
    void rejectsUnsupportedImageType() {
        assertThatThrownBy(() -> ImageInput.of(bytes(), "lab.webp"))
                .isInstanceOfSatisfying(AiVisionException.class, ex -> {
                    assertThat(ex.type()).isEqualTo(AiErrorType.UNSUPPORTED_IMAGE);
                    assertThat(ex.getMessage()).isEqualTo("仅支持 JPG、JPEG 或 PNG 图片，且单张大小不超过 10MB。");
                });
    }

    @Test
    @DisplayName("A-10 超出 10MB 大小限制在发送前返回 UNSUPPORTED_IMAGE")
    void rejectsOversizedImage() {
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        oversized[0] = 'x';

        assertThatThrownBy(() -> ImageInput.of(oversized, "lab.png"))
                .isInstanceOfSatisfying(AiVisionException.class, ex -> {
                    assertThat(ex.type()).isEqualTo(AiErrorType.UNSUPPORTED_IMAGE);
                    assertThat(ex.detail()).contains("图片大小超出限制");
                });
    }

    @Test
    @DisplayName("A-10 GIF、TXT 和无扩展名同样被拒绝")
    void rejectsOtherUnsupportedTypes() {
        for (String filename : new String[]{"a.gif", "a.txt", "labguard"}) {
            assertThatThrownBy(() -> ImageInput.of(bytes(), filename))
                    .isInstanceOfSatisfying(AiVisionException.class,
                            ex -> assertThat(ex.type()).isEqualTo(AiErrorType.UNSUPPORTED_IMAGE));
        }
    }

    @Test
    @DisplayName("JPG、PNG 能识别出媒体类型并保留文件名")
    void detectsMediaType() {
        ImageInput png = ImageInput.of(bytes(), "lab.PNG");
        ImageInput jpeg = ImageInput.of(bytes(), "lab.jpeg");

        assertThat(png.mediaType()).isEqualTo("image/png");
        assertThat(png.filename()).isEqualTo("lab.PNG");
        assertThat(jpeg.mediaType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("空图片被拒绝")
    void rejectsEmptyImage() {
        assertThatThrownBy(() -> ImageInput.of(new byte[0], "lab.png"))
                .isInstanceOfSatisfying(AiVisionException.class,
                        ex -> assertThat(ex.type()).isEqualTo(AiErrorType.REQUEST_FAILED));
    }

    private byte[] bytes() {
        return "fake-image".getBytes(StandardCharsets.UTF_8);
    }
}
