const key = 'AIzaSyCGWHG77glS_8mqsxofjbPkrxJPvZZGq_M';
const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${key}`;

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
