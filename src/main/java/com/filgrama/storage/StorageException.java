package com.filgrama.storage;

/**
 * Error de IO/S3 del almacenamiento de objetos (subida/descarga/borrado falla, backend caído, etc.).
 * Propia del track Storage; el llamador la traduce a una {@code ApiException} controlada antes de
 * exponerla al cliente — nunca se filtra cruda en la respuesta HTTP.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
