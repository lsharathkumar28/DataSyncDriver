-- =============================================================================
-- Seed data for DataSyncDriver configuration tables.
-- Executed by Spring Boot after Hibernate creates/updates the schema.
-- Uses ON CONFLICT DO NOTHING to be idempotent across restarts.
-- =============================================================================

-- ======================== Connection Configs ========================

-- 1. Internal system — DataSynchronizer (identity vault) REST API
INSERT INTO connection_configs (name, description, system_type, connection_type, base_url, host, port, auth_username, auth_password, additional_properties, active, created_at, updated_at)
VALUES (
    'internal-idvault',
    'Connection to the DataSynchronizer identity vault REST API',
    'INTERNAL',
    'REST',
    'http://localhost:8080',
    'localhost',
    8080,
    NULL,
    NULL,
    '{"apiVersion":"v1","healthEndpoint":"/actuator/health"}',
    true,
    NOW(),
    NOW()
)
ON CONFLICT (name) DO NOTHING;

-- 2. External system — CSV file connector
INSERT INTO connection_configs (name, description, system_type, connection_type, base_url, host, port, auth_username, auth_password, additional_properties, active, created_at, updated_at)
VALUES (
    'external-csv',
    'CSV file output connector — writes user data to a local CSV file',
    'EXTERNAL',
    'CSV',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    '{"outputPath":"sync-output.csv","delimiter":",","includeHeader":true}',
    true,
    NOW(),
    NOW()
)
ON CONFLICT (name) DO NOTHING;

-- 3. External system — JSON file connector
INSERT INTO connection_configs (name, description, system_type, connection_type, base_url, host, port, auth_username, auth_password, additional_properties, active, created_at, updated_at)
VALUES (
    'external-json',
    'JSON file output connector — writes user data to a local JSON file',
    'EXTERNAL',
    'JSON',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    '{"outputPath":"sync-output.json","prettyPrint":true}',
    true,
    NOW(),
    NOW()
)
ON CONFLICT (name) DO NOTHING;


-- ======================== Schema Mappings ========================
-- Group: "user-to-csv" — maps internal user fields to CSV columns
-- Source system: "internal-idvault", Target system: "external-csv"

INSERT INTO schema_mappings (mapping_group_name, source_system, target_system, source_field, target_field, data_type, transform_expression, default_value, required, created_at, updated_at)
VALUES (
    'user-to-csv', 'internal-idvault', 'external-csv',
    'userId', 'user_id',
    'UUID', NULL, NULL, true,
    NOW(), NOW()
)
ON CONFLICT (mapping_group_name, source_field, target_field) DO NOTHING;

INSERT INTO schema_mappings (mapping_group_name, source_system, target_system, source_field, target_field, data_type, transform_expression, default_value, required, created_at, updated_at)
VALUES (
    'user-to-csv', 'internal-idvault', 'external-csv',
    'name', 'full_name',
    'STRING', 'UPPER', NULL, true,
    NOW(), NOW()
)
ON CONFLICT (mapping_group_name, source_field, target_field) DO NOTHING;

INSERT INTO schema_mappings (mapping_group_name, source_system, target_system, source_field, target_field, data_type, transform_expression, default_value, required, created_at, updated_at)
VALUES (
    'user-to-csv', 'internal-idvault', 'external-csv',
    'firstName', 'first_name',
    'STRING', 'TRIM', NULL, false,
    NOW(), NOW()
)
ON CONFLICT (mapping_group_name, source_field, target_field) DO NOTHING;

INSERT INTO schema_mappings (mapping_group_name, source_system, target_system, source_field, target_field, data_type, transform_expression, default_value, required, created_at, updated_at)
VALUES (
    'user-to-csv', 'internal-idvault', 'external-csv',
    'lastName', 'last_name',
    'STRING', 'TRIM', NULL, false,
    NOW(), NOW()
)
ON CONFLICT (mapping_group_name, source_field, target_field) DO NOTHING;

INSERT INTO schema_mappings (mapping_group_name, source_system, target_system, source_field, target_field, data_type, transform_expression, default_value, required, created_at, updated_at)
VALUES (
    'user-to-csv', 'internal-idvault', 'external-csv',
    'emailId', 'email_address',
    'STRING', 'LOWER', NULL, true,
    NOW(), NOW()
)
ON CONFLICT (mapping_group_name, source_field, target_field) DO NOTHING;

INSERT INTO schema_mappings (mapping_group_name, source_system, target_system, source_field, target_field, data_type, transform_expression, default_value, required, created_at, updated_at)
VALUES (
    'user-to-csv', 'internal-idvault', 'external-csv',
    'phoneNumber', 'phone',
    'STRING', NULL, 'N/A', false,
    NOW(), NOW()
)
ON CONFLICT (mapping_group_name, source_field, target_field) DO NOTHING;

