package com.outreach.datasyncdriver.dto;

import com.outreach.datasyncdriver.entity.SystemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Map;

/**
 * Request DTO for creating / updating a connection configuration.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectionConfigRequest {

    @NotBlank(message = "Connection name is required")
    private String name;

    private String description;

    @NotNull(message = "System type is required (INTERNAL or EXTERNAL)")
    private SystemType systemType;

    @NotBlank(message = "Connection type is required (REST, KAFKA, CSV, LDAP, DATABASE, etc.)")
    private String connectionType;

    private String baseUrl;

    private String host;

    private Integer port;

    private String authUsername;

    private String authPassword;

    /**
     * Free-form additional properties.
     * Examples: {"kafkaTopic": "user-events", "sslEnabled": true, "csvPath": "/data/output.csv"}
     */
    private Map<String, Object> additionalProperties;

    @Builder.Default
    private Boolean active = true;
}


