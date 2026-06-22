package com.filgrama.storage.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.filgrama.storage.StorageException;
import com.filgrama.storage.StoragePort;
import com.filgrama.storage.StorageProperties;
import com.filgrama.storage.StoredObject;

/**
 * Adapter de carpeta local — fallback offline ({@code storage.backend=local}). Escribe/lee bajo
 * {@code storage.local.base-dir}, creando subcarpetas por la key. El {@code storage_path} es la
 * ruta relativa (la key). Sin presigned URL: {@link #presignedUrl} devuelve {@code Optional.empty()}
 * (en dev el front sirve la miniatura por un endpoint propio o se omite).
 */
@Component
@ConditionalOnProperty(prefix = "storage", name = "backend", havingValue = "local")
public class LocalStorageAdapter implements StoragePort {

    private final Path baseDir;

    public LocalStorageAdapter(StorageProperties props) {
        this.baseDir = Path.of(props.getLocal().getBaseDir()).toAbsolutePath().normalize();
    }

    @Override
    public StoredObject put(String key, byte[] content, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return new StoredObject(key, content, contentType);
        } catch (IOException e) {
            throw new StorageException("Escritura local falló para key '%s'".formatted(key), e);
        }
    }

    @Override
    public byte[] get(String storagePath) {
        try {
            return Files.readAllBytes(resolve(storagePath));
        } catch (NoSuchFileException e) {
            throw new StorageException("No existe el objeto local '%s'".formatted(storagePath), e);
        } catch (IOException e) {
            throw new StorageException("Lectura local falló para '%s'".formatted(storagePath), e);
        }
    }

    @Override
    public void delete(String storagePath) {
        try {
            Files.deleteIfExists(resolve(storagePath));
        } catch (IOException e) {
            throw new StorageException("Borrado local falló para '%s'".formatted(storagePath), e);
        }
    }

    @Override
    public Optional<String> presignedUrl(String storagePath, Duration ttl) {
        return Optional.empty();
    }

    /** Resuelve la key bajo {@code baseDir} y evita path traversal fuera de la carpeta raíz. */
    private Path resolve(String key) {
        Path resolved = baseDir.resolve(key).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new StorageException("Key fuera de base-dir: '%s'".formatted(key));
        }
        return resolved;
    }
}
