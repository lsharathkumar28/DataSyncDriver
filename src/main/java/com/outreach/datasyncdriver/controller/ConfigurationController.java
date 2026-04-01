package com.outreach.datasyncdriver.controller;

import com.outreach.datasyncdriver.dto.ConnectionConfigRequest;
import com.outreach.datasyncdriver.dto.SchemaMappingRequest;
import com.outreach.datasyncdriver.dto.SyncRuleRequest;
import com.outreach.datasyncdriver.entity.*;
import com.outreach.datasyncdriver.service.ConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for managing driver configurations: connection settings,
 * schema mappings, and sync rules — all persisted in central PostgreSQL
 * and cached in-memory via Caffeine.
 */
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
@Tag(name = "Configuration", description = "Manage connections, schema mappings, and sync rules")
public class ConfigurationController {

    private final ConfigurationService configService;

    // ======================== Connection Configs ========================

    @GetMapping("/connections")
    @Operation(summary = "List all connection configurations")
    public ResponseEntity<List<ConnectionConfig>> getAllConnections() {
        return ResponseEntity.ok(configService.getAllConnections());
    }

    @GetMapping("/connections/{id}")
    @Operation(summary = "Get a connection configuration by ID")
    public ResponseEntity<ConnectionConfig> getConnectionById(@PathVariable Long id) {
        return ResponseEntity.ok(configService.getConnectionById(id));
    }

    @GetMapping("/connections/name/{name}")
    @Operation(summary = "Get a connection configuration by name")
    public ResponseEntity<ConnectionConfig> getConnectionByName(@PathVariable String name) {
        return ResponseEntity.ok(configService.getConnectionByName(name));
    }

    @GetMapping("/connections/type/{systemType}")
    @Operation(summary = "List connections by system type",
               description = "Filter connections by INTERNAL or EXTERNAL")
    public ResponseEntity<List<ConnectionConfig>> getConnectionsByType(
            @PathVariable @Parameter(description = "INTERNAL or EXTERNAL") SystemType systemType) {
        return ResponseEntity.ok(configService.getConnectionsByType(systemType));
    }

