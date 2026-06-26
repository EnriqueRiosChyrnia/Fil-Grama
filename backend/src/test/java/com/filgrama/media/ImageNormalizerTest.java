package com.filgrama.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * El normalizador deja la miniatura en un formato que el motor de PDF rasteriza. El caso crítico es
 * WebP (lo que sirve TikTok): ni la JDK ni PDFBox lo decodifican, así que debe transcodificarse a PNG.
 */
class ImageNormalizerTest {

    @Test
    void webpIsTranscodedToDecodablePng() throws Exception {
        byte[] webp = Files.readAllBytes(Path.of("src/test/resources/thumbnails/sample.webp"));

        Optional<ImageNormalizer.Image> out = ImageNormalizer.toPdfSafe(webp, "image/webp");

        assertThat(out).isPresent();
        assertThat(out.get().contentType()).isEqualTo("image/png");
        // PNG real y decodificable (lo que necesita el PDF).
        assertThat(ImageIO.read(new ByteArrayInputStream(out.get().bytes()))).isNotNull();
    }

    @Test
    void pngPassesThroughKeepingDeclaredContentType() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

        Optional<ImageNormalizer.Image> out = ImageNormalizer.toPdfSafe(png, "image/jpeg");

        assertThat(out).isPresent();
        // No se re-encodea un formato ya PDF-safe: mismos bytes, content-type declarado.
        assertThat(out.get().bytes()).isSameAs(png);
        assertThat(out.get().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void jpegMagicIsDetectedAsPdfSafe() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

        Optional<ImageNormalizer.Image> out = ImageNormalizer.toPdfSafe(jpeg, null);

        assertThat(out).isPresent();
        assertThat(out.get().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void nonImageBytesReturnEmpty() {
        byte[] junk = "MOCK_THUMB:https://cdn.mock/x.jpg".getBytes(StandardCharsets.UTF_8);

        assertThat(ImageNormalizer.toPdfSafe(junk, "image/jpeg")).isEmpty();
    }

    @Test
    void emptyOrNullReturnEmpty() {
        assertThat(ImageNormalizer.toPdfSafe(new byte[0], "image/png")).isEmpty();
        assertThat(ImageNormalizer.toPdfSafe(null, "image/png")).isEmpty();
    }
}
