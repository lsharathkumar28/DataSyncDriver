package com.outreach.datasyncdriver.dto;

import com.outreach.datasyncdriver.entity.SyncDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request DTO for creating / updating a sync rule.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncRuleRequest {

    @NotBlank(message = "Rule name is required")
    private String ruleName;

    @NotBlank(message = "Connector name is required")
    private String connectorName;

    @NotBlank(message = "Attribute name is required")
    private String attributeName;

    @Builder.Default
    private Boolean syncEnabled = true;

    @NotNull(message = "Sync direction is required (INBOUND, OUTBOUND, BIDIRECTIONAL)")
    @Builder.Default
    private SyncDirection direction = SyncDirection.OUTBOUND;

    /** Optional filter expression */
    private String filterExpression;

    @Builder.Default
    private Integer priority = 100;
}


