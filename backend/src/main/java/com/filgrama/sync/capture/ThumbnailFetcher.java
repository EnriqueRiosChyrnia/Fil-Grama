package com.filgrama.sync.capture;

import java.time.Duration;
import java.util.Optional;

/**
 * Descarga los bytes de una miniatura remota ({@code remote_thumbnail_url}) para cachearla en el
 * storage (TAREA F). Best-effort por contrato: ante cualquier fallo (red, timeout, no-2xx, tamaño
 * excesivo, contenido no-imagen) devuelve {@link Optional#empty()} en vez de lanzar — el sync no
 * debe romperse por una miniatura.
 *
 * <p>Como los providers (mock vs reales) van por {@code @Profile}, esta interfaz tiene su impl HTTP
 * real ({@code !local & !test}) y una mock determinista ({@code local}/{@code test}).
 */
public interface ThumbnailFetcher {

    /** Descarga la miniatura, o {@link Optional#empty()} si la URL es vacía o falla algo. */
    Optional<Fetched> fetch(String url);

    /**
     * Igual que {@link #fetch(String)} pero acotando el timeout de respuesta a {@code requestTimeout}.
     * Lo usa el render del reporte, que baja la miniatura de forma sincrónica: necesita un tope corto
     * (3-5s) para no demorar el PDF cuando la URL está vencida. Mismo contrato best-effort.
     */
    Optional<Fetched> fetch(String url, Duration requestTimeout);

    /** Bytes ya descargados + su content-type (ej. {@code image/jpeg}). */
    record Fetched(byte[] bytes, String contentType) {
    }
}