INSERT INTO schema_mappings (mapping_group_name, source_system, target_system, source_field, target_field, data_type, transform_expression, default_value, required, created_at, updated_at)
VALUES (
    'user-to-csv', 'internal-idvault', 'external-csv',
    'attributes.department', 'department',
    'STRING', NULL, 'UNASSIGNED', false,
    NOW(), NOW()
)
ON CONFLICT (mapping_group_name, source_field, target_field) DO NOTHING;

INSERT INTO schema_mappings (mapping_group_name, source_system, target_system, source_field, target_field, data_type, transform_expression, default_value, required, created_at, updated_at)
VALUES (
    'user-to-csv', 'internal-idvault', 'external-csv',
    'attributes.title', 'job_title',
    'STRING', NULL, NULL, false,
    NOW(), NOW()
)
ON CONFLICT (mapping_group_name, source_field, target_field) DO NOTHING;


-- ======================== Sync Rules ========================
-- Connector name: "CSV File" (matches CsvConnector.name())
-- Controls which mapped fields are allowed through to the CSV connector

-- Allow user_id — outbound, high priority
INSERT INTO sync_rules (rule_name, connector_name, attribute_name, sync_enabled, direction, filter_expression, priority, created_at, updated_at)
VALUES (
    'csv-allow-user-id', 'CSV File', 'user_id',
    true, 'OUTBOUND', NULL, 10,
    NOW(), NOW()
)
ON CONFLICT (connector_name, attribute_name) DO NOTHING;

-- Allow full_name — outbound
INSERT INTO sync_rules (rule_name, connector_name, attribute_name, sync_enabled, direction, filter_expression, priority, created_at, updated_at)
VALUES (
    'csv-allow-full-name', 'CSV File', 'full_name',
    true, 'OUTBOUND', NULL, 20,
    NOW(), NOW()
)
ON CONFLICT (connector_name, attribute_name) DO NOTHING;

-- Allow first_name — outbound
INSERT INTO sync_rules (rule_name, connector_name, attribute_name, sync_enabled, direction, filter_expression, priority, created_at, updated_at)
VALUES (
    'csv-allow-first-name', 'CSV File', 'first_name',
    true, 'OUTBOUND', NULL, 30,
    NOW(), NOW()
)
ON CONFLICT (connector_name, attribute_name) DO NOTHING;

-- Allow last_name — outbound
INSERT INTO sync_rules (rule_name, connector_name, attribute_name, sync_enabled, direction, filter_expression, priority, created_at, updated_at)
VALUES (
    'csv-allow-last-name', 'CSV File', 'last_name',
    true, 'OUTBOUND', NULL, 40,
    NOW(), NOW()
)
ON CONFLICT (connector_name, attribute_name) DO NOTHING;

-- Allow email_address — outbound
INSERT INTO sync_rules (rule_name, connector_name, attribute_name, sync_enabled, direction, filter_expression, priority, created_at, updated_at)
VALUES (
    'csv-allow-email', 'CSV File', 'email_address',
    true, 'OUTBOUND', NULL, 50,
    NOW(), NOW()
)
ON CONFLICT (connector_name, attribute_name) DO NOTHING;

-- Allow phone — outbound
INSERT INTO sync_rules (rule_name, connector_name, attribute_name, sync_enabled, direction, filter_expression, priority, created_at, updated_at)
VALUES (
    'csv-allow-phone', 'CSV File', 'phone',
    true, 'OUTBOUND', NULL, 60,
    NOW(), NOW()
)
ON CONFLICT (connector_name, attribute_name) DO NOTHING;

-- Allow department — outbound
INSERT INTO sync_rules (rule_name, connector_name, attribute_name, sync_enabled, direction, filter_expression, priority, created_at, updated_at)
VALUES (
    'csv-allow-department', 'CSV File', 'department',
    true, 'OUTBOUND', NULL, 70,
    NOW(), NOW()
)
ON CONFLICT (connector_name, attribute_name) DO NOTHING;

-- Allow job_title — outbound
INSERT INTO sync_rules (rule_name, connector_name, attribute_name, sync_enabled, direction, filter_expression, priority, created_at, updated_at)
VALUES (
    'csv-allow-job-title', 'CSV File', 'job_title',
    true, 'OUTBOUND', NULL, 80,
    NOW(), NOW()
)
ON CONFLICT (connector_name, attribute_name) DO NOTHING;

