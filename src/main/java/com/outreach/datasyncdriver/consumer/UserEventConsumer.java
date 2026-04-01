package com.outreach.datasyncdriver.consumer;

import tools.jackson.databind.ObjectMapper;
import com.outreach.datasyncdriver.connector.ExternalSystemConnector;
import com.outreach.datasyncdriver.dto.UserChangeEvent;
import com.outreach.datasyncdriver.service.SchemaMapper;
import com.outreach.datasyncdriver.service.SyncRuleFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Listens to the {@code user-events} Kafka topic and delegates
 * each change to every registered {@link ExternalSystemConnector}.
 * <p>
 * When schema mappings are configured for a connector's target system,
 * the event is transformed and filtered before delivery. Otherwise,
 * the raw DTO is passed through (legacy mode).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {

    private final List<ExternalSystemConnector> connectors;
    private final SchemaMapper schemaMapper;
    private final SyncRuleFilter syncRuleFilter;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "user-events", groupId = "datasync-driver")
    public void onUserEvent(String message) {
        UserChangeEvent event;
        try {
            event = objectMapper.readValue(message, UserChangeEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialize user event: {}", e.getMessage(), e);
            return;
        }

        log.info("Received {} event for userId={}", event.getChangeType(), event.getUserId());

        for (ExternalSystemConnector connector : connectors) {
            try {
                String targetSystem = connector.targetSystemName();

                // If the connector declares a target system and mappings exist → mapped mode
                if (targetSystem != null && schemaMapper.hasMappings(targetSystem)) {
                    LinkedHashMap<String, Object> mapped = schemaMapper.mapFromChangeEvent(event, targetSystem);
                    Map<String, Object> filtered = syncRuleFilter.applyOutboundRules(mapped, connector.name());

                    log.debug("Mapped {} fields → {} after filtering for [{}]",
                            mapped.size(), filtered.size(), connector.name());

                    connector.pushMappedChange(event.getChangeType(), filtered);
                } else {
                    // Legacy / unmapped mode
                    connector.pushChange(event);
                }
            } catch (Exception e) {
                log.error("Connector [{}] failed for userId={}: {}",
                        connector.name(), event.getUserId(), e.getMessage(), e);
            }
        }
    }
}

