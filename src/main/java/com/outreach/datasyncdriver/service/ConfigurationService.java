package com.outreach.datasyncdriver.service;

import com.outreach.datasyncdriver.dto.ConnectionConfigRequest;
import com.outreach.datasyncdriver.dto.SchemaMappingRequest;
import com.outreach.datasyncdriver.dto.SyncRuleRequest;
import com.outreach.datasyncdriver.entity.*;
import com.outreach.datasyncdriver.repository.ConnectionConfigRepository;
import com.outreach.datasyncdriver.repository.SchemaMappingRepository;
import com.outreach.datasyncdriver.repository.SyncRuleRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Central service for managing driver configurations: connections, schema mappings, and sync rules.
 * All read operations are cached in Caffeine; mutations evict the relevant cache.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ConfigurationService {

    private final ConnectionConfigRepository connectionRepo;
    private final SchemaMappingRepository mappingRepo;
    private final SyncRuleRepository syncRuleRepo;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;
    private final ObjectMapper objectMapper;

    // ======================== Connection Configs ========================

    @Cacheable(value = "connections", key = "'all'")
    @Transactional(readOnly = true)
    public List<ConnectionConfig> getAllConnections() {
        log.debug("Loading all connections from database");
        return connectionRepo.findAll();
    }

    @Cacheable(value = "connections", key = "#id")
    @Transactional(readOnly = true)
    public ConnectionConfig getConnectionById(Long id) {
        return connectionRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found with id: " + id));
    }

    @Cacheable(value = "connections", key = "'name-' + #name")
    @Transactional(readOnly = true)
    public ConnectionConfig getConnectionByName(String name) {
        return connectionRepo.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found with name: " + name));
    }

    @Cacheable(value = "connections", key = "'type-' + #systemType")
    @Transactional(readOnly = true)
    public List<ConnectionConfig> getConnectionsByType(SystemType systemType) {
        return connectionRepo.findBySystemType(systemType);
    }

    @CacheEvict(value = "connections", allEntries = true)
    public ConnectionConfig createConnection(ConnectionConfigRequest request) {
        if (connectionRepo.existsByName(request.getName())) {
            throw new IllegalArgumentException("Connection with name '" + request.getName() + "' already exists");
        }

        ConnectionConfig config = ConnectionConfig.builder()
                .name(request.getName())
                .description(request.getDescription())
                .systemType(request.getSystemType())
                .connectionType(request.getConnectionType())
                .baseUrl(request.getBaseUrl())
                .host(request.getHost())
                .port(request.getPort())
                .authUsername(request.getAuthUsername())
                .authPassword(request.getAuthPassword())
                .additionalProperties(serializeProperties(request.getAdditionalProperties()))
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        ConnectionConfig saved = connectionRepo.save(config);
        log.info("Created connection config: {} [{}]", saved.getName(), saved.getSystemType());
        cacheInvalidationPublisher.publishInvalidation("connections");
        return saved;
    }

    @CacheEvict(value = "connections", allEntries = true)
    public ConnectionConfig updateConnection(Long id, ConnectionConfigRequest request) {
        ConnectionConfig existing = connectionRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found with id: " + id));

        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        existing.setSystemType(request.getSystemType());
        existing.setConnectionType(request.getConnectionType());
        existing.setBaseUrl(request.getBaseUrl());
        existing.setHost(request.getHost());
        existing.setPort(request.getPort());
        existing.setAuthUsername(request.getAuthUsername());
        existing.setAuthPassword(request.getAuthPassword());
        existing.setAdditionalProperties(serializeProperties(request.getAdditionalProperties()));
        existing.setActive(request.getActive() != null ? request.getActive() : true);

        ConnectionConfig saved = connectionRepo.save(existing);
        log.info("Updated connection config: {} [{}]", saved.getName(), saved.getSystemType());
        cacheInvalidationPublisher.publishInvalidation("connections");
        return saved;
    }

    @CacheEvict(value = "connections", allEntries = true)
    public void deleteConnection(Long id) {
        ConnectionConfig config = connectionRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found with id: " + id));
        connectionRepo.delete(config);
        log.info("Deleted connection config: {}", config.getName());
        cacheInvalidationPublisher.publishInvalidation("connections");
    }

    // ======================== Schema Mappings ========================

    @Cacheable(value = "schemaMappings", key = "'all'")
    @Transactional(readOnly = true)
    public List<SchemaMapping> getAllMappings() {
        log.debug("Loading all schema mappings from database");
        return mappingRepo.findAll();
    }

    @Cacheable(value = "schemaMappings", key = "#id")
    @Transactional(readOnly = true)
    public SchemaMapping getMappingById(Long id) {
        return mappingRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schema mapping not found with id: " + id));
    }

    @Cacheable(value = "schemaMappings", key = "'group-' + #groupName")
    @Transactional(readOnly = true)
    public List<SchemaMapping> getMappingsByGroup(String groupName) {
        return mappingRepo.findByMappingGroupName(groupName);
    }

    @Cacheable(value = "schemaMappings", key = "'pair-' + #sourceSystem + '-' + #targetSystem")
    @Transactional(readOnly = true)
    public List<SchemaMapping> getMappingsBySystemPair(String sourceSystem, String targetSystem) {
        return mappingRepo.findBySourceSystemAndTargetSystem(sourceSystem, targetSystem);
    }

    @Cacheable(value = "schemaMappings", key = "'target-' + #targetSystem")
    @Transactional(readOnly = true)
    public List<SchemaMapping> getMappingsByTargetSystem(String targetSystem) {
        return mappingRepo.findByTargetSystem(targetSystem);
    }

    @CacheEvict(value = "schemaMappings", allEntries = true)
    public SchemaMapping createMapping(SchemaMappingRequest request) {
        SchemaMapping mapping = SchemaMapping.builder()
                .mappingGroupName(request.getMappingGroupName())
                .sourceSystem(request.getSourceSystem())
                .targetSystem(request.getTargetSystem())
                .sourceField(request.getSourceField())
                .targetField(request.getTargetField())
                .dataType(request.getDataType())
                .transformExpression(request.getTransformExpression())
                .defaultValue(request.getDefaultValue())
                .required(request.getRequired() != null ? request.getRequired() : false)
                .build();

        SchemaMapping saved = mappingRepo.save(mapping);
        log.info("Created schema mapping: {} → {} [group={}]",
                saved.getSourceField(), saved.getTargetField(), saved.getMappingGroupName());
        cacheInvalidationPublisher.publishInvalidation("schemaMappings");
        return saved;
    }

    @CacheEvict(value = "schemaMappings", allEntries = true)
    public List<SchemaMapping> createMappingsBatch(List<SchemaMappingRequest> requests) {
        List<SchemaMapping> mappings = requests.stream()
                .map(req -> SchemaMapping.builder()
                        .mappingGroupName(req.getMappingGroupName())
                        .sourceSystem(req.getSourceSystem())
                        .targetSystem(req.getTargetSystem())
                        .sourceField(req.getSourceField())
                        .targetField(req.getTargetField())
                        .dataType(req.getDataType())
                        .transformExpression(req.getTransformExpression())
                        .defaultValue(req.getDefaultValue())
                        .required(req.getRequired() != null ? req.getRequired() : false)
                        .build())
                .toList();

        List<SchemaMapping> saved = mappingRepo.saveAll(mappings);
        log.info("Created {} schema mappings in batch for group '{}'",
                saved.size(), requests.get(0).getMappingGroupName());
        cacheInvalidationPublisher.publishInvalidation("schemaMappings");
        return saved;
    }

    @CacheEvict(value = "schemaMappings", allEntries = true)
    public SchemaMapping updateMapping(Long id, SchemaMappingRequest request) {
        SchemaMapping existing = mappingRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schema mapping not found with id: " + id));

        existing.setMappingGroupName(request.getMappingGroupName());
        existing.setSourceSystem(request.getSourceSystem());
        existing.setTargetSystem(request.getTargetSystem());
        existing.setSourceField(request.getSourceField());
        existing.setTargetField(request.getTargetField());
        existing.setDataType(request.getDataType());
        existing.setTransformExpression(request.getTransformExpression());
        existing.setDefaultValue(request.getDefaultValue());
        existing.setRequired(request.getRequired() != null ? request.getRequired() : false);

        SchemaMapping saved = mappingRepo.save(existing);
        log.info("Updated schema mapping id={}: {} → {}", id, saved.getSourceField(), saved.getTargetField());
        cacheInvalidationPublisher.publishInvalidation("schemaMappings");
        return saved;
    }

    @CacheEvict(value = "schemaMappings", allEntries = true)
    public void deleteMapping(Long id) {
        SchemaMapping mapping = mappingRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schema mapping not found with id: " + id));
        mappingRepo.delete(mapping);
        log.info("Deleted schema mapping id={}", id);
        cacheInvalidationPublisher.publishInvalidation("schemaMappings");
    }

    @CacheEvict(value = "schemaMappings", allEntries = true)
    public void deleteMappingsByGroup(String groupName) {
        mappingRepo.deleteByMappingGroupName(groupName);
        log.info("Deleted all schema mappings in group '{}'", groupName);
        cacheInvalidationPublisher.publishInvalidation("schemaMappings");
    }

    // ======================== Sync Rules ========================

    @Cacheable(value = "syncRules", key = "'all'")
    @Transactional(readOnly = true)
    public List<SyncRule> getAllSyncRules() {
        log.debug("Loading all sync rules from database");
        return syncRuleRepo.findAll();
    }

    @Cacheable(value = "syncRules", key = "#id")
    @Transactional(readOnly = true)
    public SyncRule getSyncRuleById(Long id) {
        return syncRuleRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sync rule not found with id: " + id));
    }

    @Cacheable(value = "syncRules", key = "'connector-' + #connectorName")
    @Transactional(readOnly = true)
    public List<SyncRule> getSyncRulesByConnector(String connectorName) {
        return syncRuleRepo.findByConnectorName(connectorName);
    }

    @Cacheable(value = "syncRules", key = "'enabled-' + #connectorName")
    @Transactional(readOnly = true)
    public List<SyncRule> getEnabledSyncRules(String connectorName) {
        return syncRuleRepo.findByConnectorNameAndSyncEnabledTrue(connectorName);
    }

    @CacheEvict(value = "syncRules", allEntries = true)
    public SyncRule createSyncRule(SyncRuleRequest request) {
        SyncRule rule = SyncRule.builder()
                .ruleName(request.getRuleName())
                .connectorName(request.getConnectorName())
                .attributeName(request.getAttributeName())
                .syncEnabled(request.getSyncEnabled() != null ? request.getSyncEnabled() : true)
                .direction(request.getDirection())
                .filterExpression(request.getFilterExpression())
                .priority(request.getPriority() != null ? request.getPriority() : 100)
                .build();

        SyncRule saved = syncRuleRepo.save(rule);
        log.info("Created sync rule: {} for connector '{}' attribute '{}'",
                saved.getRuleName(), saved.getConnectorName(), saved.getAttributeName());
        cacheInvalidationPublisher.publishInvalidation("syncRules");
        return saved;
    }

    @CacheEvict(value = "syncRules", allEntries = true)
    public List<SyncRule> createSyncRulesBatch(List<SyncRuleRequest> requests) {
        List<SyncRule> rules = requests.stream()
                .map(req -> SyncRule.builder()
                        .ruleName(req.getRuleName())
                        .connectorName(req.getConnectorName())
                        .attributeName(req.getAttributeName())
                        .syncEnabled(req.getSyncEnabled() != null ? req.getSyncEnabled() : true)
                        .direction(req.getDirection())
                        .filterExpression(req.getFilterExpression())
                        .priority(req.getPriority() != null ? req.getPriority() : 100)
                        .build())
                .toList();

        List<SyncRule> saved = syncRuleRepo.saveAll(rules);
        log.info("Created {} sync rules in batch", saved.size());
        cacheInvalidationPublisher.publishInvalidation("syncRules");
        return saved;
    }

    @CacheEvict(value = "syncRules", allEntries = true)
    public SyncRule updateSyncRule(Long id, SyncRuleRequest request) {
        SyncRule existing = syncRuleRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sync rule not found with id: " + id));

        existing.setRuleName(request.getRuleName());
        existing.setConnectorName(request.getConnectorName());
        existing.setAttributeName(request.getAttributeName());
        existing.setSyncEnabled(request.getSyncEnabled() != null ? request.getSyncEnabled() : true);
        existing.setDirection(request.getDirection());
        existing.setFilterExpression(request.getFilterExpression());
        existing.setPriority(request.getPriority() != null ? request.getPriority() : 100);

        SyncRule saved = syncRuleRepo.save(existing);
        log.info("Updated sync rule id={}: {}", id, saved.getRuleName());
        cacheInvalidationPublisher.publishInvalidation("syncRules");
        return saved;
    }

    @CacheEvict(value = "syncRules", allEntries = true)
    public void deleteSyncRule(Long id) {
        SyncRule rule = syncRuleRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sync rule not found with id: " + id));
        syncRuleRepo.delete(rule);
        log.info("Deleted sync rule id={}", id);
        cacheInvalidationPublisher.publishInvalidation("syncRules");
    }

    @CacheEvict(value = "syncRules", allEntries = true)
    public void deleteSyncRulesByConnector(String connectorName) {
        syncRuleRepo.deleteByConnectorName(connectorName);
        log.info("Deleted all sync rules for connector '{}'", connectorName);
        cacheInvalidationPublisher.publishInvalidation("syncRules");
    }

    // ======================== Helpers ========================

    private String serializeProperties(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(properties);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to serialize additional properties", e);
        }
    }
}

