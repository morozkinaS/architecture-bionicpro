-- ============================================================
-- BionicPRO: тестовые данные для сервиса отчётов
-- user_id совпадает с sub (UUID) пользователей Keycloak
-- из realm-export.json, чтобы отчёты можно было связать с JWT.
-- ============================================================

-- Клиенты (имитация выгрузки из CRM / Битрикс24)
INSERT INTO crm_clients (client_id, user_id, full_name, prosthesis_model, prosthesis_id, country, contract_date) VALUES
    ('CL-0001', '10000000-0000-0000-0000-000000000011', 'Prothetic One',   'BionicPro Arm X1', 'PR-001', 'Russia', CURRENT_DATE - INTERVAL '180 days'),
    ('CL-0002', '10000000-0000-0000-0000-000000000012', 'Prothetic Two',   'BionicPro Leg X2', 'PR-002', 'Russia', CURRENT_DATE - INTERVAL '150 days'),
    ('CL-0003', '10000000-0000-0000-0000-000000000013', 'Prothetic Three', 'BionicPro Arm X1', 'PR-003', 'Russia', CURRENT_DATE - INTERVAL '120 days'),
    ('CL-0004', '10000000-0000-0000-0000-000000000001', 'User One',        'BionicPro Hand X3', 'PR-004', 'Russia', CURRENT_DATE - INTERVAL '90 days'),
    ('CL-0005', '10000000-0000-0000-0000-000000000002', 'User Two',        'BionicPro Leg X2', 'PR-005', 'Russia', CURRENT_DATE - INTERVAL '60 days');

-- Телеметрия протезов за последние 7 дней:
-- 12 записей в день на каждого клиента (каждые 2 часа)
INSERT INTO prosthesis_telemetry (user_id, prosthesis_id, event_time, activation_count, response_time_ms, battery_level, usage_minutes)
SELECT
    c.user_id,
    c.prosthesis_id,
    (CURRENT_DATE - d.day_offset)::timestamp + (h.hour_offset * INTERVAL '2 hours'),
    (10 + (random() * 90))::int,
    (60 + (random() * 40))::int,
    round((50 + (random() * 50))::numeric, 1),
    (30 + (random() * 120))::int
FROM crm_clients c
CROSS JOIN generate_series(0, 6) AS d(day_offset)
CROSS JOIN generate_series(0, 11) AS h(hour_offset);