    @PostMapping("/connections")
    @Operation(summary = "Create a new connection configuration",
               description = "Register a connection to an internal or external system")
    @ApiResponse(responseCode = "201", description = "Connection created")
    public ResponseEntity<ConnectionConfig> createConnection(
            @Valid @RequestBody ConnectionConfigRequest request) {
        ConnectionConfig created = configService.createConnection(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/connections/{id}")
    @Operation(summary = "Update an existing connection configuration")
    public ResponseEntity<ConnectionConfig> updateConnection(
            @PathVariable Long id,
            @Valid @RequestBody ConnectionConfigRequest request) {
        return ResponseEntity.ok(configService.updateConnection(id, request));
    }

    @DeleteMapping("/connections/{id}")
    @Operation(summary = "Delete a connection configuration")
    @ApiResponse(responseCode = "204", description = "Connection deleted")
    public ResponseEntity<Void> deleteConnection(@PathVariable Long id) {
        configService.deleteConnection(id);
        return ResponseEntity.noContent().build();
    }

    // ======================== Schema Mappings ========================

    @GetMapping("/mappings")
    @Operation(summary = "List all schema mappings")
    public ResponseEntity<List<SchemaMapping>> getAllMappings() {
        return ResponseEntity.ok(configService.getAllMappings());
    }

    @GetMapping("/mappings/{id}")
    @Operation(summary = "Get a schema mapping by ID")
    public ResponseEntity<SchemaMapping> getMappingById(@PathVariable Long id) {
        return ResponseEntity.ok(configService.getMappingById(id));
    }

    @GetMapping("/mappings/group/{groupName}")
    @Operation(summary = "List schema mappings by group name",
               description = "Returns all field-level mappings that belong to a mapping group, e.g. 'user-to-csv'")
    public ResponseEntity<List<SchemaMapping>> getMappingsByGroup(@PathVariable String groupName) {
        return ResponseEntity.ok(configService.getMappingsByGroup(groupName));
    }

    @GetMapping("/mappings/pair")
    @Operation(summary = "List schema mappings by source and target system pair")
    public ResponseEntity<List<SchemaMapping>> getMappingsBySystemPair(
            @RequestParam String sourceSystem,
            @RequestParam String targetSystem) {
        return ResponseEntity.ok(configService.getMappingsBySystemPair(sourceSystem, targetSystem));
    }

    @PostMapping("/mappings")
    @Operation(summary = "Create a single schema mapping")
    @ApiResponse(responseCode = "201", description = "Mapping created")
    public ResponseEntity<SchemaMapping> createMapping(
            @Valid @RequestBody SchemaMappingRequest request) {
        SchemaMapping created = configService.createMapping(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/mappings/batch")
    @Operation(summary = "Create multiple schema mappings in batch",
               description = "Useful for defining an entire mapping group at once")
    @ApiResponse(responseCode = "201", description = "Mappings created")
    public ResponseEntity<List<SchemaMapping>> createMappingsBatch(
            @RequestBody List<SchemaMappingRequest> requests) {
        List<SchemaMapping> created = configService.createMappingsBatch(requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/mappings/{id}")
    @Operation(summary = "Update an existing schema mapping")
    public ResponseEntity<SchemaMapping> updateMapping(
            @PathVariable Long id,
            @Valid @RequestBody SchemaMappingRequest request) {
        return ResponseEntity.ok(configService.updateMapping(id, request));
    }

    @DeleteMapping("/mappings/{id}")
    @Operation(summary = "Delete a schema mapping by ID")
    @ApiResponse(responseCode = "204", description = "Mapping deleted")
    public ResponseEntity<Void> deleteMapping(@PathVariable Long id) {
        configService.deleteMapping(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/mappings/group/{groupName}")
    @Operation(summary = "Delete all schema mappings in a group")
    @ApiResponse(responseCode = "204", description = "Mapping group deleted")
    public ResponseEntity<Void> deleteMappingsByGroup(@PathVariable String groupName) {
        configService.deleteMappingsByGroup(groupName);
        return ResponseEntity.noContent().build();
    }

    // ======================== Sync Rules ========================

    @GetMapping("/sync-rules")
    @Operation(summary = "List all sync rules")
    public ResponseEntity<List<SyncRule>> getAllSyncRules() {
        return ResponseEntity.ok(configService.getAllSyncRules());
    }

    @GetMapping("/sync-rules/{id}")
    @Operation(summary = "Get a sync rule by ID")
    public ResponseEntity<SyncRule> getSyncRuleById(@PathVariable Long id) {
        return ResponseEntity.ok(configService.getSyncRuleById(id));
    }

    @GetMapping("/sync-rules/connector/{connectorName}")
    @Operation(summary = "List sync rules by connector name",
               description = "Returns all rules for a specific connector, e.g. 'CSV File'")
    public ResponseEntity<List<SyncRule>> getSyncRulesByConnector(@PathVariable String connectorName) {
        return ResponseEntity.ok(configService.getSyncRulesByConnector(connectorName));
    }

    @GetMapping("/sync-rules/connector/{connectorName}/enabled")
    @Operation(summary = "List enabled sync rules for a connector",
               description = "Returns only rules where syncEnabled=true")
    public ResponseEntity<List<SyncRule>> getEnabledSyncRules(@PathVariable String connectorName) {
        return ResponseEntity.ok(configService.getEnabledSyncRules(connectorName));
    }

    @PostMapping("/sync-rules")
    @Operation(summary = "Create a single sync rule")
    @ApiResponse(responseCode = "201", description = "Sync rule created")
    public ResponseEntity<SyncRule> createSyncRule(
            @Valid @RequestBody SyncRuleRequest request) {
        SyncRule created = configService.createSyncRule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/sync-rules/batch")
    @Operation(summary = "Create multiple sync rules in batch",
               description = "Useful for defining all rules for a connector at once")
    @ApiResponse(responseCode = "201", description = "Sync rules created")
    public ResponseEntity<List<SyncRule>> createSyncRulesBatch(
            @RequestBody List<SyncRuleRequest> requests) {
        List<SyncRule> created = configService.createSyncRulesBatch(requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/sync-rules/{id}")
    @Operation(summary = "Update an existing sync rule")
    public ResponseEntity<SyncRule> updateSyncRule(
            @PathVariable Long id,
            @Valid @RequestBody SyncRuleRequest request) {
        return ResponseEntity.ok(configService.updateSyncRule(id, request));
    }

    @DeleteMapping("/sync-rules/{id}")
    @Operation(summary = "Delete a sync rule by ID")
    @ApiResponse(responseCode = "204", description = "Sync rule deleted")
    public ResponseEntity<Void> deleteSyncRule(@PathVariable Long id) {
        configService.deleteSyncRule(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sync-rules/connector/{connectorName}")
    @Operation(summary = "Delete all sync rules for a connector")
    @ApiResponse(responseCode = "204", description = "Connector rules deleted")
    public ResponseEntity<Void> deleteSyncRulesByConnector(@PathVariable String connectorName) {
        configService.deleteSyncRulesByConnector(connectorName);
        return ResponseEntity.noContent().build();
    }

    // ======================== Test Connectivity ========================

    @PostMapping("/connections/{id}/test")
    @Operation(summary = "Test a connection",
               description = "Validates that the configured connection is reachable")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable Long id) {
        ConnectionConfig config = configService.getConnectionById(id);
        // Placeholder — in Phase 2 this will attempt actual connectivity
        return ResponseEntity.ok(Map.of(
                "connectionName", config.getName(),
                "systemType", config.getSystemType(),
                "status", "CONNECTION_TEST_NOT_YET_IMPLEMENTED",
                "message", "Connection test will be available in Phase 2"
        ));
    }
}



