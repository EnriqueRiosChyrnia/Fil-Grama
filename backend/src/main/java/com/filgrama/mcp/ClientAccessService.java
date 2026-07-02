package com.filgrama.mcp;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.filgrama.domain.Client;
import com.filgrama.domain.SocialAccount;
import com.filgrama.error.ApiException;
import com.filgrama.repository.ClientRepository;
import com.filgrama.repository.SocialAccountRepository;

/**
 * <b>Único choke point</b> de acceso de todas las tools MCP (spec/08 §Decisiones T5). Toda tool pasa el
 * cliente/cuenta pedido por acá antes de tocar datos — no se resuelve el scope en ningún otro lado.
 *
 * <p><b>Scope v1 (decisión spec/08, revierte una reinterpretación previa):</b> single-tenant, sin
 * modelo de acceso empleado→cliente. Un EMPLEADO ve **todos** los clientes, igual que un ADMIN — igual
 * que la app REST/UI (spec/02: {@code employee_client_priority} es un favorito informativo, nunca fue
 * ni es un permiso). No hay pedido "fuera de scope" en v1; sólo 404 si el cliente/cuenta no existe.
 *
 * <p><b>Punto de extensión para spec/11 (multi-tenant).</b> Cuando exista {@code organization_id}, sólo
 * se cambia la resolución acá adentro (p. ej. filtrar {@link #listAccessible} por organización y volver
 * a validar pertenencia en {@link #requireClient}/{@link #requireAccount}); las tools y el resto del
 * MCP no se tocan porque siempre pasan por este choke point.
 */
@Service
public class ClientAccessService {

    private final ClientRepository clients;
    private final SocialAccountRepository accounts;

    public ClientAccessService(ClientRepository clients, SocialAccountRepository accounts) {
        this.clients = clients;
        this.accounts = accounts;
    }

    /** Clientes que el usuario del token puede ver. v1: todos, para ADMIN y EMPLEADO por igual. */
    @Transactional(readOnly = true)
    public List<Client> listAccessible(McpIdentity identity) {
        return clients.findAll();
    }

    /** Valida que el cliente exista y lo devuelve. 404 si no existe. */
    @Transactional(readOnly = true)
    public Client requireClient(McpIdentity identity, Long clientId) {
        return clients.findById(clientId)
                .orElseThrow(() -> ApiException.notFound("El cliente %d no existe".formatted(clientId)));
    }

    /** Valida que la cuenta exista y la devuelve. 404 si no existe. */
    @Transactional(readOnly = true)
    public SocialAccount requireAccount(McpIdentity identity, Long accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("La cuenta %d no existe".formatted(accountId)));
    }
}
