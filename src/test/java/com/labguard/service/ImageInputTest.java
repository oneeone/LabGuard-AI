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
                    assertThat(ex.getMessage()).isEqualTo("仅支持 JPG、JPEG 或 PNG 图片。");
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
