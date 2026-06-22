package com.filgrama.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.filgrama.storage.local.LocalStorageAdapter;
import com.filgrama.storage.s3.S3ClientConfig;
import com.filgrama.storage.s3.S3StorageAdapter;

import software.amazon.awssdk.services.s3.S3Client;

/**
 * Verifica que el adapter activo lo selecciona {@code storage.backend} vía {@code @ConditionalOnProperty}.
 * No toca red: construir el {@code S3Client} no abre conexión.
 */
class StorageAdapterSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StorageTestConfig.class);

    @Test
    void s3Backend_activatesS3AdapterOnly() {
        runner.withPropertyValues(
                        "storage.backend=s3",
                        "storage.bucket=test-bucket",
                        "storage.s3.endpoint=http://localhost:9000",
                        "storage.s3.region=us-east-1",
                        "storage.s3.access-key=k",
                        "storage.s3.secret-key=s",
                        "storage.s3.path-style-access=true")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(S3StorageAdapter.class);
                    assertThat(ctx).hasSingleBean(S3Client.class);
                    assertThat(ctx).doesNotHaveBean(LocalStorageAdapter.class);
                });
    }

    @Test
    void localBackend_activatesLocalAdapterOnly() {
        runner.withPropertyValues(
                        "storage.backend=local",
                        "storage.local.base-dir=./target/test-media")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(LocalStorageAdapter.class);
                    assertThat(ctx).doesNotHaveBean(S3StorageAdapter.class);
                    assertThat(ctx).doesNotHaveBean(S3Client.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StorageProperties.class)
    @Import({S3ClientConfig.class, S3StorageAdapter.class, LocalStorageAdapter.class})
    static class StorageTestConfig {
    }
}
