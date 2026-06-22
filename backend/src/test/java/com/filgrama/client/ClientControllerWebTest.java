package com.filgrama.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.filgrama.client.dto.ClientResponse;
import com.filgrama.client.web.PageResponse;
import com.filgrama.domain.enums.ClientStatus;
import com.filgrama.error.GlobalExceptionHandler;

/**
 * Slice HTTP del {@link ClientController} con {@code standaloneSetup}: códigos del
 * contrato, forma de la página y timezone por defecto.
 */
@ExtendWith(MockitoExtension.class)
class ClientControllerWebTest {

    @Mock ClientService service;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new ClientController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void listReturnsContractPageShape() throws Exception {
        ClientResponse c = new ClientResponse(1L, "Acme", "Pro", "America/Asuncion",
                ClientStatus.ACTIVE, null,
                Instant.parse("2026-06-22T00:00:00Z"), Instant.parse("2026-06-22T00:00:00Z"));
        when(service.list(eq(ClientStatus.ACTIVE), isNull(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(c), 0, 20, 1, 1));

        mvc.perform(get("/api/v1/clients").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Acme"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void createReturns201WithDefaultTimezone() throws Exception {
        ClientResponse c = new ClientResponse(1L, "Acme", null, "America/Asuncion",
                ClientStatus.ACTIVE, null,
                Instant.parse("2026-06-22T00:00:00Z"), Instant.parse("2026-06-22T00:00:00Z"));
        when(service.create(any())).thenReturn(c);

        mvc.perform(post("/api/v1/clients").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Acme\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.timezone").value("America/Asuncion"));
    }

    @Test
    void createBlankNameReturns400() throws Exception {
        mvc.perform(post("/api/v1/clients").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void archiveReturns204() throws Exception {
        mvc.perform(post("/api/v1/clients/1/archive"))
                .andExpect(status().isNoContent());
    }
}
