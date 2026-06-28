package com.filgrama.oauth.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.filgrama.account.dto.SelectionResponse;
import com.filgrama.account.dto.SelectionResponse.CandidateView;
import com.filgrama.account.service.AccountService;

@ExtendWith(MockitoExtension.class)
class OAuthSelectionControllerTest {

    @Mock AccountService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new OAuthSelectionController(service)).build();
    }

    @Test
    void listReturnsCandidatesWithoutTokens() throws Exception {
        when(service.getSelection("tok-123")).thenReturn(new SelectionResponse("Cliente Uno",
                List.of(new CandidateView("IG-1", "@uno", "Uno", "instagram", "BUSINESS"))));

        mvc.perform(get("/api/v1/oauth/select/tok-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientName").value("Cliente Uno"))
                .andExpect(jsonPath("$.candidates[0].externalAccountId").value("IG-1"))
                .andExpect(jsonPath("$.candidates[0].handle").value("@uno"));
    }

    @Test
    void applyDelegatesChosenIds() throws Exception {
        when(service.applySelection(eq("tok-123"), any())).thenReturn(List.of());

        mvc.perform(post("/api/v1/oauth/select/tok-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalAccountIds\":[\"IG-1\",\"IG-2\"]}"))
                .andExpect(status().isOk());

        verify(service).applySelection(eq("tok-123"), eq(List.of("IG-1", "IG-2")));
    }
}
