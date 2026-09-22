"""
DAG reports_etl_dag: ETL-процесс подготовки витрины отчётности BionicPRO.

Извлекает данные телеметрии протезов из PostgreSQL и данные клиентов из CRM,
объединяет их и загружает агрегированную витрину отчётности в ClickHouse.

Расписание: ежедневно в 03:00 (0 3 * * *).
Каждый запуск обрабатывает данные за execution_date (ds).

Надёжность:
- retries с экспоненциальной задержкой (at-least-once);
- идемпотентная загрузка: перед записью данных за дату соответствующий
  диапазон витрины и staging очищается, повторные запуски безопасны.
"""

from datetime import datetime, timedelta

import clickhouse_connect
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.providers.postgres.hooks.postgres import PostgresHook

# --- Конфигурация подключений ---
# PostgreSQL: connection задаётся через AIRFLOW_CONN_BIONICPRO_DATA_DB
POSTGRES_CONN_ID = "bionicpro_data_db"

# ClickHouse: параметры подключения
CLICKHOUSE_HOST = "clickhouse"
CLICKHOUSE_PORT = 8123
CLICKHOUSE_DATABASE = "default"
CLICKHOUSE_USERNAME = "default"
CLICKHOUSE_PASSWORD = "clickhouse"

# Таблицы
MART_TABLE = "reports_mart"
TELEMETRY_STAGING_TABLE = "telemetry_staging"
CRM_STAGING_TABLE = "crm_staging"

DEFAULT_ARGS = {
    "owner": "bionicpro",
    "depends_on_past": False,
    "email_on_failure": False,
    "email_on_retry": False,
    "retries": 3,
    "retry_delay": timedelta(minutes=5),
    "retry_exponential_backoff": True,
}


def _clickhouse_client():
    """Возвращает клиент ClickHouse."""
    kwargs = {
        "host": CLICKHOUSE_HOST,
        "port": CLICKHOUSE_PORT,
        "database": CLICKHOUSE_DATABASE,
        "username": CLICKHOUSE_USERNAME,
    }
    if CLICKHOUSE_PASSWORD:
        kwargs["password"] = CLICKHOUSE_PASSWORD
    return clickhouse_connect.get_client(**kwargs)


def create_tables():
    """Создаёт витрину отчётности и staging-таблицы (идемпотентно)."""
    client = _clickhouse_client()
    client.command(
        f"""
        CREATE TABLE IF NOT EXISTS {MART_TABLE} (
            user_id String,
            client_id String,
            prosthesis_id String,
            report_date Date,
            total_activations UInt64,
            avg_response_time_ms Float64,
            max_response_time_ms Float64,
            min_response_time_ms Float64,
            avg_battery_level Float64,
            total_usage_minutes UInt64
        ) ENGINE = MergeTree()
        ORDER BY (user_id, report_date)
        """
    )
    client.command(
        f"""
        CREATE TABLE IF NOT EXISTS {TELEMETRY_STAGING_TABLE} (
            user_id String,
            prosthesis_id String,
            event_date Date,
            activation_count UInt64,
            response_time_ms UInt64,
            battery_level Float64,
            usage_minutes UInt64
        ) ENGINE = MergeTree()
        ORDER BY (user_id, event_date)
        """
    )
    client.command(
        f"""
        CREATE TABLE IF NOT EXISTS {CRM_STAGING_TABLE} (
            client_id String,
            user_id String,
            full_name String,
            prosthesis_model String,
            country String,
            contract_date Date
        ) ENGINE = MergeTree()
        ORDER BY (user_id)
        """
    )


