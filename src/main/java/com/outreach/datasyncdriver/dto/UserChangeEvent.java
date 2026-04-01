package com.outreach.datasyncdriver.dto;

import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Mirrors the event published by DataSynchronizer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserChangeEvent {

    public enum ChangeType { CREATED, UPDATED, DELETED }

    private UUID userId;
    private ChangeType changeType;
    private Instant timestamp;

    private String name;
    private String firstName;
    private String middleName;
    private String lastName;
    private String emailId;
    private String phoneNumber;
    private Map<String, Object> attributes;
}

