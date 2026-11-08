// Microservicio de SMS de Nearby sobre SMS Gateway for Android (sms-gate.app).
//
//   Backend Nearby ──POST /v1/messages (X-Api-Key)──► este servicio ──► sms-gate.app ──► celular Android ──► SMS
//   SMS entrante ──► celular ──► sms-gate.app ──POST /webhook (firmado)──► este servicio ──► backend /api/v1/sms/inbound
//
// Sin dependencias: Node 18+ (fetch y crypto incluidos). Ejecuta: node server.js
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');


// ---------------------------------------------------------------- configuración (.env)
function loadEnv(file) {
  if (!fs.existsSync(file)) return;
  for (const raw of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const i = line.indexOf('=');
    if (i < 0) continue;
    const key = line.slice(0, i).trim();
    const value = line.slice(i + 1).trim().replace(/^["']|["']$/g, '');
    if (!(key in process.env)) process.env[key] = value;
  }
}
loadEnv(path.join(__dirname, '.env'));

const API = (process.env.SMSGATE_URL || 'https://api.sms-gate.app/3rdparty/v1').replace(/\/$/, '');
const USER = process.env.SMSGATE_USERNAME || '';
const PASS = process.env.SMSGATE_PASSWORD || '';
const SIGNING_KEY = process.env.SMSGATE_SIGNING_KEY || '';
const PORT = Number(process.env.PORT || 3000);
// Clave que usa el backend para pedir envíos (header X-Api-Key).
const SERVICE_API_KEY = process.env.SERVICE_API_KEY || '';
// A dónde se reenvían los SMS recibidos (backend Nearby) y con qué token (X-Nearby-Token).
const FORWARD_URL = process.env.FORWARD_URL || '';
const FORWARD_TOKEN = process.env.FORWARD_TOKEN || '';
// Página de prueba en / y sus rutas /api/*. Solo responde en esta PC (localhost): por un túnel
// (devtunnels, ngrok) no se ve, para que nadie mande SMS sin la clave. false la apaga del todo.
const DEMO_UI = (process.env.DEMO_UI || 'true') !== 'false';
const AUTH = 'Basic ' + Buffer.from(`${USER}:${PASS}`).toString('base64');

if (!USER || !PASS) {
  console.error('Falta SMSGATE_USERNAME / SMSGATE_PASSWORD en .env (cópialos de la pestaña Home de la app).');
  process.exit(1);
}

// ---------------------------------------------------------------- estado en memoria
const sent = []; // { id, to, text, at, state, recipients, source }
const inbox = []; // { from, text, at, verified, sos, forwarded }

// Mismo formato que usa Nearby para el SOS sin internet (backend: SosCodec).
const SOS_CODE = /NB#SOS![A-Z]\d@[A-Z0-9]+\$P:(ALTA|MEDIA)/i;

// ---------------------------------------------------------------- sms-gate.app
async function gate(method, url, body) {
  const res = await fetch(API + url, {
    method,
    headers: { Authorization: AUTH, 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let data;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!res.ok) {
    const msg = (data && data.message) || text || res.statusText;
    const err = new Error(`sms-gate respondió ${res.status}: ${msg}`);
    err.status = res.status;
    throw err;
  }
  return data;
}

/** Celular peruano de 9 dígitos -> +51XXXXXXXXX. Acepta también números con +. */
function toE164(phone) {
  const s = String(phone || '').trim();
  const digits = s.replace(/\D/g, '');
  if (s.startsWith('+')) return digits.length >= 8 ? '+' + digits : null;
  if (digits.length === 9 && digits.startsWith('9')) return '+51' + digits;
  if (digits.length === 11 && digits.startsWith('51')) return '+' + digits;
  return null;
}

const mask = (p) => String(p || '').slice(0, 6) + '***';

/** Envía un SMS por el celular. Lanza un error con status 400 si los datos no sirven. */
async function sendSms(to, text, source) {
  const phone = toE164(to);
  if (!phone) throw Object.assign(new Error('Celular inválido: 9 dígitos que empiecen con 9, o con +código.'), { status: 400 });
  if (!text || !String(text).trim()) throw Object.assign(new Error('El mensaje está vacío.'), { status: 400 });

  // Evita el rechazo por filtros automáticos de los operadores en Perú (anti-smishing)
  let safeText = String(text)
    .replace(/Tu c[oó]digo Nearby es (\d{6})\. No lo compartas\./gi, 'Nearby - TU C0DIG0 ES: $1')
    .replace(/c[oó]digo/gi, 'C0DIG0')
    .replace(/No lo compartas/gi, 'Uso personal');

  if (safeText.length > 160) throw Object.assign(new Error('Máximo 160 caracteres por SMS.'), { status: 400 });
  const r = await gate('POST', '/messages', { phoneNumbers: [phone], textMessage: { text: safeText } });
  const item = {
    id: r.id, to: phone, text: safeText, at: new Date().toISOString(),
    state: r.state || 'Pending', recipients: r.recipients || [], source,
  };
  sent.unshift(item);
  sent.length = Math.min(sent.length, 200);
  console.log(`[enviado:${source}] ${mask(phone)} -> id ${r.id}`);
  return item;
}

/** Reenvía un SMS recibido al backend (POST /api/v1/sms/inbound con X-Nearby-Token). */
async function forward(from, body) {
  if (!FORWARD_URL) return 'sin FORWARD_URL';
  try {
    const res = await fetch(FORWARD_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Nearby-Token': FORWARD_TOKEN },
      body: JSON.stringify({ from, body }),
    });
    const text = await res.text();
    if (!res.ok) return `backend ${res.status}`;
    const data = JSON.parse(text || '{}');
    return data.reportCreated ? 'reporte creado' : 'recibido';
  } catch (err) {
    return 'backend no responde';
  }
}

// ---------------------------------------------------------------- utilidades HTTP
function json(res, status, data) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(data));
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (c) => {
      size += c.length;
      if (size > 64 * 1024) { reject(Object.assign(new Error('Cuerpo demasiado grande'), { status: 413 })); req.destroy(); }
      else chunks.push(c);
    });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

/** Petición hecha en esta misma PC, no a través de un túnel o proxy. */
function isLocal(req) {
  const host = String(req.headers.host || '');
  const viaProxy = req.headers['x-forwarded-for'] || req.headers['x-forwarded-host'] || req.headers['x-forwarded-proto']
    || req.headers['forwarded'];
  return !viaProxy && /^(localhost|127\.0\.0\.1|\[::1\])(:\d+)?$/i.test(host);
}

function sameSecret(a, b) {
  const x = Buffer.from(String(a || ''));
  const y = Buffer.from(String(b || ''));
  return x.length > 0 && x.length === y.length && crypto.timingSafeEqual(x, y);
}

/** Firma del webhook de sms-gate: HMAC-SHA256(clave, cuerpo crudo + X-Timestamp) en hex. */
function verifySignature(raw, timestamp, signature) {
  if (!SIGNING_KEY) return null; // sin clave configurada: no se verifica
  if (!timestamp || !signature) return false;
  if (Math.abs(Date.now() / 1000 - Number(timestamp)) > 300) return false; // ±5 min
  const expected = crypto.createHmac('sha256', SIGNING_KEY).update(raw + timestamp).digest('hex');
  return sameSecret(expected, String(signature).toLowerCase());
}

// ---------------------------------------------------------------- rutas
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  try {
    // ----- Salud (para el backend o el hosting)
    if (req.method === 'GET' && url.pathname === '/health') {
      return json(res, 200, { status: 'UP', forward: Boolean(FORWARD_URL), signedWebhook: Boolean(SIGNING_KEY) });
    }

    // ----- API de servicio (la consume el backend). Requiere X-Api-Key.
    if (url.pathname.startsWith('/v1/')) {
      if (!SERVICE_API_KEY || !sameSecret(req.headers['x-api-key'], SERVICE_API_KEY)) {
        return json(res, 401, { error: 'X-Api-Key inválida' });
      }
      if (req.method === 'POST' && url.pathname === '/v1/messages') {
        const { to, text } = JSON.parse((await readBody(req)) || '{}');
        const item = await sendSms(to, text, 'backend');
        return json(res, 202, { id: item.id, state: item.state });
      }
      const st = url.pathname.match(/^\/v1\/messages\/([\w-]+)$/);
      if (req.method === 'GET' && st) {
        const r = await gate('GET', `/messages/${st[1]}`);
        return json(res, 200, { id: r.id, state: r.state, recipients: (r.recipients || []).map((x) => ({ state: x.state, error: x.error })) });
      }
      return json(res, 404, { error: 'No encontrado' });
    }

    // ----- Webhook: sms-gate avisa aquí cuando el celular recibe un SMS
    if (req.method === 'POST' && url.pathname === '/webhook') {
      const raw = await readBody(req);
      const verified = verifySignature(raw, req.headers['x-timestamp'], req.headers['x-signature']);
      if (verified === false) {
        console.warn('[webhook] firma inválida, se ignora');
        return json(res, 401, { error: 'firma inválida' });
      }
      const evt = JSON.parse(raw || '{}');
      if (evt.event === 'sms:received' && evt.payload) {
        const from = evt.payload.sender || evt.payload.phoneNumber || '';
        const text = evt.payload.message || '';
        const sos = text.match(SOS_CODE);
        const item = { from, text, at: evt.payload.receivedAt || new Date().toISOString(), verified: verified === true, sos: sos ? sos[0].toUpperCase() : null, forwarded: '…' };
        inbox.unshift(item);
        inbox.length = Math.min(inbox.length, 200);
        item.forwarded = await forward(from, text);
        console.log(`[recibido] ${mask(from)} ${sos ? '(código SOS) ' : ''}-> ${item.forwarded}`);
      }
      return json(res, 200, { ok: true });
    }

    // ----- Página de prueba y sus rutas (solo si DEMO_UI=true y desde esta PC)
    if (DEMO_UI && isLocal(req)) {
      if (req.method === 'GET' && url.pathname === '/') {
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        return res.end(fs.readFileSync(path.join(__dirname, 'public', 'index.html')));
      }
      if (req.method === 'GET' && url.pathname === '/api/device') {
        const devices = await gate('GET', '/devices');
        return json(res, 200, devices.map((d) => ({ id: d.id, name: d.name, lastSeen: d.lastSeen })));
      }
      if (req.method === 'POST' && url.pathname === '/api/send') {
        const { to, text } = JSON.parse((await readBody(req)) || '{}');
        return json(res, 202, await sendSms(to, text, 'demo'));
      }
      const m = url.pathname.match(/^\/api\/status\/([\w-]+)$/);
      if (req.method === 'GET' && m) {
        const r = await gate('GET', `/messages/${m[1]}`);
        const item = sent.find((s) => s.id === m[1]);
        if (item) { item.state = r.state; item.recipients = r.recipients || []; }
        return json(res, 200, r);
      }
      if (req.method === 'GET' && url.pathname === '/api/sent') return json(res, 200, sent);
      if (req.method === 'GET' && url.pathname === '/api/inbox') return json(res, 200, inbox);
      if (req.method === 'GET' && url.pathname === '/api/webhooks') return json(res, 200, await gate('GET', '/webhooks'));
      if (req.method === 'POST' && url.pathname === '/api/webhooks') {
        const { url: publicUrl } = JSON.parse((await readBody(req)) || '{}');
        if (!/^https:\/\//.test(publicUrl || '')) return json(res, 400, { error: 'La URL pública debe empezar con https:// (ngrok o devtunnels).' });
        const target = publicUrl.replace(/\/$/, '') + (publicUrl.endsWith('/webhook') ? '' : '/webhook');
        return json(res, 201, await gate('POST', '/webhooks', { url: target, event: 'sms:received' }));
      }
      const wd = url.pathname.match(/^\/api\/webhooks\/([\w-]+)$/);
      if (req.method === 'DELETE' && wd) {
        await gate('DELETE', `/webhooks/${wd[1]}`);
        return json(res, 200, { deleted: wd[1] });
      }
    }

    json(res, 404, { error: 'No encontrado' });
  } catch (err) {
    console.error('[error]', err.message);
    const status = err.status && err.status < 500 ? (err.status === 413 ? 413 : 400) : 502;
    json(res, status, { error: err.message });
  }
});

server.listen(PORT, () => {
  console.log(`Microservicio SMS listo: http://localhost:${PORT}${DEMO_UI ? '  (página de prueba activa)' : ''}`);
  console.log(`sms-gate: ${USER} · firma webhook: ${SIGNING_KEY ? 'sí' : 'no'} · API backend: ${SERVICE_API_KEY ? 'sí' : 'FALTA SERVICE_API_KEY'} · reenvío: ${FORWARD_URL || 'no configurado'}`);
});
