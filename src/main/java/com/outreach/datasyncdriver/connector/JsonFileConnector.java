package com.outreach.datasyncdriver.connector;

import com.outreach.datasyncdriver.dto.UserChangeEvent;
import com.outreach.datasyncdriver.dto.UserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * JSON file connector — writes user data to a local JSON file
 * as an array of objects.
 * <p>
 * Supports two modes:
 * <ul>
 *   <li><b>Mapped mode</b> — uses schema mappings to determine fields and values dynamically</li>
 *   <li><b>Legacy mode</b> — serializes raw DTOs when no mappings are configured</li>
 * </ul>
 * <p>
 * The output file always contains a JSON array. Initial load overwrites the file;
 * incremental changes read the existing array, append/update, and rewrite.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "driver.connector.type", havingValue = "json")
public class JsonFileConnector implements ExternalSystemConnector {

    private static final String TARGET_SYSTEM = "external-json";

    private final ObjectMapper objectMapper;

    @Value("${driver.json.output-path:sync-output.json}")
    private String outputPath;

    public JsonFileConnector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "JSON File";
    }

    @Override
    public String targetSystemName() {
        return TARGET_SYSTEM;
    }

    // ======================== Mapped mode ========================

    @Override
    public void pushMappedChange(UserChangeEvent.ChangeType changeType, Map<String, Object> mappedData) {
        switch (changeType) {
            case CREATED, UPDATED -> appendMappedRecord(mappedData);
            case DELETED -> {
                Object id = mappedData.values().stream().findFirst().orElse("unknown");
                log.info("[JSON] DELETE id={} — logged (append-only file)", id);
            }
        }
    }

    @Override
    public void initialLoadMapped(List<Map<String, Object>> mappedRecords, List<String> fieldNames) {
        Path path = Path.of(outputPath);
        try {
            // Build ordered records using field name order
            List<LinkedHashMap<String, Object>> orderedRecords = new ArrayList<>();
            for (Map<String, Object> record : mappedRecords) {
                LinkedHashMap<String, Object> ordered = new LinkedHashMap<>();
                for (String field : fieldNames) {
                    ordered.put(field, record.get(field));
                }
                orderedRecords.add(ordered);
            }

            String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(orderedRecords);
            Files.writeString(path, json);

            log.info("[JSON] Mapped initial load complete — {} records written to {}",
                    mappedRecords.size(), path.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write mapped JSON during initial load", e);
        }
    }

    // ======================== Legacy (unmapped) mode ========================

    @Override
    public void pushChange(UserChangeEvent event) {
        switch (event.getChangeType()) {
            case CREATED, UPDATED -> appendLegacyRecord(event);
            case DELETED -> log.info("[JSON] DELETE userId={} — logged (append-only file)", event.getUserId());
        }
    }

    @Override
    public void initialLoad(List<UserResponse> users) {
        Path path = Path.of(outputPath);
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(users);
            Files.writeString(path, json);

            log.info("[JSON] Legacy initial load — {} users written to {}", users.size(), path.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write JSON during initial load", e);
        }
    }

    // ======================== Internal helpers ========================

    /**
     * Reads the existing JSON array from the file, appends the new record,
     * and rewrites the file. If the file doesn't exist or is empty, starts
     * a new array.
     */
    private void appendMappedRecord(Map<String, Object> mappedData) {
        Path path = Path.of(outputPath);
        try {
            List<Map<String, Object>> records = readExistingRecords(path);
            records.add(new LinkedHashMap<>(mappedData));

            String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(records);
            Files.writeString(path, json);

            log.info("[JSON] Appended mapped record ({} fields), total {} records",
                    mappedData.size(), records.size());
        } catch (IOException e) {
            throw new RuntimeException("Failed to append mapped record to JSON", e);
        }
    }

    private void appendLegacyRecord(UserChangeEvent event) {
        Path path = Path.of(outputPath);
        try {
            List<Map<String, Object>> records = readExistingRecords(path);

            LinkedHashMap<String, Object> record = new LinkedHashMap<>();
            record.put("userId", event.getUserId() != null ? event.getUserId().toString() : null);
            record.put("changeType", event.getChangeType().name());
            record.put("name", event.getName());
            record.put("firstName", event.getFirstName());
            record.put("middleName", event.getMiddleName());
            record.put("lastName", event.getLastName());
            record.put("emailId", event.getEmailId());
            record.put("phoneNumber", event.getPhoneNumber());
            record.put("attributes", event.getAttributes());
            records.add(record);

            String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(records);
            Files.writeString(path, json);

            log.info("[JSON] Appended legacy {} for userId={}, total {} records",
                    event.getChangeType(), event.getUserId(), records.size());
        } catch (IOException e) {
            throw new RuntimeException("Failed to append legacy record to JSON", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readExistingRecords(Path path) {
        try {
            if (Files.exists(path) && Files.size(path) > 0) {
                String content = Files.readString(path);
                List<Map<String, Object>> existing = objectMapper.readValue(content, List.class);
                return new ArrayList<>(existing);
            }
        } catch (IOException e) {
            log.warn("[JSON] Failed to read existing file, starting fresh: {}", e.getMessage());
        }
        return new ArrayList<>();
    }
}



