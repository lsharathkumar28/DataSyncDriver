package com.outreach.datasyncdriver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes cache-invalidation events to a Kafka topic whenever
 * configuration data is mutated. All driver instances (including
 * the one that made the change) listen on this topic and evict
 * their local Caffeine caches accordingly.
 * <p>
 * The message payload is simply the cache name to invalidate,
 * e.g. {@code "connections"}, {@code "schemaMappings"}, {@code "syncRules"}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheInvalidationPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${driver.cache.invalidation-topic:config-cache-invalidation}")
    private String invalidationTopic;

    /**
     * Broadcast a cache invalidation event for the given cache name.
     *
     * @param cacheName the Caffeine cache to evict on all instances
     */
    public void publishInvalidation(String cacheName) {
        kafkaTemplate.send(invalidationTopic, cacheName, cacheName);
        log.info("Published cache invalidation for '{}'", cacheName);
    }
}

