# BionicPRO — проектная работа 9 спринта

Проект «Архитектура и проектирование ИС»: повышение безопасности аутентификации
и разработка сервиса отчётов для платформы управления бионическими протезами.

## Состав проекта

| Компонент | Технология | Назначение |
|---|---|---|
| `frontend/` | React + TypeScript + Keycloak | UI платформы, вход через PKCE |
| `backend/` | Java 17 + Spring Boot 3.2 | REST API отчётов `/reports` |
| `keycloak/` | Keycloak 21.1 | IdP, realm `reports-realm`, PKCE |
| `airflow/` | Apache Airflow 2.9 | ETL-витрина отчётности в ClickHouse |
| `data/` | PostgreSQL 14 | Источники данных: телеметрия + CRM |
| ClickHouse | ClickHouse 24.3 | OLAP-витрина `reports_mart` |

## Задание 1 — Повышение безопасности

1. **C4-диаграмма управления учётными данными** — `BionicPRO_C4_model.drawio.xml`:
   добавлены Удостоверяющий центр (Keycloak), Token Manager (BFF) и внешние
   удостоверяющие службы, показан поток PKCE.
2. **Замена Code Grant на PKCE**:
   - `frontend/src/App.tsx` — `initOptions: { pkceMethod: 'S256' }`;
   - `keycloak/realm-export.json` — у клиента `reports-frontend` отключён
     Resource Owner Password Credentials (`directAccessGrantsEnabled: false`),
     включён PKCE (`pkce.code.challenge.method: S256`).

## Задание 2 — Сервис отчётов

1. **Архитектура** — `BionicPRO_reports_architecture.drawio.xml`.
2. **ETL** — `airflow/dags/reports_etl_dag.py`: ежедневно в 03:00 извлекает
   телеметрию протезов и справочник клиентов из PostgreSQL, агрегирует и
   загружает витрину `reports_mart` в ClickHouse. Идемпотентен: перед записью
   данные за дату очищаются, повторные запуски безопасны.
3. **API** — `GET /reports?from=YYYY-MM-DD&to=YYYY-MM-DD&format=json|csv`:
   - доступ только по JWT (Keycloak), `user_id` берётся из `sub` токена;
   - пользователь видит только свои данные;
   - `409` с понятным сообщением, если отчёт за период ещё не готов;
   - `format=csv` — выгрузка в CSV.
4. **UI** — кнопка «Получить отчёт» на странице отчётов с выбором периода
   и скачиванием CSV.

## Запуск

Требуется Docker Desktop.

```bash
docker compose up -d --build
```

Сервисы:

| Сервис | Адрес |
|---|---|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8000 |
| Keycloak (admin: `admin` / `admin`) | http://localhost:8080 |
| Airflow (admin: `admin` / `admin`) | http://localhost:8081 |
| PostgreSQL (данные) | localhost:5434 |
| ClickHouse | localhost:8123 |

### Пользователи

| Логин | Пароль | Роль |
|---|---|---|
| `prothetic1`, `prothetic2`, `prothetic3` | `prothetic123` | протезист |
| `user1`, `user2` | `password123` | пользователь |
| `admin1` | `admin123` | администратор |

### Проверка ETL

Данные телеметрии генерируются при первом старте за последние 7 дней.
Запуск DAG вручную (backfill):

```bash
docker exec -it architecture-bionicpro-airflow-webserver-1 \
  airflow dags backfill reports_etl_dag \
  --start-date YYYY-MM-DD --end-date YYYY-MM-DD
```

> `--end-date` указывайте на день позже последней нужной даты
> (execution_date DAG — 03:00 UTC).

Проверка витрины:

```bash
docker exec -it architecture-bionicpro-clickhouse-1 \
  clickhouse-client -u default --password clickhouse \
  --query "SELECT report_date, count(*) FROM reports_mart GROUP BY report_date ORDER BY report_date"
```

### Проверка API

```bash
# без токена — 401
curl http://localhost:8000/reports?from=2026-09-15\&to=2026-09-21

# с токеном (получить через UI или Keycloak admin API)
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8000/reports?from=2026-09-15&to=2026-09-21"

# CSV
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8000/reports?from=2026-09-15&to=2026-09-21&format=csv"
```

## Примечания

- `user_id` в данных телеметрии совпадает с `sub` пользователей Keycloak
  (явные UUID заданы в `keycloak/realm-export.json` и `data/init/02_seed_data.sql`).
- Airflow DAG написан на Python — это единственный поддерживаемый язык для DAG
  в Airflow; остальной бэкенд — Java.
- ClickHouse использует named volume `clickhouse-data` (bind mount на Windows
  вызывает ошибки прав доступа).