package com.filgrama.client;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.filgrama.client.dto.ClientResponse;
import com.filgrama.domain.EmployeeClientPriority;
import com.filgrama.domain.EmployeeClientPriorityId;
import com.filgrama.error.ApiException;
import com.filgrama.repository.ClientRepository;
import com.filgrama.repository.EmployeeClientPriorityRepository;
import com.filgrama.repository.UserRepository;

/**
 * Marca/lista "clientes prioritarios" de un empleado. Es un flag INFORMATIVO:
 * no restringe acceso (todos los empleados ven todos los clientes).
 */
@Service
public class PriorityClientService {

    private final EmployeeClientPriorityRepository priorities;
    private final ClientRepository clients;
    private final UserRepository users;
    private final ClientMapper mapper;

    public PriorityClientService(EmployeeClientPriorityRepository priorities,
                                 ClientRepository clients,
                                 UserRepository users,
                                 ClientMapper mapper) {
        this.priorities = priorities;
        this.clients = clients;
        this.users = users;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> listForUser(Long userId) {
        requireUser(userId);
        List<Long> clientIds = priorities.findByUserId(userId).stream()
                .map(EmployeeClientPriority::getClientId)
                .toList();
        if (clientIds.isEmpty()) {
            return List.of();
        }
        return clients.findAllById(clientIds).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public void add(Long userId, Long clientId) {
        requireUser(userId);
        if (!clients.existsById(clientId)) {
            throw ApiException.notFound("Client %d not found".formatted(clientId));
        }
        EmployeeClientPriorityId pk = new EmployeeClientPriorityId(userId, clientId);
        if (priorities.existsById(pk)) {
            return; // idempotente: ya está marcado
        }
        EmployeeClientPriority e = new EmployeeClientPriority();
        e.setUserId(userId);
        e.setClientId(clientId);
        e.setCreatedAt(Instant.now()); // la entidad no auto-genera created_at
        priorities.save(e);
    }

    @Transactional
    public void remove(Long userId, Long clientId) {
        requireUser(userId);
        EmployeeClientPriorityId pk = new EmployeeClientPriorityId(userId, clientId);
        if (priorities.existsById(pk)) {
            priorities.deleteById(pk);
        }
    }

    private void requireUser(Long userId) {
        if (!users.existsById(userId)) {
            throw ApiException.notFound("User %d not found".formatted(userId));
        }
    }
}
