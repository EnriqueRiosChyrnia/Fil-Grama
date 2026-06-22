package com.filgrama.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.filgrama.storage.StorageException;
import com.filgrama.storage.StorageProperties;
import com.filgrama.storage.StoredObject;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Test de integración del adapter S3 contra MinIO real (el de {@code docker-compose}).
 * Auto-omitido si MinIO no responde en {@code localhost:9000} → {@code mvn clean package} queda
 * verde offline; con {@code docker compose up -d minio minio-init} corre de verdad.
 *
 * <p>DoD track E: {@code put} → {@code get} devuelve los mismos bytes; {@code presignedUrl} retorna
 * URL no vacía; {@code delete} borra.
 */
@Tag("integration")
class S3StorageAdapterIT {

    private static final String ENDPOINT = "http://localhost:9000";
    private static final String BUCKET = "filgrama-media";

    private static S3Client s3;
    private static S3Presigner presigner;
    private static S3StorageAdapter adapter;
    private static boolean minioAvailable;

    @BeforeAll
    static void connect() {
        StorageProperties props = new StorageProperties();
        props.setBucket(BUCKET);
        props.getS3().setEndpoint(ENDPOINT);
        props.getS3().setRegion("us-east-1");
        props.getS3().setAccessKey("filgrama");
        props.getS3().setSecretKey("filgrama123");
        props.getS3().setPathStyleAccess(true);

        S3ClientConfig cfg = new S3ClientConfig();
        s3 = cfg.s3Client(props);
        presigner = cfg.s3Presigner(props);
        adapter = new S3StorageAdapter(s3, presigner, props);

        try {
            ensureBucket();
            minioAvailable = true;
        } catch (Exception e) {
            minioAvailable = false; // MinIO no levantado → se omiten los tests
        }
    }

    private static void ensureBucket() {
        try {
            s3.headBucket(b -> b.bucket(BUCKET));
        } catch (S3Exception e) {
            s3.createBucket(b -> b.bucket(BUCKET)); // bucket inexistente y MinIO accesible
        }
    }

    @AfterAll
    static void close() {
        if (s3 != null) {
            s3.close();
        }
        if (presigner != null) {
            presigner.close();
        }
    }

    @BeforeEach
    void requireMinio() {
        assumeTrue(minioAvailable, "MinIO no disponible en " + ENDPOINT + " — IT omitido");
    }

    @Test
    void put_then_get_returnsSameBytes_and_delete_removes() {
        String key = "it/roundtrip.bin";
        byte[] content = "hola-minio-roundtrip".getBytes(StandardCharsets.UTF_8);

        StoredObject stored = adapter.put(key, content, "application/octet-stream");
        assertThat(stored.storagePath()).isEqualTo(key); // storage_path = key, sin bucket

        assertThat(adapter.get(key)).isEqualTo(content);

        adapter.delete(key);
        assertThatThrownBy(() -> adapter.get(key)).isInstanceOf(StorageException.class);
    }

    @Test
    void presignedUrl_returnsNonEmptyHttpUrl() {
        String key = "it/presign.jpg";
        adapter.put(key, "img".getBytes(StandardCharsets.UTF_8), "image/jpeg");

        Optional<String> url = adapter.presignedUrl(key, Duration.ofMinutes(5));

        assertThat(url).isPresent();
        assertThat(url.get()).startsWith("http").contains(key);

        adapter.delete(key);
    }
}
