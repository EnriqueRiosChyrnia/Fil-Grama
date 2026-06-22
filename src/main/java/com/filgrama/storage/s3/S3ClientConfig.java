package com.filgrama.storage.s3;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.filgrama.storage.StorageProperties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Beans del cliente S3 síncrono (AWS SDK v2) y del presigner. Activos solo cuando
 * {@code storage.backend=s3}. Configurados con endpoint override + path-style para hablar con
 * MinIO local y Cloudflare R2 con el mismo código. Spring cierra ambos (son {@code AutoCloseable})
 * al apagar el contexto.
 */
@Configuration
@ConditionalOnProperty(prefix = "storage", name = "backend", havingValue = "s3")
public class S3ClientConfig {

    @Bean
    public S3Client s3Client(StorageProperties props) {
        StorageProperties.S3 s3 = props.getS3();
        return S3Client.builder()
                .endpointOverride(URI.create(s3.getEndpoint()))
                .region(Region.of(s3.getRegion()))
                .credentialsProvider(credentials(s3))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3.isPathStyleAccess())
                        .build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(StorageProperties props) {
        StorageProperties.S3 s3 = props.getS3();
        return S3Presigner.builder()
                .endpointOverride(URI.create(s3.getEndpoint()))
                .region(Region.of(s3.getRegion()))
                .credentialsProvider(credentials(s3))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3.isPathStyleAccess())
                        .build())
                .build();
    }

    private static StaticCredentialsProvider credentials(StorageProperties.S3 s3) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey()));
    }
}
