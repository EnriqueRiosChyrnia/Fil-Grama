package com.filgrama.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.filgrama.auth.security.AuthUserDetails;
import com.filgrama.auth.security.DbUserDetailsService;
import com.filgrama.auth.token.RefreshTokenService;
import com.filgrama.auth.web.dto.LoginResponse;
import com.filgrama.auth.web.dto.UserDto;
import com.filgrama.domain.User;
import com.filgrama.error.ApiException;
import com.filgrama.repository.UserRepository;

/**
 * Lógica de login y de la vista del usuario actual.
 * El rechazo de credenciales/usuario inactivo se expresa con
 * {@link InvalidCredentialsException} (= 401, lo formatea el controller).
 */
@Service
public class AuthService {

    private final DbUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;

    public AuthService(DbUserDetailsService userDetailsService, PasswordEncoder passwordEncoder,
            JwtService jwtService, RefreshTokenService refreshTokenService, UserRepository userRepository) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
    }

    /**
     * Autentica por email/password y emite el par de tokens.
     *
     * @throws InvalidCredentialsException si el usuario no existe, está inactivo o la contraseña no coincide.
     */
    @Transactional
    public LoginResponse login(String email, String rawPassword) {
        UserDetails details;
        try {
            details = userDetailsService.loadUserByUsername(email);
        } catch (UsernameNotFoundException e) {
            throw new InvalidCredentialsException();
        }
        if (!details.isEnabled()) {
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(rawPassword, details.getPassword())) {
            throw new InvalidCredentialsException();
        }

        User user = ((AuthUserDetails) details).getUser();
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getRole());
        String refreshToken = refreshTokenService.issueNew(user.getId());
        return new LoginResponse(accessToken, refreshToken, UserDto.from(user));
    }

    /** Usuario actual (a partir del userId del access token). */
    @Transactional(readOnly = true)
    public UserDto me(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User %d not found".formatted(userId)));
        return UserDto.from(user);
    }
}
