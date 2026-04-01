package com.outreach.datasyncdriver.service;

import com.outreach.datasyncdriver.dto.UserChangeEvent;
import com.outreach.datasyncdriver.dto.UserResponse;
import com.outreach.datasyncdriver.entity.SchemaMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Transforms source DTOs (UserResponse / UserChangeEvent) into
 * {@code Map<targetField, value>} according to the {@link SchemaMapping}
 * rules stored in the configuration database.
 * <p>
 * Supports:
 * <ul>
 *   <li>Flat field access: {@code "name"}, {@code "emailId"}</li>
 *   <li>Dot-notation for JSONB / Map fields: {@code "attributes.department"}</li>
 *   <li>Simple transforms: {@code UPPER}, {@code LOWER}, {@code TRIM}</li>
 *   <li>Default values when the source is null or missing</li>
 *   <li>Reverse mapping (target → source) for future inbound sync</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SchemaMapper {

    private final ConfigurationService configService;

    // ======================== Forward mapping (internal → external) ========================

    /**
     * Map a {@link UserResponse} to a target-system record using schema mappings
     * whose {@code targetSystem} matches the given name.
     *
     * @param source       the user fetched from the identity vault
     * @param targetSystem the target system name (must match SchemaMapping.targetSystem)
     * @return ordered map of target field → value, or empty map if no mappings exist
     */
    public LinkedHashMap<String, Object> mapFromUserResponse(UserResponse source, String targetSystem) {
        List<SchemaMapping> mappings = configService.getMappingsByTargetSystem(targetSystem);
        if (mappings.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return applyMappings(source, mappings);
    }

    /**
     * Map a {@link UserChangeEvent} to a target-system record.
     */
    public LinkedHashMap<String, Object> mapFromChangeEvent(UserChangeEvent source, String targetSystem) {
        List<SchemaMapping> mappings = configService.getMappingsByTargetSystem(targetSystem);
        if (mappings.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return applyMappings(source, mappings);
    }

    /**
     * Returns an ordered list of target field names for the given target system.
     * Useful for building CSV headers, LDAP attribute lists, etc.
     */
    public List<String> getTargetFieldNames(String targetSystem) {
        return configService.getMappingsByTargetSystem(targetSystem).stream()
                .map(SchemaMapping::getTargetField)
                .toList();
    }

    /**
     * Check whether any schema mappings are configured for a target system.
     */
    public boolean hasMappings(String targetSystem) {
        return !configService.getMappingsByTargetSystem(targetSystem).isEmpty();
    }

    // ======================== Reverse mapping (external → internal) ========================

    /**
     * Reverse-map an external record back to internal field names.
     * Useful for inbound synchronisation (external → identity vault).
     *
     * @param externalRecord map of target-field → value
     * @param targetSystem   the external system these fields came from
     * @return map of source-field → value
     */
    public LinkedHashMap<String, Object> reverseMap(Map<String, Object> externalRecord, String targetSystem) {
        List<SchemaMapping> mappings = configService.getMappingsByTargetSystem(targetSystem);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (SchemaMapping mapping : mappings) {
            Object value = externalRecord.get(mapping.getTargetField());
            if (value != null) {
                result.put(mapping.getSourceField(), value);
            }
        }
        return result;
    }

    // ======================== Internal helpers ========================

    private LinkedHashMap<String, Object> applyMappings(Object source, List<SchemaMapping> mappings) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();

        for (SchemaMapping mapping : mappings) {
            try {
                Object value = extractValue(source, mapping.getSourceField());

                // Apply transformation if configured
                if (value != null && mapping.getTransformExpression() != null) {
                    value = applyTransform(value, mapping.getTransformExpression());
                }

                // Apply default value if source is null
                if (value == null && mapping.getDefaultValue() != null) {
                    value = mapping.getDefaultValue();
                }

                result.put(mapping.getTargetField(), value);
            } catch (Exception e) {
                log.warn("Failed to map field '{}' → '{}': {}",
                        mapping.getSourceField(), mapping.getTargetField(), e.getMessage());
                // Use default or null
                result.put(mapping.getTargetField(), mapping.getDefaultValue());
            }
        }
        return result;
    }

    /**
     * Extract a value from a source object by field path.
     * <p>
     * Supports:
     * <ul>
     *   <li>{@code "name"} → source.getName()</li>
     *   <li>{@code "attributes.department"} → source.getAttributes().get("department")</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private Object extractValue(Object source, String fieldPath) {
        if (source == null || fieldPath == null) return null;

        String[] parts = fieldPath.split("\\.", 2);
        String fieldName = parts[0];

        // Get the top-level field value via reflection
        Object value = getFieldValue(source, fieldName);

        // If there's a nested path and the value is a Map, navigate deeper
        if (parts.length > 1 && value instanceof Map) {
            return navigateMap((Map<String, Object>) value, parts[1]);
        }

        return value;
    }

    /**
     * Navigate into a nested map using dot-notation.
     * E.g. "address.city" navigates map.get("address") → innerMap.get("city")
     */
    @SuppressWarnings("unchecked")
    private Object navigateMap(Map<String, Object> map, String path) {
        String[] parts = path.split("\\.", 2);
        Object value = map.get(parts[0]);
        if (parts.length > 1 && value instanceof Map) {
            return navigateMap((Map<String, Object>) value, parts[1]);
        }
        return value;
    }

    /**
     * Reflectively read a field value from an object.
     * Tries the declared field first, then walks up the class hierarchy.
     */
    private Object getFieldValue(Object source, String fieldName) {
        Class<?> clazz = source.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(source);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (IllegalAccessException e) {
                log.warn("Cannot access field '{}' on {}", fieldName, source.getClass().getSimpleName());
                return null;
            }
        }
        log.warn("Field '{}' not found on {}", fieldName, source.getClass().getSimpleName());
        return null;
    }

    /**
     * Apply a simple string transformation.
     */
    private Object applyTransform(Object value, String transform) {
        if (value == null || transform == null) return value;

        String strValue = value.toString();
        return switch (transform.toUpperCase()) {
            case "UPPER" -> strValue.toUpperCase();
            case "LOWER" -> strValue.toLowerCase();
            case "TRIM"  -> strValue.trim();
            default -> {
                log.debug("Unknown transform '{}', returning value as-is", transform);
                yield value;
            }
        };
    }
}

