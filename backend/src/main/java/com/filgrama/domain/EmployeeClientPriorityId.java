package com.filgrama.domain;

import java.io.Serializable;
import java.util.Objects;

/** Clave compuesta de {@link EmployeeClientPriority}. */
public class EmployeeClientPriorityId implements Serializable {

    private Long userId;
    private Long clientId;

    public EmployeeClientPriorityId() {
    }

    public EmployeeClientPriorityId(Long userId, Long clientId) {
        this.userId = userId;
        this.clientId = clientId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmployeeClientPriorityId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(clientId, that.clientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, clientId);
    }
}
