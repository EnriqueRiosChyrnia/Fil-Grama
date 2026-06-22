package com.filgrama.storage.s3;

import java.time.Duration;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.filgrama.storage.StorageException;
import com.filgrama.storage.StoragePort;
import com.filgrama.storage.StorageProperties;
import com.filgrama.storage.StoredObject;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Adapter S3-compatible (MinIO local / Cloudflare R2 prod). El {@code storage_path} que persiste
 * es la <b>key</b> del objeto (ej. {@code clients/{id}/posts/{id}/thumb-...jpg}); el bucket sale
 * de config, no de la ruta. Activo cuando {@code storage.backend=s3}.
 */
@Component
@ConditionalOnProperty(prefix = "storage", name = "backend", havingValue = "s3")
public class S3StorageAdapter implements StoragePort {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public S3StorageAdapter(S3Client s3, S3Presigner presigner, StorageProperties props) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = props.getBucket();
    }

    @Override
    public StoredObject put(String key, byte[] content, String contentType) {
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(content));
            return new StoredObject(key, content, contentType);
        } catch (S3Exception e) {
            throw new StorageException("S3 put falló para key '%s'".formatted(key), e);
        }
    }

    @Override
    public byte[] get(String storagePath) {
        try {
            ResponseBytes<GetObjectResponse> bytes = s3.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(storagePath).build());
            return bytes.asByteArray();
        } catch (S3Exception e) {
            throw new StorageException("S3 get falló para key '%s'".formatted(storagePath), e);
        }
    }

    @Override
    public void delete(String storagePath) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storagePath).build());
        } catch (S3Exception e) {
            throw new StorageException("S3 delete falló para key '%s'".formatted(storagePath), e);
        }
    }

    @Override
    public Optional<String> presignedUrl(String storagePath, Duration ttl) {
        try {
            GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(storagePath).build();
            GetObjectPresignRequest presign = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(get)
                    .build();
            return Optional.of(presigner.presignGetObject(presign).url().toString());
        } catch (S3Exception e) {
            throw new StorageException("S3 presign falló para key '%s'".formatted(storagePath), e);
        }
    }
}
