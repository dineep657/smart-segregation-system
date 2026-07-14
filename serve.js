const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 3000;
const ROOT = __dirname;

const MIME = {
  '.html': 'text/html',
  '.css':  'text/css',
  '.js':   'application/javascript',
  '.json': 'application/json',
  '.png':  'image/png',
  '.jpg':  'image/jpeg',
  '.svg':  'image/svg+xml',
  '.ico':  'image/x-icon',
};

const server = http.createServer((req, res) => {
  let urlPath = req.url.split('?')[0];
  if (urlPath === '/') urlPath = '/app.html';

  const filePath = path.join(ROOT, urlPath);

  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('404 Not Found: ' + urlPath);
      return;
    }
    const ext = path.extname(filePath).toLowerCase();
    res.writeHead(200, { 'Content-Type': MIME[ext] || 'text/plain' });
    res.end(data);
  });
});

server.listen(PORT, () => {
  console.log('');
  console.log('  ✅  SmartSeg server running!');
  console.log('');
  console.log('  Open in browser:');
  console.log('  → http://localhost:' + PORT + '/app.html          (unified app)');
  console.log('  → http://localhost:' + PORT + '/login.html        (login page)');
  console.log('  → http://localhost:' + PORT + '/dashboard.html    (dashboard)');
  console.log('');
  console.log('  Demo credentials:');
  console.log('  → admin / segregate2025');
  console.log('  → operator / greenbin');
  console.log('');
});
