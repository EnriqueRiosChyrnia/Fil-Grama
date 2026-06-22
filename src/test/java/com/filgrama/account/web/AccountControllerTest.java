package com.filgrama.account.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.filgrama.account.dto.ConnectResponse;
import com.filgrama.account.service.AccountService;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock AccountService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new AccountController(service)).build();
    }

    @Test
    void connectReturns200WithUrlAndState() throws Exception {
        when(service.connect(eq(1L), eq("tiktok"), any()))
                .thenReturn(new ConnectResponse("https://auth.url", "the-state"));

        mvc.perform(post("/api/v1/clients/1/accounts/connect/tiktok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationUrl").value("https://auth.url"))
                .andExpect(jsonPath("$.state").value("the-state"));
    }

    @Test
    void disconnectReturns204() throws Exception {
        mvc.perform(post("/api/v1/accounts/5/disconnect"))
                .andExpect(status().isNoContent());
        verify(service).disconnect(5L);
    }
}
