package com.filgrama.oauth;

/**
 * Perfil público de una cuenta social (best-effort) tal como lo expone la red: nombre visible,
 * {@code handle} (ya con "@") y avatar. Todos los campos son nullable: el provider llena lo que la
 * API devuelve y deja {@code null} lo que falte. Lo usan el canje y el refresh/sync para corregir el
 * nombre de la cuenta sin que el cliente tenga que reconectar.
 */
public record OAuthProfile(String handle, String displayName, String avatarUrl) {
}
