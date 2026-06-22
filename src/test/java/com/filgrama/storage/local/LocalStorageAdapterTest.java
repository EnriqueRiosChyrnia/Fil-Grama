package com.filgrama.storage.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.filgrama.storage.StorageException;
import com.filgrama.storage.StorageProperties;
import com.filgrama.storage.StoredObject;

class LocalStorageAdapterTest {

    @TempDir
    Path tmp;

    LocalStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        StorageProperties props = new StorageProperties();
        props.setBackend("local");
        props.getLocal().setBaseDir(tmp.toString());
        adapter = new LocalStorageAdapter(props);
    }

    @Test
    void put_writesFileUnderBaseDir_andReturnsRelativeKeyAsStoragePath() {
        byte[] content = "miniatura-local".getBytes(StandardCharsets.UTF_8);
        String key = "clients/7/posts/42/thumb-2026-06-22.jpg";

        StoredObject stored = adapter.put(key, content, "image/jpeg");

        assertThat(stored.storagePath()).isEqualTo(key);
        assertThat(stored.contentType()).isEqualTo("image/jpeg");
        assertThat(Files.exists(tmp.resolve(key))).isTrue();
    }

    @Test
    void get_returnsSameBytes() {
        byte[] content = "roundtrip".getBytes(StandardCharsets.UTF_8);
        String key = "a/b/c.bin";
        adapter.put(key, content, "application/octet-stream");

        assertThat(adapter.get(key)).isEqualTo(content);
    }

    @Test
    void presignedUrl_isEmpty_forLocalBackend() {
        assertThat(adapter.presignedUrl("a/b/c.bin", Duration.ofMinutes(5)))
                .isEqualTo(Optional.empty());
    }

    @Test
    void delete_removesFile_andSubsequentGetFails() {
        String key = "x/y.bin";
        adapter.put(key, "z".getBytes(StandardCharsets.UTF_8), "application/octet-stream");

        adapter.delete(key);

        assertThat(Files.exists(tmp.resolve(key))).isFalse();
        assertThatThrownBy(() -> adapter.get(key)).isInstanceOf(StorageException.class);
    }

    @Test
    void resolve_rejectsPathTraversalOutsideBaseDir() {
        assertThatThrownBy(() -> adapter.get("../escape.bin"))
                .isInstanceOf(StorageException.class);
    }
}
