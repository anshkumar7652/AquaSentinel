-- V002__seed_parameters.sql
-- Fictional baseline demo parameters for educational capstone demonstration

INSERT INTO parameters (id, code, name, canonical_unit, input_min_decimal, input_max_decimal)
VALUES
    ('a0000001-0000-0000-0000-000000000001', 'PH', 'pH Level', '1', '0.0', '14.0'),
    ('a0000001-0000-0000-0000-000000000002', 'TURB', 'Turbidity', 'NTU', '0.0', '1000.0'),
    ('a0000001-0000-0000-0000-000000000003', 'DO', 'Dissolved Oxygen', 'mg/L', '0.0', '20.0'),
    ('a0000001-0000-0000-0000-000000000004', 'COND', 'Electrical Conductivity', 'uS/cm', '0.0', '10000.0'),
    ('a0000001-0000-0000-0000-000000000005', 'TDS', 'Total Dissolved Solids', 'mg/L', '0.0', '5000.0'),
    ('a0000001-0000-0000-0000-000000000006', 'TEMP', 'Water Temperature', 'degC', '-10.0', '100.0'),
    ('a0000001-0000-0000-0000-000000000007', 'NITRATE', 'Nitrate (as NO3)', 'mg/L', '0.0', '500.0')
ON CONFLICT(code) DO NOTHING;
