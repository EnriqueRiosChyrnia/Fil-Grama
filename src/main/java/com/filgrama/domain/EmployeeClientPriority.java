package com.filgrama.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Flag informativo "cliente prioritario" para un empleado. No restringe acceso. */
@Entity
@Table(name = "employee_client_priority")
@IdClass(EmployeeClientPriorityId.class)
@Getter
@Setter
@NoArgsConstructor
public class EmployeeClientPriority {

    @Id
    private Long userId;

    @Id
    private Long clientId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
