const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { execFile, exec } = require('child_process');

const PORT = 3000;
const ADB_PATH = 'C:\\Users\\Ashok\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe';
const PUBLIC_DIR = path.resolve(__dirname, 'public');
const UPLOAD_DIR = path.resolve(__dirname, 'temp_uploads');
const MAX_BODY_SIZE = 64 * 1024; // 64 KB maximum JSON payload limit
const MAX_UPLOAD_SIZE = 2 * 1024 * 1024; // 2 MB maximum file upload limit

// Ensure isolated temp upload directory exists
if (!fs.existsSync(UPLOAD_DIR)) {
    fs.mkdirSync(UPLOAD_DIR, { recursive: true });
}

let latestFrame = null;
let isCapturing = false;
let lastCaptureTime = 0;
const pendingCallbacks = [];

function captureScreen(callback) {
    if (callback) pendingCallbacks.push(callback);
    if (isCapturing) return;

    isCapturing = true;
    execFile(ADB_PATH, ['exec-out', 'screencap', '-p'], { encoding: 'buffer', maxBuffer: 10 * 1024 * 1024, timeout: 5000 }, (err, stdout) => {
        isCapturing = false;
        lastCaptureTime = Date.now();
        if (!err && stdout && stdout.length > 100) {
            latestFrame = stdout;
        }
        const callbacks = pendingCallbacks.splice(0, pendingCallbacks.length);
        for (const cb of callbacks) {
            try { cb(latestFrame); } catch (e) {}
        }
    });
}

// Initial capture
captureScreen();

function runAdb(args, callback) {
    execFile(ADB_PATH, args, { timeout: 3000 }, (err, stdout, stderr) => {
        if (callback) callback(err, stdout ? stdout.toString() : '', stderr ? stderr.toString() : '');
    });
}

// =============================================================================
// RATE LIMITING & SECURITY DEFENSES (Token Bucket + Cooldown + Proxy Validation)
// =============================================================================
const IS_PROD = process.env.NODE_ENV === 'production';
const ALLOWED_ORIGINS = (process.env.ALLOWED_ORIGINS || 'http://127.0.0.1:3000,http://localhost:3000').split(',').map(s => s.trim()).filter(Boolean);
const ADMIN_KEY = process.env.ADMIN_KEY || 'towerchat-admin-secret';
const rateLimitStores = new Map();

// Configure trusted reverse proxy IP list (empty by default for local execution)
const TRUSTED_PROXIES = (process.env.TRUSTED_PROXIES || '').split(',').map(s => s.trim()).filter(Boolean);

/**
 * Safely resolves client IP address.
 * Prevents IP spoofing: only inspects X-Forwarded-For if socket IP matches a trusted proxy.
 */
function getClientIp(req) {
    const socketIp = req.socket.remoteAddress || '127.0.0.1';
    const xForwardedFor = req.headers['x-forwarded-for'];
    if (xForwardedFor && TRUSTED_PROXIES.includes(socketIp)) {
        const ips = xForwardedFor.split(',').map(s => s.trim());
        return ips[0] || socketIp;
    }
    return socketIp;
}

/**
 * Server-side Token Bucket Rate Limiter
 * Supports per-route limits, burst limits, refill rates, and temporary lockout cooldowns.
 */
