package com.filgrama.mcp;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.filgrama.domain.Client;
import com.filgrama.domain.EmployeeClientPriority;
import com.filgrama.domain.SocialAccount;
import com.filgrama.error.ApiException;
import com.filgrama.repository.ClientRepository;
import com.filgrama.repository.EmployeeClientPriorityRepository;
import com.filgrama.repository.SocialAccountRepository;

/**
 * Aislamiento de scope de las tools MCP, enforced <b>en el backend</b> (spec/08 §Decisiones T5,
 * spec/11 §aislamiento). Regla: un <b>ADMIN</b> accede a todos los clientes; un <b>EMPLEADO</b> solo a
 * los que tiene asignados. Todo pedido a un cliente/cuenta fuera de scope corta con un error de negocio
 * claro (403) — <b>nunca</b> devuelve datos de otro cliente ni filtra por lo que diga el prompt.
 *
 * <p><b>Fuente de la asignación (decisión T5):</b> en v1 no existe todavía un modelo de acceso
 * empleado→cliente (spec/02: {@code employee_client_priority} nació como favorito informativo y en la
 * app REST/UI todos los empleados ven todos los clientes). Para cumplir la regla dura del track sin
 * inventar tablas, el MCP interpreta esa MISMA relación como el conjunto asignado del empleado. Es el
 * único vínculo por-empleado existente y deja el asiento para el {@code organization_id} futuro de
 * spec/11 (sumar el org como capa superior será aditivo). No cambia el comportamiento REST/UI.
 */
@Service
public class ClientAccessService {

    private final ClientRepository clients;
    private final SocialAccountRepository accounts;
    private final EmployeeClientPriorityRepository assignments;

    public ClientAccessService(ClientRepository clients,
                               SocialAccountRepository accounts,
                               EmployeeClientPriorityRepository assignments) {
        this.clients = clients;
        this.accounts = accounts;
        this.assignments = assignments;
    }

    /** Clientes que el usuario del token puede ver (admin = todos; empleado = asignados). */
    @Transactional(readOnly = true)
    public List<Client> listAccessible(McpIdentity identity) {
        if (identity.isAdmin()) {
            return clients.findAll();
        }
        Set<Long> ids = assignedClientIds(identity.userId());
        return ids.isEmpty() ? List.of() : clients.findAllById(ids);
    }

    /**
     * Valida que el usuario tenga acceso al cliente y lo devuelve. 404 si el cliente no existe; 403 si
     * existe pero está fuera del alcance del empleado (error de negocio claro, sin fugar datos).
     */
    @Transactional(readOnly = true)
    public Client requireClient(McpIdentity identity, Long clientId) {
        Client client = clients.findById(clientId)
                .orElseThrow(() -> ApiException.notFound("El cliente %d no existe".formatted(clientId)));
        if (!hasClient(identity, clientId)) {
            throw outOfScope("cliente", clientId);
        }
        return client;
    }

    /**
     * Valida que el usuario tenga acceso a la cuenta (por el cliente al que pertenece) y la devuelve.
     * 404 si la cuenta no existe; 403 si su cliente está fuera del alcance del empleado.
     */
    @Transactional(readOnly = true)
    public SocialAccount requireAccount(McpIdentity identity, Long accountId) {
        SocialAccount account = accounts.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("La cuenta %d no existe".formatted(accountId)));
        if (!hasClient(identity, account.getClientId())) {
            throw outOfScope("cuenta", accountId);
        }
        return account;
    }

    private boolean hasClient(McpIdentity identity, Long clientId) {
        return identity.isAdmin() || assignedClientIds(identity.userId()).contains(clientId);
    }

    private Set<Long> assignedClientIds(Long userId) {
        return assignments.findByUserId(userId).stream()
                .map(EmployeeClientPriority::getClientId)
                .collect(Collectors.toSet());
    }

    private static ApiException outOfScope(String entity, Long id) {
        return new ApiException(HttpStatus.FORBIDDEN, "Forbidden",
                "No tenés acceso a %s %d: está fuera de tu alcance asignado".formatted(entity, id));
    }
}
