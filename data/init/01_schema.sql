-- ============================================================
-- BionicPRO: схема данных для сервиса отчётов
-- PostgreSQL (data_db) — источник данных для ETL-процесса
-- ============================================================

-- Телеметрия протезов (данные с датчиков, поступающие с чипа протеза)
CREATE TABLE IF NOT EXISTS prosthesis_telemetry (
    id SERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    prosthesis_id VARCHAR(50) NOT NULL,
    event_time TIMESTAMP NOT NULL,
    activation_count INT NOT NULL,
    response_time_ms INT NOT NULL,
    battery_level FLOAT NOT NULL,
    usage_minutes INT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_telemetry_user_date
    ON prosthesis_telemetry (user_id, event_time);

-- Справочник клиентов (имитация выгрузки из CRM / Битрикс24)
CREATE TABLE IF NOT EXISTS crm_clients (
    client_id VARCHAR(50) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    prosthesis_model VARCHAR(100) NOT NULL,
    prosthesis_id VARCHAR(50) NOT NULL,
    country VARCHAR(50) NOT NULL,
    contract_date DATE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_crm_user
    ON crm_clients (user_id);