-- BLOCK ssn — sensitive field, must not leak to CSV
INSERT INTO sync_rules (rule_name, connector_name, attribute_name, sync_enabled, direction, filter_expression, priority, created_at, updated_at)
VALUES (
    'csv-block-ssn', 'CSV File', 'ssn',
    false, 'BIDIRECTIONAL', NULL, 5,
    NOW(), NOW()
)
ON CONFLICT (connector_name, attribute_name) DO NOTHING;

-- BLOCK salary — sensitive field, must not leak to CSV
INSERT INTO sync_rules (rule_name, connector_name, attribute_name, sync_enabled, direction, filter_expression, priority, created_at, updated_at)
VALUES (
    'csv-block-salary', 'CSV File', 'salary',
    false, 'BIDIRECTIONAL', NULL, 5,
    NOW(), NOW()
)
ON CONFLICT (connector_name, attribute_name) DO NOTHING;


-- ======================== Schema Mappings (JSON) ========================
-- Group: "user-to-json" — maps internal user fields to JSON properties
-- Source system: "internal-idvault", Target system: "external-json"

INSERT INTO schema_mappings (mapping_group_name, source_system, target_system, source_field, target_field, data_type, transform_expression, default_value, required, created_at, updated_at)
VALUES (
    'user-to-json', 'internal-idvault', 'external-json',
    'userId', 'id',
    'UUID', NULL, NULL, true,
    NOW(), NOW()
)
ON CONFLICT (mapping_group_name, source_field, target_field) DO NOTHING;

INSERT INTO schema_mappings (mapping_group_name, source_system, target_system, source_field, target_field, data_type, transform_expression, default_value, required, created_at, updated_at)
VALUES (
    'user-to-json', 'internal-idvault', 'external-json',
    'name', 'displayName',
    'STRING', NULL, NULL, true,
    NOW(), NOW()
)
ON CONFLICT (mapping_group_name, source_field, target_field) DO NOTHING;

INSERT INTO schema_mappings (mapping_group_name, source_system, target_system, source_field, target_field, data_type, transform_expression, default_value, required, created_at, updated_at)
VALUES (
    'user-to-json', 'internal-idvault', 'external-json',
    'firstName', 'givenName',
    'STRING', NULL, NULL, false,
    NOW(), NOW()
)
ON CONFLICT (mapping_group_name, source_field, target_field) DO NOTHING;

INSERT INTO schema_mappings (mapping_group_name, source_system, target_system, source_field, target_field, data_type, transform_expression, default_value, required, created_at, updated_at)
VALUES (
    'user-to-json', 'internal-idvault', 'external-json',
    'lastName', 'surname',
    'STRING', NULL, NULL, false,
    NOW(), NOW()
)
ON CONFLICT (mapping_group_name, source_field, target_field) DO NOTHING;

INSERT INTO schema_mappings (mapping_group_name, source_system, target_system, source_field, target_field, data_type, transform_expression, default_value, required, created_at, updated_at)
VALUES (
    'user-to-json', 'internal-idvault', 'external-json',
    'emailId', 'mail',
    'STRING', 'LOWER', NULL, true,
    NOW(), NOW()
)
ON CONFLICT (mapping_group_name, source_field, target_field) DO NOTHING;

INSERT INTO schema_mappings (mapping_group_name, source_system, target_system, source_field, target_field, data_type, transform_expression, default_value, required, created_at, updated_at)
VALUES (
    'user-to-json', 'internal-idvault', 'external-json',
    'phoneNumber', 'telephoneNumber',
    'STRING', NULL, NULL, false,
    NOW(), NOW()
)
ON CONFLICT (mapping_group_name, source_field, target_field) DO NOTHING;

INSERT INTO schema_mappings (mapping_group_name, source_system, target_system, source_field, target_field, data_type, transform_expression, default_value, required, created_at, updated_at)
VALUES (
    'user-to-json', 'internal-idvault', 'external-json',
    'attributes.department', 'department',
    'STRING', 'UPPER', 'UNASSIGNED', false,
    NOW(), NOW()
)
ON CONFLICT (mapping_group_name, source_field, target_field) DO NOTHING;

INSERT INTO schema_mappings (mapping_group_name, source_system, target_system, source_field, target_field, data_type, transform_expression, default_value, required, created_at, updated_at)
VALUES (
    'user-to-json', 'internal-idvault', 'external-json',
    'attributes.title', 'jobTitle',
    'STRING', NULL, NULL, false,
    NOW(), NOW()
)
ON CONFLICT (mapping_group_name, source_field, target_field) DO NOTHING;

