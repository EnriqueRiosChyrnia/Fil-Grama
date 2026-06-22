package com.filgrama.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * Los 401/403 del filtro de seguridad salen como {@code application/problem+json}
 * con el mismo formato (type/title/status/detail/instance) que el handler global.
 */
class SecurityProblemHandlersTest {

    @Test
    void entryPointWrites401ProblemJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAuthenticationEntryPoint()
                .commence(request, response, new BadCredentialsException("nope"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).contains("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"status\":401")
                .contains("\"title\":\"Unauthorized\"")
                .contains("\"instance\":\"/api/v1/auth/me\"");
    }

    @Test
    void accessDeniedHandlerWrites403ProblemJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/admin-only");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAccessDeniedHandler()
                .handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).contains("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"status\":403")
                .contains("\"title\":\"Forbidden\"");
    }
}
