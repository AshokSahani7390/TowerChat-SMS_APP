const http = require('http');
const assert = require('assert');

function makeRequest(options, postData) {
    return new Promise((resolve, reject) => {
        const req = http.request(options, (res) => {
            let data = Buffer.alloc(0);
            res.on('data', chunk => {
                data = Buffer.concat([data, chunk]);
            });
            res.on('end', () => {
                let parsed = null;
                try {
                    parsed = JSON.parse(data.toString('utf8'));
                } catch (e) {
                    parsed = data;
                }
                resolve({ statusCode: res.statusCode, headers: res.headers, body: parsed, rawData: data });
            });
        });
        req.on('error', reject);
        if (postData) {
            req.write(postData);
        }
        req.end();
    });
}

async function runFileUploadTests() {
    console.log('🧪 Starting Step 6: File Upload Safety & Download Hardening Tests...\n');
    let passed = 0;
    let total = 0;

    // Reset rate limiter for fresh test execution
    await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/test/reset-ratelimit',
        method: 'POST',
        headers: { 'X-Test-Secret': 'towerchat-test' }
    });

    // Test 1: Oversized File (> 2MB) is rejected with 413 Payload Too Large
    total++;
    try {
        const oversizedBuffer = Buffer.alloc(2.5 * 1024 * 1024, 0x41);
        const res = await makeRequest({
            hostname: '127.0.0.1',
            port: 3000,
            path: '/api/upload',
            method: 'POST',
            headers: {
                'Content-Type': 'image/png',
                'Content-Length': oversizedBuffer.length.toString()
            }
        }, oversizedBuffer);
        assert.strictEqual(res.statusCode, 413, `Expected 413, got ${res.statusCode}`);
        assert.strictEqual(res.body.error, 'Payload Too Large');
        console.log('✅ Test 1: Oversized payload (> 2MB) immediately rejected with HTTP 413');
        passed++;
    } catch (e) {
        console.error('❌ Test 1 failed:', e.message);
    }

    // Test 2: Disallowed file type / executable payload (e.g. PHP/Bash/HTML script) rejected with 415
    total++;
    try {
        const maliciousPayload = Buffer.from('<?php echo "evil"; system($_GET["cmd"]); ?>', 'utf8');
        const res = await makeRequest({
            hostname: '127.0.0.1',
            port: 3000,
            path: '/api/upload',
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-php',
                'Content-Length': maliciousPayload.length.toString()
            }
        }, maliciousPayload);
        assert.strictEqual(res.statusCode, 415, `Expected 415, got ${res.statusCode}`);
        assert.strictEqual(res.body.error, 'Unsupported Media Type');
        console.log('✅ Test 2: Disallowed file payload (malicious script) rejected with HTTP 415');
        passed++;
    } catch (e) {
        console.error('❌ Test 2 failed:', e.message);
    }

    // Test 3: Misleading extension / spoofed Content-Type (fake PNG with text payload) fails magic byte check
    total++;
    try {
        const fakePng = Buffer.from('Not a real PNG header, just plain text trying to bypass mime checks', 'utf8');
        const res = await makeRequest({
            hostname: '127.0.0.1',
            port: 3000,
            path: '/api/upload',
            method: 'POST',
            headers: {
                'Content-Type': 'image/png',
                'Content-Length': fakePng.length.toString()
            }
        }, fakePng);
        assert.strictEqual(res.statusCode, 415, `Expected 415, got ${res.statusCode}`);
        assert(res.body.message.includes('magic byte signatures'), 'Must enforce magic byte verification');
        console.log('✅ Test 3: Spoofed Content-Type detected and blocked via binary magic-byte verification');
        passed++;
    } catch (e) {
        console.error('❌ Test 3 failed:', e.message);
    }

    // Reset rate limiter before Test 4
    await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/test/reset-ratelimit',
        method: 'POST',
        headers: { 'X-Test-Secret': 'towerchat-test' }
    });

    // Test 4: Valid PNG upload with authentic magic bytes succeeds, gets UUID storage name
    total++;
    let uploadedFileId = null;
    try {
        // Minimal valid 1x1 PNG bytes
        const validPngBytes = Buffer.from([
            0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, // PNG Signature
            0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1f, 0x15, 0xc4,
            0x89, 0x00, 0x00, 0x00, 0x0a, 0x49, 0x44, 0x41,
            0x54, 0x78, 0x9c, 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0d, 0x0a, 0x2d, 0xb4, 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4e, 0x44, 0xae,
            0x42, 0x60, 0x82
        ]);
        const res = await makeRequest({
            hostname: '127.0.0.1',
            port: 3000,
            path: '/api/upload',
            method: 'POST',
            headers: {
                'Content-Type': 'image/png',
                'Content-Length': validPngBytes.length.toString()
            }
        }, validPngBytes);
        assert.strictEqual(res.statusCode, 201, `Expected 201, got ${res.statusCode}`);
        assert.strictEqual(res.body.ok, true);
        assert(res.body.fileId.endsWith('.png'));
        assert(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.png$/i.test(res.body.fileId));
        uploadedFileId = res.body.fileId;
        console.log(`✅ Test 4: Valid PNG accepted, assigned safe UUID filename: ${uploadedFileId}`);
        passed++;
    } catch (e) {
        console.error('❌ Test 4 failed:', e.message);
    }

    // Test 5: Safe download of uploaded media enforces nosniff and attachment headers
    total++;
    try {
        assert(uploadedFileId, 'Requires uploaded file from Test 4');
        const res = await makeRequest({
            hostname: '127.0.0.1',
            port: 3000,
            path: `/api/upload/${uploadedFileId}`,
            method: 'GET'
        });
        assert.strictEqual(res.statusCode, 200, `Expected 200, got ${res.statusCode}`);
        assert.strictEqual(res.headers['x-content-type-options'], 'nosniff');
        assert(res.headers['content-disposition'].includes('attachment; filename="downloaded-media.png"'));
        assert.strictEqual(res.headers['content-type'], 'image/png');
        console.log('✅ Test 5: Media retrieval sets Content-Disposition attachment & X-Content-Type-Options: nosniff');
        passed++;
    } catch (e) {
        console.error('❌ Test 5 failed:', e.message);
    }

    // Test 6: Path traversal attempt on upload endpoint (/api/upload/../../etc) blocked
    total++;
    try {
        const res = await makeRequest({
            hostname: '127.0.0.1',
            port: 3000,
            path: '/api/upload/..%2f..%2f..%2fserver.js',
            method: 'GET'
        });
        assert([400, 403, 404].includes(res.statusCode), `Expected 400, 403 or 404, got ${res.statusCode}`);
        console.log(`✅ Test 6: Path traversal against upload storage blocked with status ${res.statusCode}`);
        passed++;
    } catch (e) {
        console.error('❌ Test 6 failed:', e.message);
    }

    // Test 7: Method restriction on static files (POST /index.html returns 405 Method Not Allowed)
    total++;
    try {
        const res = await makeRequest({
            hostname: '127.0.0.1',
            port: 3000,
            path: '/index.html',
            method: 'POST'
        });
        assert.strictEqual(res.statusCode, 405, `Expected 405, got ${res.statusCode}`);
        assert.strictEqual(res.body.error, 'Method Not Allowed');
        console.log('✅ Test 7: HTTP POST/PUT against static file paths rejected with 405 Method Not Allowed');
        passed++;
    } catch (e) {
        console.error('❌ Test 7 failed:', e.message);
    }

    // Test 8: Security headers present on static responses (nosniff & SAMEORIGIN)
    total++;
    try {
        const res = await makeRequest({
            hostname: '127.0.0.1',
            port: 3000,
            path: '/',
            method: 'GET'
        });
        assert.strictEqual(res.statusCode, 200, `Expected 200, got ${res.statusCode}`);
        assert.strictEqual(res.headers['x-content-type-options'], 'nosniff');
        assert.strictEqual(res.headers['x-frame-options'], 'SAMEORIGIN');
        console.log('✅ Test 8: Static web assets served with nosniff and SAMEORIGIN security headers');
        passed++;
    } catch (e) {
        console.error('❌ Test 8 failed:', e.message);
    }

    console.log(`\n========================================`);
    console.log(`Step 6 Verification: ${passed}/${total} tests passed!`);
    console.log(`========================================\n`);

    if (passed === total) {
        process.exit(0);
    } else {
        process.exit(1);
    }
}

runFileUploadTests();
