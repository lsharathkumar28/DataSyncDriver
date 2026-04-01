package com.outreach.datasyncdriver.dto;

import lombok.*;

import java.util.Map;
import java.util.UUID;

/**
 * DTO returned by the DataSynchronizer REST API.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private UUID userId;
    private String name;
    private String firstName;
    private String middleName;
    private String lastName;
    private String emailId;
    private String phoneNumber;
    private Map<String, Object> attributes;
}

