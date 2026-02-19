#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
#  All-in-one container entrypoint
#  Runs once to initialise PostgreSQL & create the application database,
#  then hands control to supervisord which manages all 4 processes.
# ─────────────────────────────────────────────────────────────────────────────
set -e

PG_DATA="/var/lib/postgresql/16/main"
MARKER="$PG_DATA/.stockdb_initialized"

# ── 1. Initialise PostgreSQL cluster (Ubuntu ships with one pre-created) ─────
# Ensure ownership is correct whether the directory came from a volume or not
chown -R postgres:postgres /var/lib/postgresql 2>/dev/null || true

if [ ! -f "$MARKER" ]; then
    echo "[entrypoint] First boot — configuring PostgreSQL..."

    # Allow password auth from localhost (needed by Spring Boot)
    # Ubuntu's default pg_hba.conf uses peer auth for local connections;
    # replace the local rule so our JDBC URL (password) can authenticate.
    PG_HBA="/etc/postgresql/16/main/pg_hba.conf"
    # Replace peer/ident entries with md5 for local connections
    sed -i 's/local\s\+all\s\+all\s\+peer/local   all             all                                     md5/' "$PG_HBA"
    sed -i 's/local\s\+all\s\+postgres\s\+peer/local   all             postgres                                md5/' "$PG_HBA"
    # Ensure 127.0.0.1 connections use md5
    sed -i 's/host\s\+all\s\+all\s\+127\.0\.0\.1\/32\s\+scram-sha-256/host    all             all             127.0.0.1\/32            md5/' "$PG_HBA"

    # Start postgres temporarily to create user + db
    su postgres -c "/usr/lib/postgresql/16/bin/pg_ctl \
        -D $PG_DATA \
        -l /tmp/pg_init.log \
        -o '-c listen_addresses=localhost' \
        start"

    # Wait until postgres is accepting connections
    for i in $(seq 1 30); do
        su postgres -c "pg_isready -q" && break
        sleep 1
    done

    # Create application user and database
    su postgres -c "psql -v ON_ERROR_STOP=1 <<-EOSQL
        DO \$\$
        BEGIN
            IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'stockuser') THEN
                CREATE USER stockuser WITH PASSWORD 'stockpass';
            END IF;
        END
        \$\$;
        SELECT 'CREATE DATABASE stockdb'
        WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'stockdb') \gexec
        GRANT ALL PRIVILEGES ON DATABASE stockdb TO stockuser;
EOSQL"

    # Stop the temporary postgres instance
    su postgres -c "/usr/lib/postgresql/16/bin/pg_ctl -D $PG_DATA stop -m fast"

    touch "$MARKER"
    echo "[entrypoint] PostgreSQL initialised."
else
    echo "[entrypoint] PostgreSQL already initialised, skipping."
fi

# ── 2. Hand off to supervisord ────────────────────────────────────────────────
echo "[entrypoint] Starting supervisord..."
exec /usr/bin/supervisord -c /etc/supervisor/conf.d/supervisord.conf
