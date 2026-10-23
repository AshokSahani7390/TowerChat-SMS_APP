const http = require('http');

function makeRequest(options, postData = null) {
    return new Promise((resolve, reject) => {
        const req = http.request(options, (res) => {
            let data = '';
            res.on('data', chunk => { data += chunk; });
            res.on('end', () => {
                resolve({
                    statusCode: res.statusCode,
                    headers: res.headers,
                    body: data
                });
            });
        });
        req.on('error', reject);
        if (postData) {
            req.write(postData);
        }
        req.end();
    });
}

async function runValidationTests() {
    console.log('================================================================');
    console.log('🛡️ TOWERCHAT INPUT VALIDATION & SECURITY TEST SUITE');
    console.log('================================================================\n');

    // Reset rate limits before testing input validation
    await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/test/reset-ratelimit',
        method: 'POST',
        headers: { 'X-Test-Secret': 'towerchat-test' }
    });

    let passed = 0;
    let failed = 0;

    function assertTest(name, condition, details = '') {
        if (condition) {
            console.log(`✅ [PASS] ${name}`);
            passed++;
        } else {
            console.error(`❌ [FAIL] ${name} - ${details}`);
            failed++;
        }
    }

    // 1. Malformed JSON Body
    const r1 = await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/tap',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, '{ broken json: true ');
    assertTest('Malformed JSON rejected with 400 Bad Request', r1.statusCode === 400, `Got ${r1.statusCode}`);

    // 2. Empty Body
    const r2 = await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/tap',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, '   ');
    assertTest('Empty body rejected with 400 Bad Request', r2.statusCode === 400, `Got ${r2.statusCode}`);

    // 3. Array payload instead of Object
    const r3 = await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/tap',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, JSON.stringify([1, 2, 3]));
    assertTest('Array payload rejected with 400 Bad Request', r3.statusCode === 400, `Got ${r3.statusCode}`);

    // 4. Oversized Request Body (> 64KB DoS Prevention)
    const largePayload = JSON.stringify({ text: 'A'.repeat(70 * 1024) });
    const r4 = await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/text',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, largePayload);
    assertTest('Oversized payload (>64KB) rejected with 413 Payload Too Large', r4.statusCode === 413, `Got ${r4.statusCode}`);

    // Reset rate limits again to avoid rate limiting interfering with functional validation
    await makeRequest({ hostname: '127.0.0.1', port: 3000, path: '/api/test/reset-ratelimit', method: 'POST', headers: { 'X-Test-Secret': 'towerchat-test' } });

    // 5. Tap Coordinates Range & Type Validation
    const r5_type = await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/tap',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, JSON.stringify({ x: 'invalid_string', y: 0.5 }));
    assertTest('Tap non-number rejected with 400', r5_type.statusCode === 400, `Got ${r5_type.statusCode}`);

    const r5_bounds = await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/tap',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, JSON.stringify({ x: 1.5, y: -0.2 }));
    assertTest('Tap out-of-bounds coordinates (x>1.0, y<0.0) rejected with 400', r5_bounds.statusCode === 400, `Got ${r5_bounds.statusCode}`);

    // 6. Text Input Validation (Length & Sanitization)
    await makeRequest({ hostname: '127.0.0.1', port: 3000, path: '/api/test/reset-ratelimit', method: 'POST', headers: { 'X-Test-Secret': 'towerchat-test' } });

    const r6_empty = await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/text',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, JSON.stringify({ text: '' }));
    assertTest('Empty text rejected with 400', r6_empty.statusCode === 400, `Got ${r6_empty.statusCode}`);

    const r6_toolong = await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/text',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, JSON.stringify({ text: 'Z'.repeat(501) }));
    assertTest('Text exceeding 500 chars rejected with 400', r6_toolong.statusCode === 400, `Got ${r6_toolong.statusCode}`);

    // 7. Key Event Range & Type Validation
    await makeRequest({ hostname: '127.0.0.1', port: 3000, path: '/api/test/reset-ratelimit', method: 'POST', headers: { 'X-Test-Secret': 'towerchat-test' } });

    const r7_nonInt = await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/key',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, JSON.stringify({ code: 4.5 }));
    assertTest('Keycode non-integer rejected with 400', r7_nonInt.statusCode === 400, `Got ${r7_nonInt.statusCode}`);

    const r7_outOfRange = await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/key',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, JSON.stringify({ code: 9999 }));
    assertTest('Keycode out of range (>350) rejected with 400', r7_outOfRange.statusCode === 400, `Got ${r7_outOfRange.statusCode}`);

    // 8. SMS Sender & Message Format Validation
    await makeRequest({ hostname: '127.0.0.1', port: 3000, path: '/api/test/reset-ratelimit', method: 'POST', headers: { 'X-Test-Secret': 'towerchat-test' } });

    const r8_invalidSender = await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/sms',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, JSON.stringify({ sender: 'bad number with spaces and $', message: 'Hello' }));
    assertTest('Malformed phone number/sender rejected with 400', r8_invalidSender.statusCode === 400, `Got ${r8_invalidSender.statusCode}`);

    const r8_emptyMsg = await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/sms',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, JSON.stringify({ sender: '+919876543210', message: '' }));
    assertTest('Empty SMS message rejected with 400', r8_emptyMsg.statusCode === 400, `Got ${r8_emptyMsg.statusCode}`);

    // 9. Path Traversal Containment Check
    await makeRequest({ hostname: '127.0.0.1', port: 3000, path: '/api/test/reset-ratelimit', method: 'POST', headers: { 'X-Test-Secret': 'towerchat-test' } });

    const r9_traversal = await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/../../server.js',
        method: 'GET'
    });
    // Must be either 403 Forbidden or 404 Not Found, never 200 leaking server.js
    const isProtected = r9_traversal.statusCode === 403 || r9_traversal.statusCode === 404;
    assertTest('Path traversal attempt (/../../server.js) safely blocked with 403/404', isProtected, `Got ${r9_traversal.statusCode}`);

    console.log(`\n================================================================`);
    console.log(`Summary: ${passed} Passed, ${failed} Failed`);
    console.log(`================================================================`);

    if (failed > 0) {
        process.exit(1);
    }
}

runValidationTests().catch(err => {
    console.error('Test Suite Error:', err);
    process.exit(1);
});
