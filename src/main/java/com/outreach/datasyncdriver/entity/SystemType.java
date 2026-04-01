package com.outreach.datasyncdriver.entity;

/**
 * Identifies whether a connection configuration refers to the
 * internal identity vault or an external target system.
 */
public enum SystemType {
    /** The internal system (e.g., DataSynchronizer / identity vault). */
    INTERNAL,
    /** An external target system (e.g., CSV, LDAP, Active Directory, HR system). */
    EXTERNAL
}

