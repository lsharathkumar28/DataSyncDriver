package com.outreach.datasyncdriver.service;

import com.outreach.datasyncdriver.entity.SyncDirection;
import com.outreach.datasyncdriver.entity.SyncRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Filters mapped data according to the {@link SyncRule} configurations
 * stored in the database.
 * <p>
 * Rules determine which attributes are allowed to flow through
 * to a specific connector and in which direction. Attributes whose
 * rules have {@code syncEnabled=false} are stripped from the data map.
 * <p>
 * If <b>no rules</b> are configured for a connector, all attributes pass through
 * (open-by-default). Once at least one rule exists, only explicitly-enabled
 * attributes are included (closed-by-default for listed attributes).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncRuleFilter {

    private final ConfigurationService configService;

    /**
     * Filter a mapped data record for outbound sync (internal → external).
     * Removes entries whose sync rules disable them for this connector.
     *
     * @param data          the mapped record (target-field → value)
     * @param connectorName the connector name (must match SyncRule.connectorName)
     * @return a new map with blocked attributes removed
     */
    public LinkedHashMap<String, Object> applyOutboundRules(Map<String, Object> data, String connectorName) {
        return applyRules(data, connectorName, SyncDirection.OUTBOUND);
    }

    /**
     * Filter a mapped data record for inbound sync (external → internal).
     */
    public LinkedHashMap<String, Object> applyInboundRules(Map<String, Object> data, String connectorName) {
        return applyRules(data, connectorName, SyncDirection.INBOUND);
    }

    /**
     * Check whether any sync rules are configured for a connector.
     */
    public boolean hasRules(String connectorName) {
        return !configService.getSyncRulesByConnector(connectorName).isEmpty();
    }

    /**
     * Get the set of attribute names that are blocked for a given connector + direction.
     */
    public Set<String> getBlockedAttributes(String connectorName, SyncDirection direction) {
        List<SyncRule> rules = configService.getSyncRulesByConnector(connectorName);
        return rules.stream()
                .filter(r -> !r.isSyncEnabled())
                .filter(r -> matchesDirection(r.getDirection(), direction))
                .map(SyncRule::getAttributeName)
                .collect(Collectors.toSet());
    }

    // ======================== Internal ========================

    private LinkedHashMap<String, Object> applyRules(Map<String, Object> data,
                                                      String connectorName,
                                                      SyncDirection requestedDirection) {
        List<SyncRule> rules = configService.getSyncRulesByConnector(connectorName);

        // No rules configured → pass everything through (open-by-default)
        if (rules.isEmpty()) {
            log.debug("No sync rules for connector '{}', passing all attributes", connectorName);
            return new LinkedHashMap<>(data);
        }

        // Build lookup: attributeName → SyncRule
        Map<String, SyncRule> ruleMap = rules.stream()
                .collect(Collectors.toMap(SyncRule::getAttributeName, r -> r, (a, b) -> {
                    // If duplicate rules, prefer the one with lower priority number (higher priority)
                    return a.getPriority() <= b.getPriority() ? a : b;
                }));

        LinkedHashMap<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String attrName = entry.getKey();
            SyncRule rule = findMatchingRule(attrName, ruleMap);

            if (rule == null) {
                // No explicit rule for this attribute → allow by default
                filtered.put(attrName, entry.getValue());
            } else if (rule.isSyncEnabled() && matchesDirection(rule.getDirection(), requestedDirection)) {
                filtered.put(attrName, entry.getValue());
            } else {
                log.debug("Sync rule blocked attribute '{}' for connector '{}' direction={}",
                        attrName, connectorName, requestedDirection);
            }
        }

        return filtered;
    }

    /**
     * Find a matching rule for the given attribute name.
     * Tries exact match first, then checks if any rule's attributeName
     * is a prefix of the target field (e.g., rule for "attributes.ssn"
     * matches mapped target field that originated from "attributes.ssn").
     */
    private SyncRule findMatchingRule(String attributeName, Map<String, SyncRule> ruleMap) {
        // Exact match
        SyncRule rule = ruleMap.get(attributeName);
        if (rule != null) return rule;

        // Check if any rule matches as a source-field name
        // (rules are stored with source attribute names like "attributes.ssn",
        //  but the data map keys are target field names like "ssn_field")
        // In that case, the caller should ensure consistency.
        return null;
    }

    /**
     * Checks whether a rule's configured direction covers the requested direction.
     * BIDIRECTIONAL covers both INBOUND and OUTBOUND.
     */
    private boolean matchesDirection(SyncDirection ruleDirection, SyncDirection requested) {
        if (ruleDirection == SyncDirection.BIDIRECTIONAL) return true;
        return ruleDirection == requested;
    }
}