INSERT INTO schema_mappings (mapping_group_name, source_system, target_system, source_field, target_field, data_type, transform_expression, default_value, required, created_at, updated_at)
VALUES (
    'user-to-json', 'internal-idvault', 'external-json',
    'attributes.location', 'officeLocation',
    'STRING', NULL, NULL, false,
    NOW(), NOW()
)
ON CONFLICT (mapping_group_name, source_field, target_field) DO NOTHING;


-- ======================== Sync Rules (JSON) ========================
-- Connector name: "JSON File" (matches JsonFileConnector.name())

INSERT INTO sync_rules (rule_name, connector_name, attribute_name, sync_enabled, direction, filter_expression, priority, created_at, updated_at)
VALUES ('json-allow-id', 'JSON File', 'id', true, 'OUTBOUND', NULL, 10, NOW(), NOW())
ON CONFLICT (connector_name, attribute_name) DO NOTHING;

INSERT INTO sync_rules (rule_name, connector_name, attribute_name, sync_enabled, direction, filter_expression, priority, created_at, updated_at)
VALUES ('json-allow-displayName', 'JSON File', 'displayName', true, 'OUTBOUND', NULL, 20, NOW(), NOW())
ON CONFLICT (connector_name, attribute_name) DO NOTHING;

INSERT INTO sync_rules (rule_name, connector_name, attribute_name, sync_enabled, direction, filter_expression, priority, created_at, updated_at)
VALUES ('json-allow-givenName', 'JSON File', 'givenName', true, 'OUTBOUND', NULL, 30, NOW(), NOW())
ON CONFLICT (connector_name, attribute_name) DO NOTHING;

INSERT INTO sync_rules (rule_name, connector_name, attribute_name, sync_enabled, direction, filter_expression, priority, created_at, updated_at)
VALUES ('json-allow-surname', 'JSON File', 'surname', true, 'OUTBOUND', NULL, 40, NOW(), NOW())
ON CONFLICT (connector_name, attribute_name) DO NOTHING;

INSERT INTO sync_rules (rule_name, connector_name, attribute_name, sync_enabled, direction, filter_expression, priority, created_at, updated_at)
VALUES ('json-allow-mail', 'JSON File', 'mail', true, 'OUTBOUND', NULL, 50, NOW(), NOW())
ON CONFLICT (connector_name, attribute_name) DO NOTHING;

INSERT INTO sync_rules (rule_name, connector_name, attribute_name, sync_enabled, direction, filter_expression, priority, created_at, updated_at)
VALUES ('json-allow-phone', 'JSON File', 'telephoneNumber', true, 'OUTBOUND', NULL, 60, NOW(), NOW())
ON CONFLICT (connector_name, attribute_name) DO NOTHING;

INSERT INTO sync_rules (rule_name, connector_name, attribute_name, sync_enabled, direction, filter_expression, priority, created_at, updated_at)
VALUES ('json-allow-dept', 'JSON File', 'department', true, 'OUTBOUND', NULL, 70, NOW(), NOW())
ON CONFLICT (connector_name, attribute_name) DO NOTHING;

INSERT INTO sync_rules (rule_name, connector_name, attribute_name, sync_enabled, direction, filter_expression, priority, created_at, updated_at)
VALUES ('json-allow-jobTitle', 'JSON File', 'jobTitle', true, 'OUTBOUND', NULL, 80, NOW(), NOW())
ON CONFLICT (connector_name, attribute_name) DO NOTHING;

INSERT INTO sync_rules (rule_name, connector_name, attribute_name, sync_enabled, direction, filter_expression, priority, created_at, updated_at)
VALUES ('json-allow-office', 'JSON File', 'officeLocation', true, 'OUTBOUND', NULL, 90, NOW(), NOW())
ON CONFLICT (connector_name, attribute_name) DO NOTHING;

-- BLOCK ssn — sensitive field, must not leak to JSON
INSERT INTO sync_rules (rule_name, connector_name, attribute_name, sync_enabled, direction, filter_expression, priority, created_at, updated_at)
VALUES ('json-block-ssn', 'JSON File', 'ssn', false, 'BIDIRECTIONAL', NULL, 5, NOW(), NOW())
ON CONFLICT (connector_name, attribute_name) DO NOTHING;

-- BLOCK salary — sensitive field, must not leak to JSON
INSERT INTO sync_rules (rule_name, connector_name, attribute_name, sync_enabled, direction, filter_expression, priority, created_at, updated_at)
VALUES ('json-block-salary', 'JSON File', 'salary', false, 'BIDIRECTIONAL', NULL, 5, NOW(), NOW())
ON CONFLICT (connector_name, attribute_name) DO NOTHING;