function checkRateLimit(req, res, options) {
    const {
        windowMs = 60000,
        max = 10,
        burst = max,
        cooldownMs = 0,
        keyPrefix = 'general',
        customKey = null
    } = options;

    const ip = getClientIp(req);
    const key = customKey ? `${keyPrefix}:${customKey}` : `${keyPrefix}:${ip}`;
    const now = Date.now();

    let record = rateLimitStores.get(key);
    if (!record) {
        record = {
            tokens: burst,
            lastRefill: now,
            lockUntil: 0,
            violations: 0
        };
        rateLimitStores.set(key, record);
    }

    // 1. Check temporary lockout status
    if (now < record.lockUntil) {
        const retryAfter = Math.max(1, Math.ceil((record.lockUntil - now) / 1000));
        res.writeHead(429, {
            'Content-Type': 'application/json',
            'Retry-After': retryAfter.toString(),
            'RateLimit-Limit': max.toString(),
            'RateLimit-Remaining': '0',
            'RateLimit-Reset': retryAfter.toString()
        });
        res.end(JSON.stringify({
            error: 'Too Many Requests',
            message: `Rate limit abuse detected. Temporary lockout active for ${retryAfter}s.`,
            retryAfter
        }));
        return false;
    }

    // 2. Replenish tokens based on time elapsed
    const elapsed = now - record.lastRefill;
    const tokensToAdd = elapsed * (max / windowMs);
    record.tokens = Math.min(burst, record.tokens + tokensToAdd);
    record.lastRefill = now;

    // 3. Check token availability
    if (record.tokens < 1) {
        record.violations += 1;
        if (cooldownMs > 0 && record.violations >= 3) {
            record.lockUntil = now + cooldownMs;
        }
        const timeToNextToken = Math.ceil(((1 - record.tokens) * (windowMs / max)) / 1000);
        const retryAfter = Math.max(1, timeToNextToken);

        res.writeHead(429, {
            'Content-Type': 'application/json',
            'Retry-After': retryAfter.toString(),
            'RateLimit-Limit': max.toString(),
            'RateLimit-Remaining': '0',
            'RateLimit-Reset': retryAfter.toString()
        });
        res.end(JSON.stringify({
            error: 'Too Many Requests',
            message: `Rate limit exceeded for endpoint. Retry in ${retryAfter}s.`,
            retryAfter
        }));
        return false;
    }

    // 4. Consume token and set informational RFC rate limit headers
    record.tokens -= 1;
    res.setHeader('RateLimit-Limit', max.toString());
    res.setHeader('RateLimit-Remaining', Math.max(0, Math.floor(record.tokens)).toString());
    return true;
}

// =============================================================================
// INPUT VALIDATION & PAYLOAD SANITIZATION HELPERS
// =============================================================================

/**
 * Safely parses JSON request bodies with bounded size limits (DoS prevention).
 * Automatically returns HTTP 413 on oversized payloads and HTTP 400 on malformed syntax.
 */
function readJsonBody(req, res, callback) {
    let body = '';
    let isTooLarge = false;

    req.on('data', chunk => {
        if (isTooLarge) return;
        body += chunk;
        if (body.length > MAX_BODY_SIZE) {
            isTooLarge = true;
            res.writeHead(413, { 'Content-Type': 'application/json', 'Connection': 'close' });
            res.end(JSON.stringify({ error: 'Payload Too Large', message: `Request body exceeds maximum allowed size of ${MAX_BODY_SIZE} bytes` }));
            req.resume();
        }
    });

    req.on('end', () => {
        if (isTooLarge) return;
        if (!body.trim()) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Bad Request', message: 'Request body cannot be empty' }));
            return;
        }
        try {
            const parsed = JSON.parse(body);
            if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Bad Request', message: 'Request payload must be a JSON object' }));
                return;
            }
            callback(parsed);
        } catch (e) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Bad Request', message: 'Invalid JSON format' }));
        }
    });
}

/**
 * Sanitizes Android Logcat output to prevent sensitive data/token leakage.
 */
function sanitizeLogcat(rawLogs) {
    if (!rawLogs) return '';
    return rawLogs
        .replace(/(Bearer\s+)[A-Za-z0-9_\-\.]+/gi, '$1[REDACTED]')
        .replace(/(api[_-]?key\s*[=:]\s*)[A-Za-z0-9_\-]+/gi, '$1[REDACTED]')
        .replace(/(password\s*[=:]\s*)[^\s]+/gi, '$1[REDACTED]')
        .replace(/-----BEGIN[A-Z\s]+PRIVATE KEY-----[^-]+-----END[A-Z\s]+PRIVATE KEY-----/g, '[REDACTED_PRIVATE_KEY]');
}

// =============================================================================
// HTTP REQUEST ROUTER & EXCEPTION BOUNDARY
// =============================================================================

