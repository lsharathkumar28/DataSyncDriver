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
            case CREATED -> upsertMappedRecord(mappedData);
            case UPDATED -> upsertMappedRecord(mappedData);
            case DELETED -> deleteMappedRecord(mappedData);
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
            case CREATED -> upsertLegacyRecord(event);
            case UPDATED -> upsertLegacyRecord(event);
            case DELETED -> deleteLegacyRecord(event);
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
     * Finds the ID field name (first key) and its value from a mapped record.
     */
    private String getIdFieldName(Map<String, Object> mappedData) {
        return mappedData.keySet().iterator().next();
    }

    private String getIdValue(Map<String, Object> mappedData) {
        return mappedData.values().stream().findFirst()
                .map(Object::toString).orElse("");
    }

    /**
     * Upsert a mapped record — if a record with the same ID exists, replace it.
     * Otherwise append.
     */
    private void upsertMappedRecord(Map<String, Object> mappedData) {
        Path path = Path.of(outputPath);
        try {
            String idField = getIdFieldName(mappedData);
            String idValue = getIdValue(mappedData);

            List<Map<String, Object>> records = readExistingRecords(path);
            boolean replaced = false;

            for (int i = 0; i < records.size(); i++) {
                Object existing = records.get(i).get(idField);
                if (existing != null && existing.toString().equals(idValue)) {
                    records.set(i, new LinkedHashMap<>(mappedData));
                    replaced = true;
                    break;
                }
            }

            if (!replaced) {
                records.add(new LinkedHashMap<>(mappedData));
            }

            Files.writeString(path, objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(records));
            log.info("[JSON] {} mapped record for {}={}, total {} records",
                    replaced ? "Updated" : "Inserted", idField, idValue, records.size());
        } catch (IOException e) {
            throw new RuntimeException("Failed to upsert mapped record in JSON", e);
        }
    }

    /**
     * Delete a mapped record by matching the first field (ID).
     */
    private void deleteMappedRecord(Map<String, Object> mappedData) {
        Path path = Path.of(outputPath);
        try {
            String idField = getIdFieldName(mappedData);
            String idValue = getIdValue(mappedData);

            List<Map<String, Object>> records = readExistingRecords(path);
            int sizeBefore = records.size();
            records.removeIf(r -> {
                Object existing = r.get(idField);
                return existing != null && existing.toString().equals(idValue);
            });

            if (records.size() == sizeBefore) {
                log.info("[JSON] DELETE {}={} — not found in file", idField, idValue);
                return;
            }

            Files.writeString(path, objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(records));
            log.info("[JSON] Deleted mapped record for {}={}, total {} records",
                    idField, idValue, records.size());
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete mapped record from JSON", e);
        }
    }

    private void upsertLegacyRecord(UserChangeEvent event) {
        Path path = Path.of(outputPath);
        try {
            String userId = event.getUserId() != null ? event.getUserId().toString() : null;

            List<Map<String, Object>> records = readExistingRecords(path);
            boolean replaced = false;

            LinkedHashMap<String, Object> record = new LinkedHashMap<>();
            record.put("userId", userId);
            record.put("name", event.getName());
            record.put("firstName", event.getFirstName());
            record.put("middleName", event.getMiddleName());
            record.put("lastName", event.getLastName());
            record.put("emailId", event.getEmailId());
            record.put("phoneNumber", event.getPhoneNumber());
            record.put("attributes", event.getAttributes());

            for (int i = 0; i < records.size(); i++) {
                Object existing = records.get(i).get("userId");
                if (existing != null && existing.toString().equals(userId)) {
                    records.set(i, record);
                    replaced = true;
                    break;
                }
            }

            if (!replaced) {
                records.add(record);
            }

            Files.writeString(path, objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(records));
            log.info("[JSON] {} legacy record for userId={}, total {} records",
                    replaced ? "Updated" : "Inserted", userId, records.size());
        } catch (IOException e) {
            throw new RuntimeException("Failed to upsert legacy record in JSON", e);
        }
    }

    private void deleteLegacyRecord(UserChangeEvent event) {
        Path path = Path.of(outputPath);
        try {
            String userId = event.getUserId() != null ? event.getUserId().toString() : null;

            List<Map<String, Object>> records = readExistingRecords(path);
            int sizeBefore = records.size();
            records.removeIf(r -> {
                Object existing = r.get("userId");
                return existing != null && existing.toString().equals(userId);
            });

            if (records.size() == sizeBefore) {
                log.info("[JSON] DELETE userId={} — not found in file", userId);
                return;
            }

            Files.writeString(path, objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(records));
            log.info("[JSON] Deleted legacy record for userId={}, total {} records",
                    userId, records.size());
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete legacy record from JSON", e);
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



