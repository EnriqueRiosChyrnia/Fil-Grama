package com.filgrama.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.filgrama.client.PriorityClientService;
import com.filgrama.domain.enums.Role;
import com.filgrama.error.ApiException;
import com.filgrama.error.GlobalExceptionHandler;
import com.filgrama.user.dto.UserResponse;

/**
 * Slice HTTP del {@link UserController} con {@code standaloneSetup} (sin contexto
 * Spring, sin DB, sin seguridad). Verifica códigos del contrato y serialización.
 */
@ExtendWith(MockitoExtension.class)
class UserControllerWebTest {

    @Mock UserService userService;
    @Mock PriorityClientService priorityClients;

    MockMvc mvc;

    private static final String VALID_BODY = """
            {"email":"a@b.com","fullName":"Ana","role":"EMPLEADO","password":"password123"}""";

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new UserController(userService, priorityClients))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createReturns201WithoutPasswordHash() throws Exception {
        UserResponse resp = new UserResponse(1L, "a@b.com", "Ana", Role.EMPLEADO, true,
                Instant.parse("2026-06-22T00:00:00Z"), Instant.parse("2026-06-22T00:00:00Z"));
        when(userService.create(any())).thenReturn(resp);

        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("a@b.com"))
                .andExpect(jsonPath("$.role").value("EMPLEADO"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void createDuplicateEmailReturns409() throws Exception {
        when(userService.create(any())).thenThrow(ApiException.conflict("Email already in use: a@b.com"));

        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void createInvalidPayloadReturns400() throws Exception {
        String bad = """
                {"email":"not-an-email","fullName":"","role":"EMPLEADO","password":"short"}""";

        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removePriorityClientReturns204() throws Exception {
        mvc.perform(delete("/api/v1/users/1/priority-clients/2"))
                .andExpect(status().isNoContent());
    }
}
