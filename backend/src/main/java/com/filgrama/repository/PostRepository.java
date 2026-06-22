package com.filgrama.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.filgrama.domain.Post;

public interface PostRepository extends JpaRepository<Post, Long> {

    Optional<Post> findByAccountIdAndExternalPostId(Long accountId, String externalPostId);

    List<Post> findByAccountId(Long accountId);

    List<Post> findByClientId(Long clientId);
}
