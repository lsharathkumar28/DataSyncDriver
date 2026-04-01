package com.outreach.datasyncdriver.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Request DTO for creating / updating a single field-level schema mapping.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchemaMappingRequest {

    @NotBlank(message = "Mapping group name is required")
    private String mappingGroupName;

    @NotBlank(message = "Source system name is required")
    private String sourceSystem;

    @NotBlank(message = "Target system name is required")
    private String targetSystem;

    @NotBlank(message = "Source field is required")
    private String sourceField;

    @NotBlank(message = "Target field is required")
    private String targetField;

    /** Data type: STRING, UUID, INTEGER, BOOLEAN, JSON */
    private String dataType;

    /** Optional transformation: UPPER, LOWER, TRIM, custom expression */
    private String transformExpression;

    /** Default value when source is null */
    private String defaultValue;

    @Builder.Default
    private Boolean required = false;
}


