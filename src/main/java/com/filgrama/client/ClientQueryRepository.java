package com.filgrama.client;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.filgrama.domain.Client;

/**
 * Repositorio PROPIO del track (no se editan los repos compartidos de
 * {@code com.filgrama.repository}). Aporta consulta paginada + filtrable por
 * {@code Specification}, capacidad que {@code ClientRepository} no expone.
 */
public interface ClientQueryRepository
        extends JpaRepository<Client, Long>, JpaSpecificationExecutor<Client> {
}
