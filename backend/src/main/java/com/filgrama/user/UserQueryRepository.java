package com.filgrama.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.filgrama.domain.User;

/**
 * Repositorio PROPIO del track para la búsqueda paginada/filtrable de usuarios
 * ({@code role}, {@code active}, {@code q}). No se editan los repos compartidos.
 */
public interface UserQueryRepository
        extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
}
