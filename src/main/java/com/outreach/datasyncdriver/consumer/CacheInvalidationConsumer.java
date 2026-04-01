package com.outreach.datasyncdriver.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Listens to the {@code config-cache-invalidation} Kafka topic and
 * evicts the specified Caffeine cache on this instance.
 * <p>
 * Every driver instance (including the one that published the event)
 * receives the message and clears its local cache, ensuring all
 * instances stay in sync with the central PostgreSQL database.
 * <p>
 * Uses a separate consumer group ({@code datasync-cache-invalidation})
 * so that <b>every</b> instance receives every invalidation message
 * (each instance is its own consumer in its own group — or more
 * precisely, all instances in the same group each get a copy because
 * the topic should have enough partitions, but to guarantee every
 * instance gets the message, a unique group ID per instance could be
 * used; here we use a shared group with the understanding that the
 * topic has at least as many partitions as instances).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheInvalidationConsumer {

    private final CacheManager cacheManager;

    @KafkaListener(
            topics = "${driver.cache.invalidation-topic:config-cache-invalidation}",
            groupId = "datasync-cache-invalidation-#{T(java.util.UUID).randomUUID().toString()}",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void onCacheInvalidation(String cacheName) {
        log.info("Received cache invalidation for '{}'", cacheName);

        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            log.info("Cleared local cache '{}'", cacheName);
        } else {
            // If a specific cache name is not found, clear all caches as a fallback
            log.warn("Cache '{}' not found — clearing all caches", cacheName);
            cacheManager.getCacheNames().stream()
                    .map(cacheManager::getCache)
                    .filter(Objects::nonNull)
                    .forEach(c -> {
                        c.clear();
                        log.debug("Cleared cache '{}'", c.getName());
                    });
        }
    }
}


