package com.filgrama.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.filgrama.domain.RawApiPayload;

public interface RawApiPayloadRepository extends JpaRepository<RawApiPayload, Long> {

    List<RawApiPayload> findByAccountId(Long accountId);

    /** Borrado real de los payloads crudos de una cuenta (compliance Meta: data-deletion). */
    @Modifying
    @Query("delete from RawApiPayload r where r.accountId = ?1")
    void deleteByAccountId(Long accountId);
}
