package com.filgrama.storage;

import java.time.Duration;
import java.util.Optional;

/**
 * Puerto de almacenamiento de binarios, agnóstico del backend. Dos adapters lo implementan:
 * {@code S3StorageAdapter} (MinIO local / Cloudflare R2 prod) y {@code LocalStorageAdapter}
 * (carpeta de fallback offline). El binario nunca va a Postgres; en la base solo se guarda
 * la ruta ({@code storage_path}) + metadata.
 */
public interface StoragePort {

    /** Sube bytes y devuelve la ruta/clave persistible ({@code storage_path}). */
    StoredObject put(String key, byte[] content, String contentType);

    /** Descarga por ruta/clave. */
    byte[] get(String storagePath);

    /** Borra (para purga por retención). */
    void delete(String storagePath);

    /** URL temporal para servir la miniatura al front (presigned si el backend lo soporta). */
    Optional<String> presignedUrl(String storagePath, Duration ttl);
}
