package com.filgrama.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Config de almacenamiento de objetos (prefijo {@code storage.*}). La cargó la central en
 * {@code application.yml}: defaults = MinIO local; en PROD se sobreescriben por env hacia
 * Cloudflare R2. Ambos backends son S3-compatible → mismo código. El backend {@code local}
 * es solo un fallback offline.
 *
 * <p>Dueño: track Storage/Media. Solo lee las claves; no las modifica en el yml.
 */
@Component
@ConfigurationProperties(prefix = "storage")
@Getter
@Setter
public class StorageProperties {

    /** Backend activo: {@code s3} (MinIO/R2) o {@code local} (carpeta de fallback). */
    private String backend = "s3";

    /** Bucket S3 donde viven las miniaturas (no se guarda en {@code storage_path}). */
    private String bucket = "filgrama-media";

    private S3 s3 = new S3();
    private Local local = new Local();

    @Getter
    @Setter
    public static class S3 {
        /** Endpoint S3-compatible (MinIO local / R2 prod). */
        private String endpoint = "http://localhost:9000";
        /** Región. R2 y MinIO la ignoran, pero el SDK la exige. */
        private String region = "us-east-1";
        private String accessKey = "filgrama";
        private String secretKey = "filgrama123";
        /** Necesario {@code true} para MinIO; R2 también lo acepta. */
        private boolean pathStyleAccess = true;
    }

    @Getter
    @Setter
    public static class Local {
        /** Carpeta raíz del backend de fallback ({@code backend=local}). */
        private String baseDir = "./var/media";
    }
}
