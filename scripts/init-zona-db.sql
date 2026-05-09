-- Роль и база для приложения Zona (совпадают с дефолтами сервера).
-- Запуск от суперпользователя PostgreSQL, например: psql -U postgres -d postgres -f init-zona-db.sql

\set ON_ERROR_STOP on

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'zona') THEN
    CREATE ROLE zona WITH LOGIN PASSWORD 'zona';
  ELSE
    ALTER ROLE zona WITH LOGIN PASSWORD 'zona';
  END IF;
END
$$;

SELECT format(
    'CREATE DATABASE %I OWNER zona TEMPLATE template0 ENCODING ''UTF8''',
    'zona'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'zona')
\gexec
