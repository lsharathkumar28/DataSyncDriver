package com.outreach.datasyncdriver.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stores connection details for internal and external systems.
 * Configurations are persisted in the central PostgreSQL database.
 */
@Entity
@Table(name = "connection_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectionConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique human-readable name, e.g. "internal-idvault", "external-csv", "external-ldap". */
    @Column(nullable = false, unique = true)
    private String name;

    /** Description of this connection. */
    private String description;

    /** Whether this connects to the INTERNAL identity vault or an EXTERNAL target. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SystemType systemType;

    /** Protocol / connector type: REST, KAFKA, CSV, LDAP, DATABASE, etc. */
    @Column(nullable = false)
    private String connectionType;

    /** Base URL for REST-based connections. */
    private String baseUrl;

    /** Hostname for network-based connections. */
    private String host;

    /** Port number. */
    private Integer port;

    /** Authentication username. */
    private String authUsername;

    /** Authentication password / secret (stored encrypted in production). */
    private String authPassword;

    /**
     * Additional properties stored as a JSON string.
     * Examples: Kafka topic names, SSL settings, LDAP base DN, CSV file path, etc.
     */
    @Column(columnDefinition = "TEXT")
    private String additionalProperties;

    /** Whether this connection is currently active. */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

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

