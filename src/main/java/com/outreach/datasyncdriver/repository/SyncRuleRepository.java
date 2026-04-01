package com.outreach.datasyncdriver.repository;

import com.outreach.datasyncdriver.entity.SyncDirection;
import com.outreach.datasyncdriver.entity.SyncRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SyncRuleRepository extends JpaRepository<SyncRule, Long> {

    List<SyncRule> findByConnectorName(String connectorName);

    List<SyncRule> findByConnectorNameAndSyncEnabledTrue(String connectorName);

    List<SyncRule> findByConnectorNameAndDirection(String connectorName, SyncDirection direction);

    Optional<SyncRule> findByConnectorNameAndAttributeName(String connectorName, String attributeName);

    void deleteByConnectorName(String connectorName);
}

