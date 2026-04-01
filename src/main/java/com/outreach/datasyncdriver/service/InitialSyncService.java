package com.outreach.datasyncdriver.service;

import com.outreach.datasyncdriver.connector.ExternalSystemConnector;
import com.outreach.datasyncdriver.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Performs the initial full synchronization by calling the DataSynchronizer
 * REST API and loading all users into every registered connector.
 * <p>
 * When schema mappings are configured for a connector, the data is
 * transformed and filtered before delivery. Otherwise, raw DTOs are
 * passed through (legacy mode).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InitialSyncService {

    private final List<ExternalSystemConnector> connectors;
    private final SchemaMapper schemaMapper;
    private final SyncRuleFilter syncRuleFilter;

    @Value("${driver.datasynchronizer.base-url}")
    private String baseUrl;

    /**
     * Fetch all users from DataSynchronizer and push them into each connector.
     *
     * @return the number of users synchronized
     */
    public int runInitialSync() {
        log.info("Starting initial sync from {}", baseUrl);

        RestClient client = RestClient.create(baseUrl);

        List<UserResponse> users = client.get()
                .uri("/api/v1/users")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (users == null || users.isEmpty()) {
            log.warn("No users returned from DataSynchronizer");
            return 0;
        }

        for (ExternalSystemConnector connector : connectors) {
            try {
                String targetSystem = connector.targetSystemName();

                // If the connector declares a target system and mappings exist → mapped mode
                if (targetSystem != null && schemaMapper.hasMappings(targetSystem)) {
                    List<String> fieldNames = schemaMapper.getTargetFieldNames(targetSystem);

                    List<Map<String, Object>> mappedRecords = users.stream()
                            .map(user -> {
                                LinkedHashMap<String, Object> mapped =
                                        schemaMapper.mapFromUserResponse(user, targetSystem);
                                return (Map<String, Object>) syncRuleFilter.applyOutboundRules(
                                        mapped, connector.name());
                            })
                            .toList();

                    connector.initialLoadMapped(mappedRecords, fieldNames);
                    log.info("Mapped initial load complete for [{}] — {} users, {} fields",
                            connector.name(), mappedRecords.size(), fieldNames.size());
                } else {
                    // Legacy / unmapped mode
                    connector.initialLoad(users);
                    log.info("Legacy initial load complete for [{}]", connector.name());
                }
            } catch (Exception e) {
                log.error("Initial load failed for connector [{}]: {}",
                        connector.name(), e.getMessage(), e);
            }
        }

        return users.size();
    }
}

