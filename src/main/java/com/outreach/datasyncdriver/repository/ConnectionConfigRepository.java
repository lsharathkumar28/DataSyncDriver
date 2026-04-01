package com.outreach.datasyncdriver.repository;

import com.outreach.datasyncdriver.entity.ConnectionConfig;
import com.outreach.datasyncdriver.entity.SystemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConnectionConfigRepository extends JpaRepository<ConnectionConfig, Long> {

    Optional<ConnectionConfig> findByName(String name);

    List<ConnectionConfig> findBySystemType(SystemType systemType);

    List<ConnectionConfig> findByActiveTrue();

    List<ConnectionConfig> findBySystemTypeAndActiveTrue(SystemType systemType);

    boolean existsByName(String name);
}

