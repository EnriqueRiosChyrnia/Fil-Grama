package com.filgrama.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import com.filgrama.client.dto.ClientDetailResponse;
import com.filgrama.client.dto.ClientResponse;
import com.filgrama.client.dto.CreateClientRequest;
import com.filgrama.client.dto.UpdateClientRequest;
import com.filgrama.client.web.PageResponse;
import com.filgrama.domain.Client;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.AccountStatus;
import com.filgrama.domain.enums.ClientStatus;
import com.filgrama.domain.enums.Platform;
import com.filgrama.error.ApiException;
import com.filgrama.repository.ClientRepository;
import com.filgrama.repository.SocialAccountRepository;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock ClientRepository clients;
    @Mock ClientQueryRepository clientQuery;
    @Mock SocialAccountRepository socialAccounts;

    ClientService service;

    @BeforeEach
    void setUp() {
        service = new ClientService(clients, clientQuery, socialAccounts, new ClientMapper());
    }

    @Test
    void createAppliesDefaultTimezoneAndActiveStatus() {
        when(clients.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        ClientResponse r = service.create(new CreateClientRequest("Acme", "nota", "Pro", null));

        assertThat(r.name()).isEqualTo("Acme");
        assertThat(r.timezone()).isEqualTo("America/Asuncion");
        assertThat(r.status()).isEqualTo(ClientStatus.ACTIVE);
    }

    @Test
    void createKeepsProvidedTimezone() {
        when(clients.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        ClientResponse r = service.create(new CreateClientRequest("Acme", null, null, "UTC"));

        assertThat(r.timezone()).isEqualTo("UTC");
    }

    @Test
    void archiveSetsStatusArchived() {
        Client c = new Client();
        c.setName("Acme");
        c.setStatus(ClientStatus.ACTIVE);
        when(clients.findById(7L)).thenReturn(Optional.of(c));
        when(clients.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        service.archive(7L);

        assertThat(c.getStatus()).isEqualTo(ClientStatus.ARCHIVED);
        verify(clients).save(c);
    }

    @Test
    void getDetailNotFoundThrows404() {
        when(clients.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(99L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getDetailIncludesAccountsSummary() {
        Client c = new Client();
        c.setName("Acme");
        c.setStatus(ClientStatus.ACTIVE);
        when(clients.findById(5L)).thenReturn(Optional.of(c));
        SocialAccount a = new SocialAccount();
        a.setPlatform(Platform.INSTAGRAM);
        a.setStatus(AccountStatus.CONNECTED);
        a.setHandle("@acme");
        when(socialAccounts.findByClientId(5L)).thenReturn(List.of(a));

        ClientDetailResponse r = service.getDetail(5L);

        assertThat(r.accountsSummary()).hasSize(1);
        assertThat(r.accountsSummary().get(0).platform()).isEqualTo(Platform.INSTAGRAM);
        assertThat(r.accountsSummary().get(0).status()).isEqualTo(AccountStatus.CONNECTED);
        assertThat(r.accountsSummary().get(0).handle()).isEqualTo("@acme");
    }

    @Test
    void updateRejectsBlankName() {
        Client c = new Client();
        c.setName("Acme");
        when(clients.findById(1L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.update(1L, new UpdateClientRequest("  ", null, null, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listFiltersAndPaginates() {
        Client c = new Client();
        c.setName("Acme");
        c.setStatus(ClientStatus.ACTIVE);
        Page<Client> page = new PageImpl<>(List.of(c), PageRequest.of(0, 20), 1);
        when(clientQuery.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<ClientResponse> res = service.list(ClientStatus.ACTIVE, "ac", PageRequest.of(0, 20));

        assertThat(res.totalElements()).isEqualTo(1);
        assertThat(res.totalPages()).isEqualTo(1);
        assertThat(res.page()).isZero();
        assertThat(res.size()).isEqualTo(20);
        assertThat(res.content()).hasSize(1);
        assertThat(res.content().get(0).name()).isEqualTo("Acme");
    }
}
