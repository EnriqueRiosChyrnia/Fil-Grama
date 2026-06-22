package com.filgrama.auth.web.dto;

public record LoginResponse(String accessToken, String refreshToken, UserDto user) {
}
