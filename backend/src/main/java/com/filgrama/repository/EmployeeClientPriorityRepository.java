package com.filgrama.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.filgrama.domain.EmployeeClientPriority;
import com.filgrama.domain.EmployeeClientPriorityId;

public interface EmployeeClientPriorityRepository
        extends JpaRepository<EmployeeClientPriority, EmployeeClientPriorityId> {

    List<EmployeeClientPriority> findByUserId(Long userId);

    List<EmployeeClientPriority> findByClientId(Long clientId);
}
