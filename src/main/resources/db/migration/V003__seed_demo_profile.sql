-- V003__seed_demo_profile.sql
-- Fictional published demonstration quality profile for educational capstone use

INSERT INTO profile_versions (
    id, profile_key, version_number, name, status, intended_use,
    allowed_source_types_json, record_kind, provenance_kind,
    reference_text, reference_url, published_at
) VALUES (
    'b0000001-0000-0000-0000-000000000001',
    'SURFACE_WATER_DEMO',
    1,
    'Surface Water Baseline (Educational Demo)',
    'PUBLISHED',
    'General surface water quality screening',
    '["RIVER", "LAKE", "RESERVOIR"]',
    'POINT_SAMPLE',
    'MANUAL_ENTRY',
    'Fictional threshold profile created strictly for capstone academic demonstrations',
    NULL,
    '2026-10-20T00:00:00Z'
) ON CONFLICT(profile_key, version_number) DO NOTHING;

-- pH: 6.5 - 8.5 (inclusive, required)
INSERT INTO profile_rules (
    id, profile_version_id, parameter_id, lower_decimal, upper_decimal,
    lower_inclusive, upper_inclusive, required, unit
) VALUES (
    'c0000001-0000-0000-0000-000000000001',
    'b0000001-0000-0000-0000-000000000001',
    'a0000001-0000-0000-0000-000000000001', -- PH
    '6.5', '8.5', 1, 1, 1, '1'
) ON CONFLICT(profile_version_id, parameter_id) DO NOTHING;

-- Turbidity: <= 5.0 NTU (inclusive, required)
INSERT INTO profile_rules (
    id, profile_version_id, parameter_id, lower_decimal, upper_decimal,
    lower_inclusive, upper_inclusive, required, unit
) VALUES (
    'c0000001-0000-0000-0000-000000000002',
    'b0000001-0000-0000-0000-000000000001',
    'a0000001-0000-0000-0000-000000000002', -- TURB
    NULL, '5.0', 1, 1, 1, 'NTU'
) ON CONFLICT(profile_version_id, parameter_id) DO NOTHING;

-- Dissolved Oxygen: >= 5.0 mg/L (inclusive, required)
INSERT INTO profile_rules (
    id, profile_version_id, parameter_id, lower_decimal, upper_decimal,
    lower_inclusive, upper_inclusive, required, unit
) VALUES (
    'c0000001-0000-0000-0000-000000000003',
    'b0000001-0000-0000-0000-000000000001',
    'a0000001-0000-0000-0000-000000000003', -- DO
    '5.0', NULL, 1, 1, 1, 'mg/L'
) ON CONFLICT(profile_version_id, parameter_id) DO NOTHING;

-- Electrical Conductivity: <= 1500.0 uS/cm (optional)
INSERT INTO profile_rules (
    id, profile_version_id, parameter_id, lower_decimal, upper_decimal,
    lower_inclusive, upper_inclusive, required, unit
) VALUES (
    'c0000001-0000-0000-0000-000000000004',
    'b0000001-0000-0000-0000-000000000001',
    'a0000001-0000-0000-0000-000000000004', -- COND
    NULL, '1500.0', 1, 1, 0, 'uS/cm'
) ON CONFLICT(profile_version_id, parameter_id) DO NOTHING;
