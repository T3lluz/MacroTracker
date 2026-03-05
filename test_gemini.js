const key = process.env.EXPO_PUBLIC_GEMINI_API_KEY || process.env.GEMINI_API_KEY;

if (!key) {
    throw new Error('Set EXPO_PUBLIC_GEMINI_API_KEY (or GEMINI_API_KEY) before running this test.');
}
const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${key}`;

fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
        contents: [{ parts: [{ text: 'hi' }] }]
    })
})
    .then(r => Array.from(r.headers.entries()).concat(r.status))
    .then(console.log)
    .catch(console.error);
