package com.filgrama.storage;

/**
 * Resultado de subir un binario al storage. Lo que después se persiste en {@code media_assets}.
 *
 * @param storagePath ruta/clave persistible (la key S3 o la ruta relativa local); va a la columna
 *                    {@code media_assets.storage_path}. Nunca incluye el bucket.
 * @param bytes       contenido subido (mismos bytes que se enviaron).
 * @param contentType MIME, ej. {@code image/jpeg}.
 */
public record StoredObject(String storagePath, byte[] bytes, String contentType) {
}
