package com.outreach.datasyncdriver.entity;

/**
 * Direction in which a sync rule operates.
 */
public enum SyncDirection {
    /** Data flows from internal system to external system. */
    OUTBOUND,
    /** Data flows from external system to internal system. */
    INBOUND,
    /** Data flows in both directions. */
    BIDIRECTIONAL
}

