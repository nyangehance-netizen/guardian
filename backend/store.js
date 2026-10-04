'use strict';

/*
 * Tiny JSON-file data store + crypto helpers — no external dependencies.
 * Fine for development and small family deployments. For production, swap this
 * module for a real database (Postgres, SQLite via node:sqlite, etc.); the rest
 * of the server only touches the exported helpers below.
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const DB_FILE = path.join(__dirname, 'guardian-data.json');

const blank = { parents: [], devices: [], heartbeats: [], events: [] };
let data;

function load() {
  try { data = JSON.parse(fs.readFileSync(DB_FILE, 'utf8')); }
  catch { data = structuredClone(blank); }
  for (const k of Object.keys(blank)) if (!data[k]) data[k] = [];
}
let saveQueued = false;
function save() {
  if (saveQueued) return;
  saveQueued = true;
  setImmediate(() => {
    saveQueued = false;
    fs.writeFileSync(DB_FILE, JSON.stringify(data, null, 2));
  });
}
load();

/* ----------------------------- id + crypto ------------------------------- */
const id = () => crypto.randomBytes(12).toString('hex');
const secret = () => crypto.randomBytes(30).toString('hex');
const sixDigit = () => ('' + Math.floor(100000 + Math.random() * 900000));

// Password hashing with scrypt (built in). Format: scrypt$<saltHex>$<hashHex>
function hashPassword(pw) {
  const salt = crypto.randomBytes(16);
  const hash = crypto.scryptSync(pw, salt, 64);
  return `scrypt$${salt.toString('hex')}$${hash.toString('hex')}`;
}
function verifyPassword(pw, stored) {
  try {
    const [, saltHex, hashHex] = stored.split('$');
    const hash = crypto.scryptSync(pw, Buffer.from(saltHex, 'hex'), 64);
    return crypto.timingSafeEqual(hash, Buffer.from(hashHex, 'hex'));
  } catch { return false; }
}

// Minimal signed token (HMAC). payload is base64url(JSON).signature
const TOKEN_KEY = process.env.TOKEN_KEY || 'dev-token-key-change-me';
function signToken(payload) {
  const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
  const sig = crypto.createHmac('sha256', TOKEN_KEY).update(body).digest('base64url');
  return `${body}.${sig}`;
}
function verifyToken(token) {
  if (!token || !token.includes('.')) return null;
  const [body, sig] = token.split('.');
  const expect = crypto.createHmac('sha256', TOKEN_KEY).update(body).digest('base64url');
  if (sig.length !== expect.length || !crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(expect))) return null;
  try {
    const payload = JSON.parse(Buffer.from(body, 'base64url').toString());
    if (payload.exp && Date.now() > payload.exp) return null;
    return payload;
  } catch { return null; }
}

module.exports = {
  data, save, id, secret, sixDigit,
  hashPassword, verifyPassword, signToken, verifyToken,
};
