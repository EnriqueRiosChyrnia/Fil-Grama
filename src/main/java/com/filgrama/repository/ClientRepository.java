package com.filgrama.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.filgrama.domain.Client;
import com.filgrama.domain.enums.ClientStatus;

public interface ClientRepository extends JpaRepository<Client, Long> {

    List<Client> findByStatus(ClientStatus status);

    List<Client> findByNameContainingIgnoreCase(String name);
}
