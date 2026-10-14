-- Creates the login role Grafana uses to query the wallet database.
-- It runs from a one-shot compose container, not from Flyway, so monitoring
-- never leaks into the application schema. Safe to run on every start.
\getenv grafana_password WALLET_GRAFANA_DB_PASSWORD
\set ON_ERROR_STOP on

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'grafana_reader') THEN
        CREATE ROLE grafana_reader LOGIN;
    END IF;
END
$$;

-- Applied on every run, so rotating the password needs no other step.
ALTER ROLE grafana_reader LOGIN PASSWORD :'grafana_password';

-- The role can only read: CONNECT plus SELECT on the application tables,
-- including tables created after this script ran. The wallet owner's trigger
-- keeps ledger_entry immutable regardless of who writes.
GRANT CONNECT ON DATABASE :"DBNAME" TO grafana_reader;
GRANT USAGE ON SCHEMA public TO grafana_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO grafana_reader;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO grafana_reader;
