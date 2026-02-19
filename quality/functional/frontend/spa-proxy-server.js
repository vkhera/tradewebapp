/**
 * Minimal SPA + API proxy server for Playwright tests.
 *
 * - Serves Angular static files from the dist folder.
 * - Falls back to index.html for any path that isn't a real file
 *   (standard SPA behaviour so Angular Router can handle all routes).
 * - Proxies /api/** and /actuator/** to the Spring Boot backend so the
 *   Playwright tests get real API responses without CORS issues.
 *
 * Usage:
 *   node spa-proxy-server.js [port] [distDir] [backendBase]
 *
 * Defaults:
 *   port        = 4201
 *   distDir     = ../../frontend/dist/stock-brokerage-ui/browser  (relative to this file)
 *   backendBase = http://localhost:8080
 */

'use strict';

const http  = require('http');
const fs    = require('fs');
const path  = require('path');
const url   = require('url');

const PORT        = parseInt(process.argv[2] || '4201', 10);
const DIST        = path.resolve(__dirname, process.argv[3] || '../../../frontend/dist/stock-brokerage-ui/browser');
const BACKEND     = process.argv[4] || 'http://localhost:8080';
const backendUrl  = url.parse(BACKEND);

const MIME = {
  '.html': 'text/html',
  '.js':   'application/javascript',
  '.mjs':  'application/javascript',
  '.css':  'text/css',
  '.json': 'application/json',
  '.png':  'image/png',
  '.jpg':  'image/jpeg',
  '.svg':  'image/svg+xml',
  '.ico':  'image/x-icon',
  '.woff': 'font/woff',
  '.woff2':'font/woff2',
  '.ttf':  'font/ttf',
  '.txt':  'text/plain',
};

function proxyToBackend(req, res) {
  const opts = {
    hostname: backendUrl.hostname,
    port:     backendUrl.port || 8080,
    path:     req.url,
    method:   req.method,
    headers:  { ...req.headers, host: `${backendUrl.hostname}:${backendUrl.port || 8080}` },
  };

  const proxy = http.request(opts, (backendRes) => {
    res.writeHead(backendRes.statusCode, backendRes.headers);
    backendRes.pipe(res);
  });

  proxy.on('error', (err) => {
    console.error('[proxy] backend error:', err.message);
    res.writeHead(502);
    res.end(`Backend unavailable: ${err.message}`);
  });

  req.pipe(proxy);
}

function serveFile(filePath, res) {
  const ext  = path.extname(filePath).toLowerCase();
  const mime = MIME[ext] || 'application/octet-stream';

  fs.readFile(filePath, (err, data) => {
    if (err) {
      // Fallback to index.html for SPA routing
      const indexPath = path.join(DIST, 'index.html');
      fs.readFile(indexPath, (err2, html) => {
        if (err2) {
          res.writeHead(500);
          res.end('index.html not found');
          return;
        }
        res.writeHead(200, { 'Content-Type': 'text/html' });
        res.end(html);
      });
      return;
    }
    res.writeHead(200, { 'Content-Type': mime });
    res.end(data);
  });
}

const server = http.createServer((req, res) => {
  const reqPath = url.parse(req.url).pathname;

  // Proxy API and actuator requests to Spring Boot backend
  if (reqPath.startsWith('/api/') || reqPath.startsWith('/actuator/')) {
    return proxyToBackend(req, res);
  }

  // Serve static files; fall back to index.html for SPA routes
  let filePath = path.join(DIST, reqPath === '/' ? 'index.html' : reqPath);

  // Prevent path traversal
  if (!filePath.startsWith(DIST)) {
    res.writeHead(403);
    res.end('Forbidden');
    return;
  }

  fs.stat(filePath, (err, stat) => {
    if (!err && stat.isDirectory()) {
      filePath = path.join(filePath, 'index.html');
    }
    serveFile(filePath, res);
  });
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`[spa-proxy-server] Serving ${DIST}`);
  console.log(`[spa-proxy-server] Proxy  /api/** → ${BACKEND}`);
  console.log(`[spa-proxy-server] Listening on http://127.0.0.1:${PORT}`);
});

process.on('SIGTERM', () => server.close());
process.on('SIGINT',  () => server.close());
