package com.outreach.datasyncdriver.connector;

import com.outreach.datasyncdriver.dto.UserChangeEvent;
import com.outreach.datasyncdriver.dto.UserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * CSV connector — writes user data to a local CSV file.
 * <p>
 * Supports two modes:
 * <ul>
 *   <li><b>Mapped mode</b> — uses schema mappings to determine columns and values dynamically</li>
 *   <li><b>Legacy mode</b> — uses hardcoded field order when no mappings are configured</li>
 * </ul>
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "driver.connector.type", havingValue = "csv")
public class CsvConnector implements ExternalSystemConnector {

    /** Default header used when no schema mappings are configured. */
    private static final String LEGACY_HEADER =
            "user_id,name,first_name,middle_name,last_name,email_id,phone_number,attributes";

    /** Target system name that matches SchemaMapping.targetSystem in the config DB. */
    private static final String TARGET_SYSTEM = "external-csv";

    @Value("${driver.csv.output-path:sync-output.csv}")
    private String outputPath;

    @Override
    public String name() {
        return "CSV File";
    }

    @Override
    public String targetSystemName() {
        return TARGET_SYSTEM;
    }

    // ======================== Mapped mode ========================

    @Override
    public void pushMappedChange(UserChangeEvent.ChangeType changeType, Map<String, Object> mappedData) {
        switch (changeType) {
            case CREATED, UPDATED -> appendMappedRow(changeType, mappedData);
            case DELETED -> {
                Object id = mappedData.values().stream().findFirst().orElse("unknown");
                log.info("[CSV] DELETE id={} — logged (append-only file)", id);
            }
        }
    }

    @Override
    public void initialLoadMapped(List<Map<String, Object>> mappedRecords, List<String> fieldNames) {
        Path path = Path.of(outputPath);
        try (PrintWriter pw = new PrintWriter(new FileWriter(path.toFile(), false))) {
            // Write dynamic header from field names
            pw.println(String.join(",", fieldNames));

            // Write each record in field-name order
            for (Map<String, Object> record : mappedRecords) {
                String[] values = fieldNames.stream()
                        .map(f -> record.get(f) != null ? record.get(f).toString() : "")
                        .toArray(String[]::new);
                pw.println(toCsvRow(values));
            }
            log.info("[CSV] Mapped initial load complete — {} records, {} columns written to {}",
                    mappedRecords.size(), fieldNames.size(), path.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write mapped CSV during initial load", e);
        }
    }

    // ======================== Legacy (unmapped) mode ========================

    @Override
    public void pushChange(UserChangeEvent event) {
        switch (event.getChangeType()) {
            case CREATED, UPDATED -> appendLegacyRow(event);
            case DELETED -> log.info("[CSV] DELETE userId={} — logged (append-only file)", event.getUserId());
        }
    }

    @Override
    public void initialLoad(List<UserResponse> users) {
        Path path = Path.of(outputPath);
        try (PrintWriter pw = new PrintWriter(new FileWriter(path.toFile(), false))) {
            pw.println(LEGACY_HEADER);
            for (UserResponse u : users) {
                pw.println(toCsvRow(
                        str(u.getUserId()),
                        u.getName(),
                        u.getFirstName(),
                        u.getMiddleName(),
                        u.getLastName(),
                        u.getEmailId(),
                        u.getPhoneNumber(),
                        u.getAttributes() != null ? u.getAttributes().toString() : ""));
            }
            log.info("[CSV] Legacy initial load — {} users written to {}", users.size(), path.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write CSV during initial load", e);
        }
    }

    // ======================== Internal helpers ========================

    private void appendMappedRow(UserChangeEvent.ChangeType changeType, Map<String, Object> mappedData) {
        Path path = Path.of(outputPath);
        try {
            boolean needsHeader = !Files.exists(path) || Files.size(path) == 0;
            try (PrintWriter pw = new PrintWriter(new FileWriter(path.toFile(), true))) {
                if (needsHeader) {
                    pw.println(String.join(",", mappedData.keySet()));
                }
                String[] values = mappedData.values().stream()
                        .map(v -> v != null ? v.toString() : "")
                        .toArray(String[]::new);
                pw.println(toCsvRow(values));
            }
            log.info("[CSV] Appended mapped {} row ({} fields)", changeType, mappedData.size());
        } catch (IOException e) {
            throw new RuntimeException("Failed to append mapped row to CSV", e);
        }
    }

    private void appendLegacyRow(UserChangeEvent e) {
        Path path = Path.of(outputPath);
        try {
            boolean needsHeader = !Files.exists(path) || Files.size(path) == 0;
            try (PrintWriter pw = new PrintWriter(new FileWriter(path.toFile(), true))) {
                if (needsHeader) {
                    pw.println(LEGACY_HEADER);
                }
                pw.println(toCsvRow(
                        str(e.getUserId()),
                        e.getName(),
                        e.getFirstName(),
                        e.getMiddleName(),
                        e.getLastName(),
                        e.getEmailId(),
                        e.getPhoneNumber(),
                        e.getAttributes() != null ? e.getAttributes().toString() : ""));
            }
            log.info("[CSV] Appended legacy {} for userId={}", e.getChangeType(), e.getUserId());
        } catch (IOException ex) {
            throw new RuntimeException("Failed to append to CSV", ex);
        }
    }

    private String toCsvRow(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCsv(fields[i]));
        }
        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String str(Object obj) {
        return obj != null ? obj.toString() : "";
    }
}

