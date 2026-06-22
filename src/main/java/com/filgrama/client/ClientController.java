package com.filgrama.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.filgrama.client.dto.ClientDetailResponse;
import com.filgrama.client.dto.ClientResponse;
import com.filgrama.client.dto.CreateClientRequest;
import com.filgrama.client.dto.UpdateClientRequest;
import com.filgrama.client.web.PageRequests;
import com.filgrama.client.web.PageResponse;
import com.filgrama.domain.enums.ClientStatus;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    private final ClientService service;

    public ClientController(ClientService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ClientResponse> list(
            @RequestParam(required = false) ClientStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return service.list(status, q, PageRequests.of(page, size, sort));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponse create(@Valid @RequestBody CreateClientRequest req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    public ClientDetailResponse get(@PathVariable Long id) {
        return service.getDetail(id);
    }

    @PatchMapping("/{id}")
    public ClientResponse update(@PathVariable Long id, @RequestBody UpdateClientRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable Long id) {
        service.archive(id);
    }
}
