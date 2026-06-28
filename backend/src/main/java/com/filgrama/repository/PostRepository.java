package com.filgrama.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.filgrama.domain.Post;

public interface PostRepository extends JpaRepository<Post, Long> {

    Optional<Post> findByAccountIdAndExternalPostId(Long accountId, String externalPostId);

    List<Post> findByAccountId(Long accountId);

    List<Post> findByClientId(Long clientId);

    /** Borrado real de los posts de una cuenta (compliance Meta: data-deletion). */
    @Modifying
    @Query("delete from Post p where p.accountId = ?1")
    void deleteByAccountId(Long accountId);
}