function handleRequest(req, res) {
    const parsedUrl = new URL(req.url, `http://${req.headers.host}`);
    const pathname = parsedUrl.pathname;

    // 29, 34. OWASP Security Headers & XSS Protections
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Frame-Options', 'SAMEORIGIN');
    res.setHeader('X-XSS-Protection', '1; mode=block');
    res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
    res.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self' 'unsafe-inline' https://fonts.googleapis.com; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self'; frame-ancestors 'self';");

    // 30, 35. CORS Protection (Restricted Origins in Production)
    const requestOrigin = req.headers['origin'];
    if (!IS_PROD) {
        res.setHeader('Access-Control-Allow-Origin', requestOrigin || '*');
    } else if (requestOrigin && ALLOWED_ORIGINS.includes(requestOrigin)) {
        res.setHeader('Access-Control-Allow-Origin', requestOrigin);
    } else {
        res.setHeader('Access-Control-Allow-Origin', ALLOWED_ORIGINS[0] || 'http://127.0.0.1:3000');
    }
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-Test-Secret, X-Admin-Key');
    res.setHeader('Access-Control-Expose-Headers', 'RateLimit-Limit, RateLimit-Remaining, RateLimit-Reset, Retry-After');

    if (req.method === 'OPTIONS') {
        res.writeHead(204);
        res.end();
        return;
    }

    // Automated test hook: reset rate limit stores (Protected by TEST_SECRET or non-production environment)
    if (pathname === '/api/test/reset-ratelimit' && req.method === 'POST') {
        const configuredSecret = process.env.TEST_SECRET || 'towerchat-test';
        const clientSecret = req.headers['x-test-secret'];
        const isAllowedMode = (process.env.NODE_ENV !== 'production');

        if (isAllowedMode && clientSecret && clientSecret === configuredSecret) {
            rateLimitStores.clear();
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ ok: true, message: 'Rate limit stores cleared' }));
            return;
        }
        res.writeHead(403, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Forbidden', message: 'Test reset hook disabled or unauthorized' }));
        return;
    }

    // 1. Live Screen Capture Stream (Heavy ADB screencap)
    if (pathname === '/api/screen') {
        if (!checkRateLimit(req, res, { windowMs: 1000, max: 5, burst: 10, keyPrefix: 'screen' })) {
            return;
        }

        const now = Date.now();
        if (latestFrame && (now - lastCaptureTime < 700)) {
            res.writeHead(200, {
                'Content-Type': 'image/png',
                'Content-Length': latestFrame.length,
                'Cache-Control': 'no-store, no-cache, must-revalidate, max-age=0'
            });
            res.end(latestFrame);
        } else {
            captureScreen((frame) => {
                if (frame) {
                    res.writeHead(200, {
                        'Content-Type': 'image/png',
                        'Content-Length': frame.length,
                        'Cache-Control': 'no-store, no-cache, must-revalidate, max-age=0'
                    });
                    res.end(frame);
                } else {
                    res.writeHead(503, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ error: 'Service Unavailable', message: 'Waiting for device screen capture...' }));
                }
            });
        }
        return;
    }

    // 2. Tap Touch Coordinate Injection (Strict Number & Range Validation)
    if (pathname === '/api/tap' && req.method === 'POST') {
        if (!checkRateLimit(req, res, { windowMs: 60000, max: 30, burst: 10, keyPrefix: 'tap' })) {
            return;
        }

        readJsonBody(req, res, (data) => {
            const { x, y } = data;

            if (typeof x !== 'number' || typeof y !== 'number' || !Number.isFinite(x) || !Number.isFinite(y)) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Bad Request', message: 'Parameters x and y must be finite numbers' }));
                return;
            }

            if (x < 0.0 || x > 1.0 || y < 0.0 || y > 1.0) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Bad Request', message: 'Normalized coordinates x and y must be between 0.0 and 1.0' }));
                return;
            }

            const realX = Math.round(x * 1080);
            const realY = Math.round(y * 2400);

            runAdb(['shell', 'input', 'tap', realX.toString(), realY.toString()], (err) => {
                if (err) {
                    console.error('ADB input tap error:', err.message || err);
                    res.writeHead(502, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ error: 'Bad Gateway', message: 'Failed to dispatch touch coordinate to emulator' }));
                    return;
                }
                setTimeout(() => captureScreen(), 150);
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ ok: true, x: realX, y: realY }));
            });
        });
        return;
    }

    // 3. Text Key Input Injection (Type, Length & Sanitization)
    if (pathname === '/api/text' && req.method === 'POST') {
        if (!checkRateLimit(req, res, { windowMs: 60000, max: 20, burst: 5, keyPrefix: 'text' })) {
            return;
        }

        readJsonBody(req, res, (data) => {
            const { text } = data;

            if (typeof text !== 'string' || text.length === 0) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Bad Request', message: 'Parameter text must be a non-empty string' }));
                return;
            }

            if (text.length > 500) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Bad Request', message: 'Parameter text exceeds maximum length of 500 characters' }));
                return;
            }

            const sanitizedText = text.replace(/[\x00-\x1F\x7F]/g, '');
            const formatted = sanitizedText.replace(/ /g, '%s');

            runAdb(['shell', 'input', 'text', formatted], (err) => {
                if (err) {
                    console.error('ADB input text error:', err.message || err);
                    res.writeHead(502, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ error: 'Bad Gateway', message: 'Failed to inject text into emulator' }));
                    return;
                }
                setTimeout(() => captureScreen(), 200);
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ ok: true }));
            });
        });
        return;
    }

    // 4. Key Event Injection (Integer & Android KeyCode Range Validation)
    if (pathname === '/api/key' && req.method === 'POST') {
        if (!checkRateLimit(req, res, { windowMs: 60000, max: 20, burst: 5, keyPrefix: 'key' })) {
            return;
        }

        readJsonBody(req, res, (data) => {
            const { code } = data;

            if (typeof code !== 'number' || !Number.isInteger(code)) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Bad Request', message: 'Parameter code must be an integer' }));
                return;
            }

            if (code < 1 || code > 350) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Bad Request', message: 'Parameter code must be a valid Android KeyCode between 1 and 350' }));
                return;
            }

            runAdb(['shell', 'input', 'keyevent', code.toString()], (err) => {
                if (err) {
                    console.error('ADB input keyevent error:', err.message || err);
                    res.writeHead(502, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ error: 'Bad Gateway', message: 'Failed to dispatch keyevent to emulator' }));
                    return;
                }
                setTimeout(() => captureScreen(), 200);
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ ok: true }));
            });
        });
        return;
    }

    // 5. Incoming SMS / Verification Injection
    if (pathname === '/api/sms' && req.method === 'POST') {
        if (!checkRateLimit(req, res, {
            windowMs: 60000,
            max: 5,
            burst: 2,
            cooldownMs: 300000,
            keyPrefix: 'sms'
        })) {
            return;
        }

        readJsonBody(req, res, (data) => {
            const { sender, message } = data;

            if (typeof sender !== 'string' || typeof message !== 'string') {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Bad Request', message: 'Parameters sender and message must be strings' }));
                return;
            }

            const trimmedSender = sender.trim();
            const isPhoneNumber = /^\+?[0-9]{3,16}$/.test(trimmedSender);
            const isAlphanumericTag = /^[A-Za-z0-9_-]{3,16}$/.test(trimmedSender);
            if (!isPhoneNumber && !isAlphanumericTag) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Bad Request', message: 'Sender must be a valid phone number (3-16 digits) or alphanumeric identifier' }));
                return;
            }

            if (message.length === 0 || message.length > 1600) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Bad Request', message: 'Message length must be between 1 and 1600 characters' }));
                return;
            }

            const sanitizedMessage = message.replace(/[\r\x00-\x08\x0B\x0C\x0E-\x1F]/g, '');

            runAdb(['emu', 'sms', 'send', trimmedSender, sanitizedMessage], (err, stdout, stderr) => {
                if (err) {
                    console.error('ADB emu sms send error:', stderr || err.message);
                    res.writeHead(502, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ error: 'Bad Gateway', message: 'Failed to deliver SMS packet to emulator base station' }));
                    return;
                }
                setTimeout(() => captureScreen(), 300);
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ ok: true, message: 'Carrier SMS delivered successfully' }));
            });
        });
        return;
    }

    // 6. Force-Stop & Restart Application (High Risk - Destructive Admin Action)
    if (pathname === '/api/restart' && req.method === 'POST') {
        // 3, 17, 32. Admin Authentication Check in Production
        if (IS_PROD) {
            const clientAdminKey = req.headers['x-admin-key'];
            if (!clientAdminKey || clientAdminKey !== ADMIN_KEY) {
                res.writeHead(403, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Forbidden', message: 'Admin authentication required in production mode' }));
                return;
            }
        }

        if (!checkRateLimit(req, res, {
            windowMs: 60000,
            max: 2,
            burst: 1,
            cooldownMs: 30000,
            keyPrefix: 'restart'
        })) {
            return;
        }

        runAdb(['shell', 'am', 'force-stop', 'com.towerchat.app.debug'], (err) => {
            if (err) {
                console.error('ADB force-stop error:', err.message);
                res.writeHead(502, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Bad Gateway', message: 'Failed to stop app process' }));
                return;
            }
            setTimeout(() => {
                runAdb(['shell', 'am', 'start', '-n', 'com.towerchat.app.debug/com.towerchat.app.ui.MainActivity'], (startErr) => {
                    if (startErr) {
                        console.error('ADB start error:', startErr.message);
                        res.writeHead(502, { 'Content-Type': 'application/json' });
                        res.end(JSON.stringify({ error: 'Bad Gateway', message: 'Failed to launch app activity' }));
                        return;
                    }
                    setTimeout(() => captureScreen(), 500);
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ ok: true, message: 'App restarted successfully' }));
                });
            }, 300);
        });
        return;
    }

    // 7. RAM Zeroize & Purge State (High Risk - Destructive Admin Action)
    if (pathname === '/api/purge' && req.method === 'POST') {
        // 3, 17, 32. Admin Authentication Check in Production
        if (IS_PROD) {
            const clientAdminKey = req.headers['x-admin-key'];
            if (!clientAdminKey || clientAdminKey !== ADMIN_KEY) {
                res.writeHead(403, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Forbidden', message: 'Admin authentication required in production mode' }));
                return;
            }
        }

        if (!checkRateLimit(req, res, {
            windowMs: 60000,
            max: 1,
            burst: 1,
            cooldownMs: 60000,
            keyPrefix: 'purge'
        })) {
            return;
        }

        runAdb(['shell', 'am', 'force-stop', 'com.towerchat.app.debug'], (err) => {
            if (err) {
                console.error('ADB purge force-stop error:', err.message);
                res.writeHead(502, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Bad Gateway', message: 'Failed to purge memory' }));
                return;
            }
            setTimeout(() => {
                runAdb(['shell', 'am', 'start', '-n', 'com.towerchat.app.debug/com.towerchat.app.ui.MainActivity'], (startErr) => {
                    if (startErr) {
                        console.error('ADB purge restart error:', startErr.message);
                        res.writeHead(502, { 'Content-Type': 'application/json' });
                        res.end(JSON.stringify({ error: 'Bad Gateway', message: 'Failed to re-launch app after purge' }));
                        return;
                    }
                    setTimeout(() => captureScreen(), 500);
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ ok: true, message: 'All RAM memory wiped and app re-launched' }));
                });
            }, 300);
        });
        return;
    }

    // 8. Telephony Logcat Extraction with Sanitization
    if (pathname === '/api/logs') {
        if (!checkRateLimit(req, res, { windowMs: 60000, max: 10, burst: 3, keyPrefix: 'logs' })) {
            return;
        }

        runAdb(['logcat', '-d', '-t', '150'], (err, stdout, stderr) => {
            if (err) {
                console.error('ADB logcat error:', stderr || err.message);
                res.writeHead(502, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Bad Gateway', message: 'Failed to extract device logcat' }));
                return;
            }
            const sanitized = sanitizeLogcat(stdout);
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ logs: sanitized }));
        });
        return;
    }

    // =============================================================================
    // 9. FILE UPLOAD SAFETY & EMULATOR MEDIA DISPATCH
    // =============================================================================
    
    // Helper to validate magic byte signatures
    function getValidatedFileType(buffer) {
        if (!buffer || buffer.length < 8) return null;
        // PNG magic bytes: 89 50 4E 47 0D 0A 1A 0A
        if (buffer[0] === 0x89 && buffer[1] === 0x50 && buffer[2] === 0x4E && buffer[3] === 0x47 &&
            buffer[4] === 0x0D && buffer[5] === 0x0A && buffer[6] === 0x1A && buffer[7] === 0x0A) {
            return { ext: '.png', mime: 'image/png' };
        }
        // JPEG magic bytes: FF D8 FF
        if (buffer[0] === 0xFF && buffer[1] === 0xD8 && buffer[2] === 0xFF) {
            return { ext: '.jpg', mime: 'image/jpeg' };
        }
        return null;
    }

    // 9a. Secure File Upload Endpoint
    if (pathname === '/api/upload' && req.method === 'POST') {
        if (!checkRateLimit(req, res, { windowMs: 60000, max: 10, burst: 3, keyPrefix: 'upload' })) {
            return;
        }

        const contentLength = parseInt(req.headers['content-length'] || '0', 10);
        if (contentLength > MAX_UPLOAD_SIZE) {
            res.writeHead(413, { 'Content-Type': 'application/json', 'Connection': 'close' });
            res.end(JSON.stringify({ error: 'Payload Too Large', message: `File size exceeds 2MB limit` }));
            req.resume();
            return;
        }

        const chunks = [];
        let totalSize = 0;
        let isTooLarge = false;

        req.on('data', chunk => {
            if (isTooLarge) return;
            totalSize += chunk.length;
            if (totalSize > MAX_UPLOAD_SIZE) {
                isTooLarge = true;
                res.writeHead(413, { 'Content-Type': 'application/json', 'Connection': 'close' });
                res.end(JSON.stringify({ error: 'Payload Too Large', message: `File size exceeds 2MB limit` }));
                req.resume();
                return;
            }
            chunks.push(chunk);
        });

        req.on('end', () => {
            if (isTooLarge) return;

            if (chunks.length === 0) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Bad Request', message: 'No file data received' }));
                return;
            }

            const buffer = Buffer.concat(chunks);
            const typeInfo = getValidatedFileType(buffer);

            // Strict file signature validation (ignore client-supplied Content-Type or extensions)
            if (!typeInfo) {
                res.writeHead(415, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({
                    error: 'Unsupported Media Type',
                    message: 'Disallowed file type. Only validated PNG and JPEG images are permitted based on magic byte signatures'
                }));
                return;
            }

            // Cryptographically secure generated filename (prevents directory traversal & collisions)
            const safeFileId = `${crypto.randomUUID()}${typeInfo.ext}`;
            const targetPath = path.resolve(UPLOAD_DIR, safeFileId);

            // Verify strict path containment
            if (!targetPath.startsWith(UPLOAD_DIR)) {
                res.writeHead(403, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Forbidden', message: 'Illegal path assignment' }));
                return;
            }

            try {
                // Non-executable permissions (0o644)
                fs.writeFileSync(targetPath, buffer, { mode: 0o644 });
                res.writeHead(201, {
                    'Content-Type': 'application/json',
                    'X-Content-Type-Options': 'nosniff'
                });
                res.end(JSON.stringify({
                    ok: true,
                    fileId: safeFileId,
                    mime: typeInfo.mime,
                    size: buffer.length,
                    message: 'File uploaded and validated successfully'
                }));
            } catch (err) {
                console.error('File write error:', err.message);
                res.writeHead(500, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Internal Server Error', message: 'Failed to write file to isolated storage' }));
            }
        });
        return;
    }

    // 9b. Secure Download / Retrieval of Uploaded File with Safe Headers
    const uploadMatch = pathname.match(/^\/api\/upload\/([a-zA-Z0-9\-_.]+)$/);
    if (uploadMatch) {
        if (req.method !== 'GET' && req.method !== 'HEAD') {
            res.writeHead(405, { 'Content-Type': 'application/json', 'Allow': 'GET, HEAD' });
            res.end(JSON.stringify({ error: 'Method Not Allowed', message: 'Only GET and HEAD are supported' }));
            return;
        }

        const requestedId = uploadMatch[1];
        // Enforce strict UUID filename format
        if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(png|jpg)$/i.test(requestedId)) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Bad Request', message: 'Invalid file identifier format' }));
            return;
        }

        const safeFilePath = path.resolve(UPLOAD_DIR, requestedId);
        if (!safeFilePath.startsWith(UPLOAD_DIR)) {
            res.writeHead(403, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Forbidden', message: 'Illegal file path' }));
            return;
        }

        fs.stat(safeFilePath, (err, stats) => {
            if (err || !stats.isFile()) {
                res.writeHead(404, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Not Found', message: 'Uploaded file not found' }));
                return;
            }

            const ext = path.extname(safeFilePath).toLowerCase();
            const contentType = ext === '.png' ? 'image/png' : 'image/jpeg';

            res.writeHead(200, {
                'Content-Type': contentType,
                'Content-Length': stats.size,
                // Critical security download headers: prevent browser execution & MIME-sniffing
                'X-Content-Type-Options': 'nosniff',
                'Content-Disposition': `attachment; filename="downloaded-media${ext}"`,
                'Cache-Control': 'private, no-transform, max-age=3600'
            });

            if (req.method === 'HEAD') {
                res.end();
                return;
            }

            fs.createReadStream(safeFilePath).pipe(res);
        });
        return;
    }

    // 10. System Health & Carrier Telemetry Status
    if (pathname === '/api/health' && req.method === 'GET') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            status: 'online',
            service: 'Towerchat Live Bridge',
            carrier: 'GSM/LTE Cellular Gateway',
            device: 'Android Emulator (emulator-5554)',
            uptime: Math.round(process.uptime()),
            timestamp: new Date().toISOString()
        }));
        return;
    }

    // 11. Static File Serving (OWASP Path Traversal & Method Restriction Check)
    if (!checkRateLimit(req, res, { windowMs: 60000, max: 120, burst: 40, keyPrefix: 'static' })) {
        return;
    }

    // Enforce GET / HEAD only on static files
    if (req.method !== 'GET' && req.method !== 'HEAD') {
        res.writeHead(405, { 'Content-Type': 'application/json', 'Allow': 'GET, HEAD' });
        res.end(JSON.stringify({ error: 'Method Not Allowed', message: `HTTP method ${req.method} not allowed on static files` }));
        return;
    }

    const safePathname = pathname === '/' ? 'index.html' : pathname;
    const normalizedPath = path.normalize(safePathname).replace(/^(\.\.[\/\\])+/, '');
    const filePath = path.resolve(PUBLIC_DIR, '.' + path.sep + normalizedPath);

    // Path traversal containment validation
    if (!filePath.startsWith(PUBLIC_DIR)) {
        res.writeHead(403, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Forbidden', message: 'Path traversal attempt blocked' }));
        return;
    }

    fs.stat(filePath, (err, stats) => {
        if (err || !stats.isFile()) {
            const isHtml = !pathname.startsWith('/api/') && (req.headers.accept || '').includes('text/html');
            const custom404Path = path.resolve(PUBLIC_DIR, '404.html');
            if (isHtml && fs.existsSync(custom404Path)) {
                res.writeHead(404, {
                    'Content-Type': 'text/html; charset=utf-8',
                    'X-Content-Type-Options': 'nosniff'
                });
                fs.createReadStream(custom404Path).pipe(res);
                return;
            }
            res.writeHead(404, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Not Found', message: 'Requested asset does not exist' }));
            return;
        }

        const ext = path.extname(filePath).toLowerCase();
        const contentTypes = {
            '.html': 'text/html; charset=utf-8',
            '.js': 'application/javascript; charset=utf-8',
            '.css': 'text/css; charset=utf-8',
            '.png': 'image/png',
            '.svg': 'image/svg+xml',
            '.ico': 'image/x-icon'
        };

        const contentType = contentTypes[ext] || 'application/octet-stream';
        res.writeHead(200, {
            'Content-Type': contentType,
            'X-Content-Type-Options': 'nosniff',
            'X-Frame-Options': 'SAMEORIGIN'
        });
        if (req.method === 'HEAD') {
            res.end();
            return;
        }
        fs.createReadStream(filePath).pipe(res);
    });
}

// Global server with top-level try/catch boundary
const server = http.createServer((req, res) => {
    try {
        handleRequest(req, res);
    } catch (unhandledErr) {
        console.error('Top-level unhandled exception:', unhandledErr);
        if (!res.headersSent) {
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Internal Server Error', message: 'An unexpected server error occurred' }));
        }
    }
});

server.listen(PORT, '127.0.0.1', () => {
    console.log(`Towerchat Live Web Runner started on http://127.0.0.1:${PORT}`);
});
