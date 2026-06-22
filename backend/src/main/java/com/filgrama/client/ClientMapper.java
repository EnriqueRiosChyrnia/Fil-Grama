package com.filgrama.client;

import java.util.List;

import org.springframework.stereotype.Component;

import com.filgrama.client.dto.AccountSummary;
import com.filgrama.client.dto.ClientDetailResponse;
import com.filgrama.client.dto.ClientResponse;
import com.filgrama.domain.Client;

@Component
public class ClientMapper {

    public ClientResponse toResponse(Client c) {
        return new ClientResponse(
                c.getId(), c.getName(), c.getPlan(), c.getTimezone(),
                c.getStatus(), c.getNotes(), c.getCreatedAt(), c.getUpdatedAt());
    }

    public ClientDetailResponse toDetail(Client c, List<AccountSummary> accountsSummary) {
        return new ClientDetailResponse(
                c.getId(), c.getName(), c.getPlan(), c.getTimezone(),
                c.getStatus(), c.getNotes(), c.getCreatedAt(), c.getUpdatedAt(),
                accountsSummary);
    }
}
