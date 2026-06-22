package com.filgrama.oauth.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.filgrama.account.service.AccountService;

@ExtendWith(MockitoExtension.class)
class OAuthCallbackControllerTest {

    @Mock AccountService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new OAuthCallbackController(service)).build();
    }

    @Test
    void callbackRedirectsToTargetUrl() throws Exception {
        when(service.completeCallback(eq("tiktok"), any(), any()))
                .thenReturn("https://front.app/oauth/result?accountId=10");

        mvc.perform(get("/api/v1/oauth/callback/tiktok").param("code", "c").param("state", "s"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://front.app/oauth/result?accountId=10"));
    }
}
