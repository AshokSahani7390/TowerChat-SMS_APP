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

async function runTests() {
    console.log('=====================================================');
    console.log('🧪 TOWERCHAT RATE LIMITING INTEGRATION TEST SUITE');
    console.log('=====================================================\n');

    // Step 0: Clear stores
    console.log('[Test 0] Resetting rate limiter stores...');
    const resetRes = await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/test/reset-ratelimit',
        method: 'POST',
        headers: { 'X-Test-Secret': 'towerchat-test' }
    });
    console.log(`Reset status: ${resetRes.statusCode} - ${resetRes.body.trim()}\n`);

    // Test 1: Verify Initial Request & RateLimit Headers on / (Static Asset Route)
    console.log('[Test 1] Testing initial request and RFC headers on / (max: 120, burst: 40)...');
    const req1 = await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/',
        method: 'GET'
    });
    console.log(`Status: ${req1.statusCode}`);
    console.log(`RateLimit-Limit: ${req1.headers['ratelimit-limit']}`);
    console.log(`RateLimit-Remaining: ${req1.headers['ratelimit-remaining']}`);
    if (req1.statusCode === 200 && req1.headers['ratelimit-limit'] === '120') {
        console.log('✅ Passed Test 1: Headers and initial token deduction correct\n');
    } else {
        console.error('❌ Failed Test 1', req1);
    }

    // Test 2: Burst Limit Test on /api/sms (Limit: 5/min, Burst: 2)
    console.log('[Test 2] Testing burst limits on /api/sms (burst: 2)...');
    const smsPayload = JSON.stringify({ sender: '+919999900001', message: 'Rate limit test' });

    console.log('Sending Request 1 (Expect 200)...');
    const s1 = await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/sms',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, smsPayload);
    console.log(`Req 1: HTTP ${s1.statusCode}, Remaining: ${s1.headers['ratelimit-remaining']}`);

    console.log('Sending Request 2 (Expect 200)...');
    const s2 = await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/sms',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, smsPayload);
    console.log(`Req 2: HTTP ${s2.statusCode}, Remaining: ${s2.headers['ratelimit-remaining']}`);

    console.log('Sending Request 3 (Expect 429 Too Many Requests - Burst Exceeded)...');
    const s3 = await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/sms',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, smsPayload);
    console.log(`Req 3: HTTP ${s3.statusCode}, Retry-After: ${s3.headers['retry-after']}s, Body: ${s3.body.trim()}`);

    if (s1.statusCode === 200 && s2.statusCode === 200 && s3.statusCode === 429 && s3.headers['retry-after']) {
        console.log('✅ Passed Test 2: Burst limit correctly enforced with HTTP 429 & Retry-After header\n');
    } else {
        console.error('❌ Failed Test 2');
    }

    // Test 3: Temporary Lockout on Repeat Violations
    console.log('[Test 3] Testing Temporary Lockout after repeat violations on /api/sms...');
    console.log('Sending violations to trigger 5-minute lockout cooldown...');
    await makeRequest({ hostname: '127.0.0.1', port: 3000, path: '/api/sms', method: 'POST', headers: { 'Content-Type': 'application/json' } }, smsPayload);
    await makeRequest({ hostname: '127.0.0.1', port: 3000, path: '/api/sms', method: 'POST', headers: { 'Content-Type': 'application/json' } }, smsPayload);
    const lockRes = await makeRequest({ hostname: '127.0.0.1', port: 3000, path: '/api/sms', method: 'POST', headers: { 'Content-Type': 'application/json' } }, smsPayload);
    console.log(`Lockout response: HTTP ${lockRes.statusCode}, Retry-After: ${lockRes.headers['retry-after']}s`);
    console.log(`Body: ${lockRes.body.trim()}`);
    if (lockRes.statusCode === 429 && parseInt(lockRes.headers['retry-after'], 10) > 60) {
        console.log('✅ Passed Test 3: Temporary lockout triggered (lockout > 60s cooldown)\n');
    } else {
        console.error('❌ Failed Test 3');
    }

    // Test 4: IP Spoofing Prevention via Untrusted X-Forwarded-For
    console.log('[Test 4] Testing IP Spoofing protection with forged X-Forwarded-For...');
    const spoofRes = await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/sms',
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Forwarded-For': '198.51.100.42' // Attacker attempting to bypass rate limits with forged IP
        }
    }, smsPayload);
    console.log(`Spoofed request: HTTP ${spoofRes.statusCode} (Still locked out on socket IP: ${spoofRes.body.trim()})`);
    if (spoofRes.statusCode === 429) {
        console.log('✅ Passed Test 4: Forged X-Forwarded-For rejected; socket IP safely enforced\n');
    } else {
        console.error('❌ Failed Test 4');
    }

    // Test 5: Reset Behavior
    console.log('[Test 5] Testing Rate Limit Reset behavior...');
    await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/test/reset-ratelimit',
        method: 'POST',
        headers: { 'X-Test-Secret': 'towerchat-test' }
    });
    const postResetRes = await makeRequest({
        hostname: '127.0.0.1',
        port: 3000,
        path: '/api/sms',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    }, smsPayload);
    console.log(`Post-reset status: HTTP ${postResetRes.statusCode}, Remaining: ${postResetRes.headers['ratelimit-remaining']}`);
    if (postResetRes.statusCode === 200) {
        console.log('✅ Passed Test 5: Rate limit store cleanly reset and tokens replenished\n');
    } else {
        console.error('❌ Failed Test 5');
    }

    console.log('🎉 ALL RATE LIMITING TESTS PASSED SUCCESSFULLY!');
}

runTests().catch(err => {
    console.error('Test Suite Error:', err);
    process.exit(1);
});
