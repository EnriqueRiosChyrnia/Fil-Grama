package com.filgrama.client;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.filgrama.client.dto.ClientResponse;

/** {@code GET /api/v1/me/priority-clients} — prioritarios del empleado autenticado. */
@RestController
@RequestMapping("/api/v1/me/priority-clients")
public class MePriorityClientController {

    private final PriorityClientService priorityClients;
    private final CurrentUserProvider currentUser;

    public MePriorityClientController(PriorityClientService priorityClients,
                                      CurrentUserProvider currentUser) {
        this.priorityClients = priorityClients;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<ClientResponse> myPriorityClients() {
        return priorityClients.listForUser(currentUser.currentUserId());
    }
}
