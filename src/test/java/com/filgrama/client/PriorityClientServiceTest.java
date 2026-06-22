package com.filgrama.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.filgrama.client.dto.ClientResponse;
import com.filgrama.domain.Client;
import com.filgrama.domain.EmployeeClientPriority;
import com.filgrama.domain.EmployeeClientPriorityId;
import com.filgrama.domain.enums.ClientStatus;
import com.filgrama.error.ApiException;
import com.filgrama.repository.ClientRepository;
import com.filgrama.repository.EmployeeClientPriorityRepository;
import com.filgrama.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PriorityClientServiceTest {

    @Mock EmployeeClientPriorityRepository priorities;
    @Mock ClientRepository clients;
    @Mock UserRepository users;

    PriorityClientService service;

    @BeforeEach
    void setUp() {
        service = new PriorityClientService(priorities, clients, users, new ClientMapper());
    }

    @Test
    void addCreatesWhenAbsentAndStampsCreatedAt() {
        when(users.existsById(1L)).thenReturn(true);
        when(clients.existsById(2L)).thenReturn(true);
        when(priorities.existsById(any(EmployeeClientPriorityId.class))).thenReturn(false);

        service.add(1L, 2L);

        ArgumentCaptor<EmployeeClientPriority> cap = ArgumentCaptor.forClass(EmployeeClientPriority.class);
        verify(priorities).save(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo(1L);
        assertThat(cap.getValue().getClientId()).isEqualTo(2L);
        assertThat(cap.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    void addIsIdempotentWhenAlreadyPresent() {
        when(users.existsById(1L)).thenReturn(true);
        when(clients.existsById(2L)).thenReturn(true);
        when(priorities.existsById(any(EmployeeClientPriorityId.class))).thenReturn(true);

        service.add(1L, 2L);

        verify(priorities, never()).save(any());
    }

    @Test
    void addUserNotFoundThrows() {
        when(users.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.add(1L, 2L)).isInstanceOf(ApiException.class);
        verify(priorities, never()).save(any());
    }

    @Test
    void addClientNotFoundThrows() {
        when(users.existsById(1L)).thenReturn(true);
        when(clients.existsById(2L)).thenReturn(false);

        assertThatThrownBy(() -> service.add(1L, 2L)).isInstanceOf(ApiException.class);
        verify(priorities, never()).save(any());
    }

    @Test
    void listForUserReturnsTheirClients() {
        when(users.existsById(1L)).thenReturn(true);
        EmployeeClientPriority e = new EmployeeClientPriority();
        e.setUserId(1L);
        e.setClientId(2L);
        when(priorities.findByUserId(1L)).thenReturn(List.of(e));
        Client c = new Client();
        c.setName("Acme");
        c.setStatus(ClientStatus.ACTIVE);
        when(clients.findAllById(List.of(2L))).thenReturn(List.of(c));

        List<ClientResponse> r = service.listForUser(1L);

        assertThat(r).hasSize(1);
        assertThat(r.get(0).name()).isEqualTo("Acme");
    }

    @Test
    void removeDeletesWhenPresent() {
        when(users.existsById(1L)).thenReturn(true);
        when(priorities.existsById(any(EmployeeClientPriorityId.class))).thenReturn(true);

        service.remove(1L, 2L);

        verify(priorities).deleteById(any(EmployeeClientPriorityId.class));
    }
}
