package com.outreach.datasyncdriver.connector;

import com.outreach.datasyncdriver.dto.UserChangeEvent;
import com.outreach.datasyncdriver.dto.UserResponse;

import java.util.List;
import java.util.Map;

/**
 * Service Provider Interface for external-system connectors.
 * Implement this for each target: CSV, LDAP, Active Directory, etc.
 * <p>
 * Connectors can operate in two modes:
 * <ol>
 *   <li><b>Unmapped (legacy)</b> — receives typed DTOs via {@link #pushChange} / {@link #initialLoad}</li>
 *   <li><b>Mapped</b> — receives schema-mapped {@code Map<String, Object>} via
 *       {@link #pushMappedChange} / {@link #initialLoadMapped}</li>
 * </ol>
 * The driver will prefer mapped methods when schema mappings are configured
 * for the connector's {@link #targetSystemName()}.
 */
public interface ExternalSystemConnector {

    /** Human-readable name of the target system. */
    String name();

    /**
     * The target-system identifier used to look up {@code SchemaMapping} entries.
     * Must match the {@code targetSystem} column in the schema_mappings table.
     * <p>
     * Return {@code null} to indicate that this connector does not support
     * schema mapping (unmapped / legacy mode).
     */
    default String targetSystemName() {
        return null;
    }

    /**
     * Whether this connector supports schema-mapped data.
     * Returns true when {@link #targetSystemName()} is non-null.
     */
    default boolean supportsMappedData() {
        return targetSystemName() != null;
    }

    // ---- Unmapped (legacy) methods ----

    /** Push a single change (create / update / delete) using the raw DTO. */
    void pushChange(UserChangeEvent event);

    /** Bulk-load all users during initial synchronization using raw DTOs. */
    void initialLoad(List<UserResponse> users);

    // ---- Mapped methods ----

    /**
     * Push a single change using schema-mapped data.
     * Override this to handle mapped records in connectors that support schema mapping.
     *
     * @param changeType the type of change (CREATED, UPDATED, DELETED)
     * @param mappedData map of target-field-name → value, filtered by sync rules
     */
    default void pushMappedChange(UserChangeEvent.ChangeType changeType, Map<String, Object> mappedData) {
        throw new UnsupportedOperationException(
                "Connector '" + name() + "' does not implement pushMappedChange");
    }

    /**
     * Bulk-load all users using schema-mapped data.
     *
     * @param mappedRecords list of mapped records (each is target-field → value)
     * @param fieldNames    ordered list of target field names (for headers, ordering, etc.)
     */
    default void initialLoadMapped(List<Map<String, Object>> mappedRecords, List<String> fieldNames) {
        throw new UnsupportedOperationException(
                "Connector '" + name() + "' does not implement initialLoadMapped");
    }
}

