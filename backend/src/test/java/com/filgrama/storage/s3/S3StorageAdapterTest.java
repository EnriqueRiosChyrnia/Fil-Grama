package com.filgrama.storage.s3;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.filgrama.storage.StorageException;
import com.filgrama.storage.StorageProperties;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Unit del adapter S3 con el {@code S3Client} mockeado (sin MinIO). Cubre el caso que rompía el
 * reporte: cuando MinIO está caído/inalcanzable el SDK lanza {@link SdkClientException} (I/O), que
 * <b>no</b> es {@code S3Exception}. El adapter debe traducir <b>todo</b> error del SDK
 * ({@code SdkException} base) a {@link StorageException} — su contrato — para no filtrar la excepción
 * cruda al llamador (que terminaba en un 500 al armar el reporte).
 */
class S3StorageAdapterTest {

    private S3Client s3;
    private S3Presigner presigner;
    private S3StorageAdapter adapter;

    @BeforeEach
    void setUp() {
        s3 = mock(S3Client.class);
        presigner = mock(S3Presigner.class);
        StorageProperties props = new StorageProperties();
        props.setBucket("filgrama-media");
        adapter = new S3StorageAdapter(s3, presigner, props);
    }

    private static SdkClientException unreachable() {
        return SdkClientException.builder()
                .message("Unable to execute HTTP request: Connection refused")
                .build();
    }

    @Test
    void getWrapsSdkClientExceptionAsStorageException() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(unreachable());

        assertThatThrownBy(() -> adapter.get("demo/thumbs/x.jpg"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void putWrapsSdkClientExceptionAsStorageException() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(unreachable());

        assertThatThrownBy(() -> adapter.put("k", "x".getBytes(StandardCharsets.UTF_8), "text/plain"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void deleteWrapsSdkClientExceptionAsStorageException() {
        when(s3.deleteObject(any(DeleteObjectRequest.class))).thenThrow(unreachable());

        assertThatThrownBy(() -> adapter.delete("k"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void presignedUrlWrapsSdkClientExceptionAsStorageException() {
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenThrow(unreachable());

        assertThatThrownBy(() -> adapter.presignedUrl("k", Duration.ofMinutes(5)))
                .isInstanceOf(StorageException.class);
    }
}
