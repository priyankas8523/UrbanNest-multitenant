#!/bin/bash
# Creates the keycloak_db database inside the same PostgreSQL instance.
# This script runs ONCE on first container startup (docker-entrypoint-initdb.d/).
# The primary database (multitenant_db) is created automatically by POSTGRES_DB env var.

set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE keycloak_db;
EOSQL