def extract_telemetry(**context):
    """Извлекает телеметрию протезов из PostgreSQL за execution_date."""
    ds = context["ds"]
    pg_hook = PostgresHook(postgres_conn_id=POSTGRES_CONN_ID)
    rows = pg_hook.get_records(
        sql="""
            SELECT user_id, prosthesis_id, event_time, activation_count,
                   response_time_ms, battery_level, usage_minutes
            FROM prosthesis_telemetry
            WHERE event_time::date = %s
        """,
        parameters=(ds,),
    )

    client = _clickhouse_client()
    # Идемпотентность: очищаем staging за обрабатываемую дату
    client.command(
        f"ALTER TABLE {TELEMETRY_STAGING_TABLE} DELETE WHERE event_date = '{ds}'"
    )

    if not rows:
        return

    client.insert(
        TELEMETRY_STAGING_TABLE,
        [
            (
                row[0],
                row[1],
                row[2].date() if hasattr(row[2], "date") else row[2],
                row[3],
                row[4],
                row[5],
                row[6],
            )
            for row in rows
        ],
        column_names=[
            "user_id",
            "prosthesis_id",
            "event_date",
            "activation_count",
            "response_time_ms",
            "battery_level",
            "usage_minutes",
        ],
    )


def extract_crm(**context):
    """Извлекает справочник клиентов из CRM (PostgreSQL) в staging."""
    pg_hook = PostgresHook(postgres_conn_id=POSTGRES_CONN_ID)
    rows = pg_hook.get_records(
        sql="""
            SELECT client_id, user_id, full_name, prosthesis_model, country, contract_date
            FROM crm_clients
        """
    )

    client = _clickhouse_client()
    # Идемпотентность: полная перезагрузка справочника
    client.command(f"TRUNCATE TABLE {CRM_STAGING_TABLE}")

    if not rows:
        return

    client.insert(
        CRM_STAGING_TABLE,
        [
            (
                row[0],
                row[1],
                row[2],
                row[3],
                row[4],
                row[5].date() if hasattr(row[5], "date") else row[5],
            )
            for row in rows
        ],
        column_names=[
            "client_id",
            "user_id",
            "full_name",
            "prosthesis_model",
            "country",
            "contract_date",
        ],
    )


def load_reports_mart(**context):
    """
    Формирует витрину отчётности: агрегирует телеметрию в разрезе клиентов
    и загружает результат в ClickHouse за execution_date.
    """
    ds = context["ds"]
    client = _clickhouse_client()

    # Идемпотентность: очищаем витрину за обрабатываемую дату
    client.command(f"ALTER TABLE {MART_TABLE} DELETE WHERE report_date = '{ds}'")

    client.command(
        f"""
        INSERT INTO {MART_TABLE}
        SELECT
            t.user_id,
            c.client_id,
            t.prosthesis_id,
            t.event_date AS report_date,
            sum(t.activation_count) AS total_activations,
            avg(t.response_time_ms) AS avg_response_time_ms,
            max(t.response_time_ms) AS max_response_time_ms,
            min(t.response_time_ms) AS min_response_time_ms,
            avg(t.battery_level) AS avg_battery_level,
            sum(t.usage_minutes) AS total_usage_minutes
        FROM {TELEMETRY_STAGING_TABLE} AS t
        INNER JOIN {CRM_STAGING_TABLE} AS c ON c.user_id = t.user_id
        WHERE t.event_date = '{ds}'
        GROUP BY t.user_id, c.client_id, t.prosthesis_id, t.event_date
        """
    )


with DAG(
    dag_id="reports_etl_dag",
    description="ETL: подготовка витрины отчётности BionicPRO в ClickHouse",
    default_args=DEFAULT_ARGS,
    schedule_interval="0 3 * * *",
    start_date=datetime(2024, 1, 1),
    catchup=False,
    max_active_runs=1,
    tags=["bionicpro", "reports", "etl"],
) as dag:

    create_tables_task = PythonOperator(
        task_id="create_tables",
        python_callable=create_tables,
    )

    extract_telemetry_task = PythonOperator(
        task_id="extract_telemetry",
        python_callable=extract_telemetry,
    )

    extract_crm_task = PythonOperator(
        task_id="extract_crm",
        python_callable=extract_crm,
    )

    load_reports_mart_task = PythonOperator(
        task_id="load_reports_mart",
        python_callable=load_reports_mart,
    )

    create_tables_task >> [extract_telemetry_task, extract_crm_task] >> load_reports_mart_task