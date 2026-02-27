#!/bin/sh
# docker-entrypoint-frontend.sh
# Generates a self-signed TLS certificate if no certificate is present in /etc/nginx/ssl/
# For production: mount real Let's Encrypt certs into /etc/nginx/ssl/ (cert.pem + key.pem)
# and the self-signed generation is skipped automatically.

SSL_DIR="/etc/nginx/ssl"
CERT="$SSL_DIR/cert.pem"
KEY="$SSL_DIR/key.pem"
CERTBOT_WEBROOT="/var/www/certbot"

mkdir -p "$SSL_DIR" "$CERTBOT_WEBROOT"

if [ ! -f "$CERT" ] || [ ! -f "$KEY" ]; then
    echo "[entrypoint] No TLS certificate found – generating self-signed cert for $SSL_DIR"
    DOMAIN="${DOMAIN_NAME:-localhost}"
    openssl req -x509 -nodes -days 365 \
        -newkey rsa:2048 \
        -keyout "$KEY" \
        -out "$CERT" \
        -subj "/C=US/ST=NY/L=NewYork/O=StockBrokerage/CN=$DOMAIN" \
        2>/dev/null
    echo "[entrypoint] Self-signed cert generated for CN=$DOMAIN"
    echo "[entrypoint] Replace with Let's Encrypt certs by mounting them into $SSL_DIR"
else
    echo "[entrypoint] TLS certificate found at $CERT – skipping generation"
fi

# Hand off to nginx
exec nginx -g 'daemon off;'
