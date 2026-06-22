package com.filgrama.auth.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.filgrama.auth.JwtService;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lee {@code Authorization: Bearer <token>}, valida el access token y arma el
 * {@code Authentication} (principal = userId, authority = {@code ROLE_<role>}).
 * Si el token falta o es inválido NO escribe error: deja seguir la cadena y que
 * el {@code AuthenticationEntryPoint} responda 401 cuando el endpoint lo requiera.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(PREFIX.length());
            try {
                JwtService.ParsedToken parsed = jwtService.parse(token);
                var authentication = new UsernamePasswordAuthenticationToken(
                        parsed.userId(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + parsed.role().name())));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                // Token inválido/expirado/malformado: no autenticamos. El entry point hará 401.
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
