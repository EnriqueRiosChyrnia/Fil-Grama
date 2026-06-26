package com.filgrama.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import javax.imageio.ImageIO;

/**
 * Normaliza miniaturas a un raster que el motor de PDF sabe rasterizar.
 *
 * <p><b>Por qué existe:</b> TikTok sirve las miniaturas en <b>WebP</b>, y ni la JDK ni
 * openhtmltopdf/PDFBox (que decodifican por {@link ImageIO}) traen un reader de WebP. Un
 * {@code data:image/webp;base64,...} embebido en el PDF termina como "sin miniatura" aunque el
 * navegador sí lo muestre (de ahí que el preview web funcione y el PDF no). Para que aparezca, hay
 * que dejar la imagen en un formato PDF-safe.
 *
 * <p><b>Qué hace:</b> si los bytes ya son PDF-safe (JPEG/PNG/GIF/BMP) los devuelve intactos; si no
 * (WebP u otro decodificable por ImageIO — el reader de WebP de TwelveMonkeys está en el classpath)
 * los decodifica y re-encodea a PNG. Si no son una imagen decodificable (p.ej. los bytes mock de
 * {@code local}/{@code test}) devuelve {@link Optional#empty()} — el llamador decide el fallback.
 */
public final class ImageNormalizer {

    private ImageNormalizer() {
    }

    /** Imagen lista para el PDF: bytes en un formato rasterizable + su content-type. */
    public record Image(byte[] bytes, String contentType) {
    }

    /**
     * Devuelve la imagen en un formato que el PDF rasteriza. Los formatos ya PDF-safe se conservan
     * (respetando {@code declaredContentType} si viene); el resto se transcodifica a PNG. {@code empty}
     * si los bytes están vacíos o no son una imagen decodificable.
     */
    public static Optional<Image> toPdfSafe(byte[] bytes, String declaredContentType) {
        if (bytes == null || bytes.length == 0) {
            return Optional.empty();
        }
        String pdfSafe = pdfSafeContentType(bytes);
        if (pdfSafe != null) {
            String contentType = declaredContentType == null || declaredContentType.isBlank()
                    ? pdfSafe : declaredContentType;
            return Optional.of(new Image(bytes, contentType));
        }
        // No PDF-safe (WebP, ...): decodificar y re-encodear a PNG (lossless, writer nativo de la JDK).
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) {
                return Optional.empty();
            }
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            if (!ImageIO.write(img, "png", os)) {
                return Optional.empty();
            }
            return Optional.of(new Image(os.toByteArray(), "image/png"));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Content-type si los bytes ya son un raster que ImageIO/PDFBox rasterizan nativamente; si no, null. */
    private static String pdfSafeContentType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8 && (b[2] & 0xff) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 4 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F') {
            return "image/gif";
        }
        if (b.length >= 2 && b[0] == 'B' && b[1] == 'M') {
            return "image/bmp";
        }
        return null;
    }
}
