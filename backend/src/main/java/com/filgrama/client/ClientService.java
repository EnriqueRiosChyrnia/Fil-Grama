package com.filgrama.client;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.filgrama.client.dto.AccountSummary;
import com.filgrama.client.dto.ClientDetailResponse;
import com.filgrama.client.dto.ClientResponse;
import com.filgrama.client.dto.CreateClientRequest;
import com.filgrama.client.dto.UpdateClientRequest;
import com.filgrama.client.web.PageResponse;
import com.filgrama.domain.Client;
import com.filgrama.domain.enums.ClientStatus;
import com.filgrama.error.ApiException;
import com.filgrama.repository.ClientRepository;
import com.filgrama.repository.SocialAccountRepository;

@Service
public class ClientService {

    private static final String DEFAULT_TIMEZONE = "America/Asuncion";

    private final ClientRepository clients;
    private final ClientQueryRepository clientQuery;
    private final SocialAccountRepository socialAccounts;
    private final ClientMapper mapper;

    public ClientService(ClientRepository clients,
                         ClientQueryRepository clientQuery,
                         SocialAccountRepository socialAccounts,
                         ClientMapper mapper) {
        this.clients = clients;
        this.clientQuery = clientQuery;
        this.socialAccounts = socialAccounts;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> list(ClientStatus status, String q, Pageable pageable) {
        List<Specification<Client>> specs = new ArrayList<>();
        if (status != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            specs.add((root, query, cb) -> cb.like(cb.lower(root.get("name")), like));
        }
        // Spring Data JPA 4 ya no acepta una Specification null: allOf(empty) = unrestricted (sin filtro).
        Specification<Client> spec = Specification.allOf(specs);
        Page<ClientResponse> page = clientQuery.findAll(spec, pageable).map(mapper::toResponse);
        return PageResponse.of(page);
    }

    @Transactional
    public ClientResponse create(CreateClientRequest req) {
        Client c = new Client();
        c.setName(req.name());
        c.setNotes(req.notes());
        c.setPlan(req.plan());
        c.setTimezone(req.timezone() == null || req.timezone().isBlank()
                ? DEFAULT_TIMEZONE : req.timezone().trim());
        c.setStatus(ClientStatus.ACTIVE);
        return mapper.toResponse(clients.save(c));
    }

    @Transactional(readOnly = true)
    public ClientDetailResponse getDetail(Long id) {
        Client c = find(id);
        List<AccountSummary> accountsSummary = socialAccounts.findByClientId(id).stream()
                .map(a -> new AccountSummary(a.getPlatform(), a.getStatus(), a.getHandle()))
                .toList();
        return mapper.toDetail(c, accountsSummary);
    }

    @Transactional
    public ClientResponse update(Long id, UpdateClientRequest req) {
        Client c = find(id);
        if (req.name() != null) {
            if (req.name().isBlank()) {
                throw ApiException.badRequest("name must not be blank");
            }
            c.setName(req.name());
        }
        if (req.notes() != null) {
            c.setNotes(req.notes());
        }
        if (req.plan() != null) {
            c.setPlan(req.plan());
        }
        if (req.timezone() != null && !req.timezone().isBlank()) {
            c.setTimezone(req.timezone().trim());
        }
        return mapper.toResponse(clients.save(c));
    }

    @Transactional
    public void archive(Long id) {
        Client c = find(id);
        c.setStatus(ClientStatus.ARCHIVED);
        clients.save(c);
    }

    private Client find(Long id) {
        return clients.findById(id)
                .orElseThrow(() -> ApiException.notFound("Client %d not found".formatted(id)));
    }
}
