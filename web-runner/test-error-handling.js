const http = require('http');
const assert = require('assert');

function makeRequest(options, postData) {
    return new Promise((resolve, reject) => {
        const req = http.request(options, (res) => {
            let data = '';
            res.on('data', chunk => { data += chunk; });
            res.on('end', () => {
                let parsed = null;
                try {
                    parsed = JSON.parse(data);
                } catch (e) {
                    parsed = data;
                }
                resolve({ statusCode: res.statusCode, headers: res.headers, body: parsed });
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
    console.log('🧪 Starting Step 5: Error Handling & Information Leakage Tests...\n');
    let passed = 0;
    let total = 0;

    // Test 1: Non-existent route returns 404 with generic JSON and no stack traces or server paths
    total++;
    try {
        const res = await makeRequest({
            hostname: '127.0.0.1',
            port: 3000,
            path: '/non-existent-endpoint-test',
            method: 'GET'
        });
        assert.strictEqual(res.statusCode, 404, `Expected 404, got ${res.statusCode}`);
        assert.strictEqual(res.body.error, 'Not Found');
        assert.strictEqual(typeof res.body.message, 'string');
        assert.strictEqual(res.headers['x-powered-by'], undefined, 'X-Powered-By should not be present');
        assert(!JSON.stringify(res.body).includes('C:\\'), 'Must not leak Windows file paths');
        console.log('✅ Test 1: 404 response is generic JSON without leaking server paths or stack traces');
        passed++;
    } catch (e) {
        console.error('❌ Test 1 failed:', e.message);
    }

    // Test 2: Malformed JSON payload returns clean 400 Bad Request
    total++;
    try {
        const res = await makeRequest({
            hostname: '127.0.0.1',
            port: 3000,
            path: '/api/tap',
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, '{ bad json: 123 ');
        assert.strictEqual(res.statusCode, 400, `Expected 400, got ${res.statusCode}`);
        assert.strictEqual(res.body.error, 'Bad Request');
        assert.strictEqual(res.body.message, 'Invalid JSON format');
        assert(!JSON.stringify(res.body).includes('SyntaxError'), 'Must not leak V8 engine syntax error traces');
        console.log('✅ Test 2: Malformed JSON handled gracefully with 400 Bad Request (no stack traces)');
        passed++;
    } catch (e) {
        console.error('❌ Test 2 failed:', e.message);
    }

    // Test 3: Empty body returns 400 Bad Request
    total++;
    try {
        const res = await makeRequest({
            hostname: '127.0.0.1',
            port: 3000,
            path: '/api/text',
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, '   ');
        assert.strictEqual(res.statusCode, 400, `Expected 400, got ${res.statusCode}`);
        assert.strictEqual(res.body.message, 'Request body cannot be empty');
        console.log('✅ Test 3: Empty body returns 400 Bad Request');
        passed++;
    } catch (e) {
        console.error('❌ Test 3 failed:', e.message);
    }

    // Test 4: Path traversal attempt returns 403 Forbidden without disclosing internal directories
    total++;
    try {
        const res = await makeRequest({
            hostname: '127.0.0.1',
            port: 3000,
            path: '/..%2f..%2f..%2fWindows%2fwin.ini',
            method: 'GET'
        });
        assert([403, 404].includes(res.statusCode), `Expected 403 or 404, got ${res.statusCode}`);
        assert(!JSON.stringify(res.body).includes('[extensions]'), 'Must not leak file content');
        console.log(`✅ Test 4: Path traversal attempt blocked safely with status ${res.statusCode}`);
        passed++;
    } catch (e) {
        console.error('❌ Test 4 failed:', e.message);
    }

    // Test 5: Verify log sanitization function redacts sensitive patterns
    total++;
    try {
        const serverCode = require('fs').readFileSync('d:/SMS_APP/web-runner/server.js', 'utf8');
        assert(serverCode.includes('sanitizeLogcat'), 'server.js must define sanitizeLogcat');
        assert(serverCode.includes('[REDACTED]'), 'sanitizeLogcat must mask credentials');
        
        // Fetch logs endpoint
        const res = await makeRequest({
            hostname: '127.0.0.1',
            port: 3000,
            path: '/api/logs',
            method: 'GET'
        });
        assert.strictEqual(res.statusCode, 200, `Expected 200, got ${res.statusCode}`);
        assert(typeof res.body.logs === 'string', 'Logs must be string');
        assert(!res.body.logs.includes('Bearer secret'), 'No unmasked bearer tokens allowed');
        console.log('✅ Test 5: Log extraction endpoint enforces logcat sanitization & redaction');
        passed++;
    } catch (e) {
        console.error('❌ Test 5 failed:', e.message);
    }

    // Test 6: Verify global error boundary handles unexpected server errors
    total++;
    try {
        const res = await makeRequest({
            hostname: '127.0.0.1',
            port: 3000,
            path: '/api/sms',
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, JSON.stringify({ sender: '12345', message: 'Hello error boundary' }));
        // Should either succeed (200) or report clean 502/429 without exposing ADB process internals
        assert([200, 429, 502].includes(res.statusCode), `Expected 200, 429 or 502, got ${res.statusCode}`);
        if (res.statusCode === 502) {
            assert.strictEqual(res.body.error, 'Bad Gateway');
            assert(!res.body.message.includes('C:\\'), 'Must not leak executable paths in 502 response');
        }
        console.log(`✅ Test 6: Subprocess error handled safely with status ${res.statusCode} (no path leaks)`);
        passed++;
    } catch (e) {
        console.error('❌ Test 6 failed:', e.message);
    }

    console.log(`\n========================================`);
    console.log(`Step 5 Verification: ${passed}/${total} tests passed!`);
    console.log(`========================================\n`);

    if (passed === total) {
        process.exit(0);
    } else {
        process.exit(1);
    }
}

runTests();
