package com.outreach.datasyncdriver.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Defines a field-level mapping between a source system schema and a target system schema.
 * <p>
 * Given the internal schema:
 * <pre>
 *   CREATE TABLE users (
 *     id UUID PRIMARY KEY,
 *     username TEXT,
 *     email TEXT,
 *     attributes JSONB
 *   );
 * </pre>
 * A mapping might map {@code username → name} or {@code attributes.department → dept_code}.
 * </p>
 */
@Entity
@Table(name = "schema_mappings",
       uniqueConstraints = @UniqueConstraint(
               columnNames = {"mappingGroupName", "sourceField", "targetField"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchemaMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Logical group name that ties related field mappings together,
     * e.g. "user-to-csv", "user-to-ldap".
     */
    @Column(nullable = false)
    private String mappingGroupName;

    /** Name of the source system connection (references ConnectionConfig.name). */
    @Column(nullable = false)
    private String sourceSystem;

    /** Name of the target system connection (references ConnectionConfig.name). */
    @Column(nullable = false)
    private String targetSystem;

    /**
     * Source field path. Supports dot-notation for nested/JSONB fields.
     * Examples: "id", "username", "email", "attributes.department"
     */
    @Column(nullable = false)
    private String sourceField;

    /**
     * Target field path in the external system.
     * Examples: "user_id", "name", "email_address", "dept_code"
     */
    @Column(nullable = false)
    private String targetField;

    /**
     * Data type of the field for validation/conversion.
     * Examples: "STRING", "UUID", "INTEGER", "BOOLEAN", "JSON"
     */
    private String dataType;

    /**
     * Optional transformation expression applied during mapping.
     * Examples: "UPPER", "LOWER", "TRIM", "CONCAT(firstName, ' ', lastName)"
     */
    private String transformExpression;

    /** Default value to use when the source field is null or missing. */
    private String defaultValue;

    /** Whether this field mapping is required (non-nullable). */
    @Builder.Default
    @Column(nullable = false)
    private boolean required = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

