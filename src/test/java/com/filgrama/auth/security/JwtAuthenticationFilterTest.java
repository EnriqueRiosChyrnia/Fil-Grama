package com.filgrama.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.filgrama.auth.JwtProperties;
import com.filgrama.auth.JwtService;
import com.filgrama.domain.enums.Role;

class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-test-secret-test-secret-test-secret-0123456789-abc");
        props.setAccessTtl(Duration.ofMinutes(15));
        jwtService = new JwtService(props);
        filter = new JwtAuthenticationFilter(jwtService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validBearerTokenPopulatesAuthenticationWithRole() throws Exception {
        String token = jwtService.issueAccessToken(5L, Role.ADMIN);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(5L);
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void noHeaderLeavesContextUnauthenticated() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void invalidTokenLeavesContextUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer not-a-real-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
