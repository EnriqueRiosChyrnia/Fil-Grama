package com.filgrama.user;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.filgrama.client.PriorityClientService;
import com.filgrama.client.dto.ClientResponse;
import com.filgrama.client.web.PageRequests;
import com.filgrama.client.web.PageResponse;
import com.filgrama.domain.enums.Role;
import com.filgrama.user.dto.CreateUserRequest;
import com.filgrama.user.dto.PriorityClientRequest;
import com.filgrama.user.dto.UpdateUserRequest;
import com.filgrama.user.dto.UserResponse;

import jakarta.validation.Valid;

/**
 * Gestión de usuarios — rutas {@code [ADMIN]}.
 *
 * <p>{@code @PreAuthorize} queda anotado aunque en esta rama {@code @EnableMethodSecurity}
 * todavía no esté activo (lo habilita el track A al mergear). Hasta entonces es inerte.
 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;
    private final PriorityClientService priorityClients;

    public UserController(UserService userService, PriorityClientService priorityClients) {
        this.userService = userService;
        this.priorityClients = priorityClients;
    }

    @GetMapping
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return userService.list(role, active, q, PageRequests.of(page, size, sort));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest req) {
        return userService.create(req);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PatchMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @RequestBody UpdateUserRequest req) {
        return userService.update(id, req);
    }

    @GetMapping("/{id}/priority-clients")
    public List<ClientResponse> priorityClients(@PathVariable Long id) {
        return priorityClients.listForUser(id);
    }

    @PostMapping("/{id}/priority-clients")
    @ResponseStatus(HttpStatus.CREATED)
    public void addPriorityClient(@PathVariable Long id,
                                  @Valid @RequestBody PriorityClientRequest req) {
        priorityClients.add(id, req.clientId());
    }

    @DeleteMapping("/{id}/priority-clients/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePriorityClient(@PathVariable Long id, @PathVariable Long clientId) {
        priorityClients.remove(id, clientId);
    }
}
