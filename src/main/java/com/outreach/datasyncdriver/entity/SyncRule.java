package com.outreach.datasyncdriver.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Controls which attributes are synchronised and in which direction.
 * <p>
 * Example: for a CSV connector you might enable sync for "username" and "email"
 * but disable sync for "attributes.ssn" to prevent sensitive data leakage.
 * </p>
 */
@Entity
@Table(name = "sync_rules",
       uniqueConstraints = @UniqueConstraint(
               columnNames = {"connectorName", "attributeName"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable rule name, e.g. "csv-sync-username". */
    @Column(nullable = false)
    private String ruleName;

    /** The connector this rule applies to (matches ExternalSystemConnector.name()). */
    @Column(nullable = false)
    private String connectorName;

    /**
     * The attribute this rule governs.
     * Supports dot-notation for nested fields: "attributes.department"
     */
    @Column(nullable = false)
    private String attributeName;

    /** Whether synchronisation of this attribute is enabled. */
    @Builder.Default
    @Column(nullable = false)
    private boolean syncEnabled = true;

    /** Direction of synchronisation. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SyncDirection direction = SyncDirection.OUTBOUND;

    /**
     * Optional filter expression. When present, the attribute is only synced
     * if this condition evaluates to true.
     * Example: "value != null && value.length() > 0"
     */
    private String filterExpression;

    /** Priority / ordering when multiple rules match (lower = higher priority). */
    @Builder.Default
    @Column(nullable = false)
    private int priority = 100;

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

