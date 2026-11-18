// npm install --prefix /tmp/chat-ui-check --no-package-lock jsdom@31.0.1
// NODE_PATH=/tmp/chat-ui-check/node_modules node --test scripts/test-messenger.cjs
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {JSDOM} = require('jsdom');
const fs = require('node:fs');
const html = fs.readFileSync('chat-gateway/src/main/resources/static/messenger.html', 'utf8');

function page() {
    const dom = new JSDOM(html, {url: 'http://localhost:8080', runScripts: 'outside-only'});
    const script = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].at(-1)[1];
    dom.window.eval(script + '\nwindow.runForTest = code => eval(code);');
    dom.window.runForTest(`currentUser='00000000-0000-4000-8000-000000000001'; activeChatId='chat-A';`);
    return dom;
}

test('outgoing message appears immediately and correlated failure preserves its text', () => {
    const dom = page();
    try {
        dom.window.runForTest(`stompClient={connected:true,publish(){throw new Error('offline')}}`);
        dom.window.document.getElementById('msgInput').value = 'keep this text';
        dom.window.sendMessage();
        const row = dom.window.document.querySelector('[data-client-id]');
        assert.ok(row);
        assert.equal(row.dataset.state, 'failed');
        assert.ok(row.textContent.includes('keep this text'));
        assert.ok(row.textContent.includes('Не отправлено'));
    } finally { dom.window.close(); }
});

test('accepted server id merges the outgoing row with history without duplicate', () => {
    const dom = page();
    try {
        dom.window.runForTest(`stompClient={connected:true,publish(){}}`);
        dom.window.document.getElementById('msgInput').value = 'hello';
        dom.window.sendMessage();
        const id = dom.window.document.querySelector('[data-client-id]').dataset.clientId;
        dom.window.markOutgoing(id, 'queued', 'server-id');
        dom.window.appendMessage('hello', 'sent', new Date().toISOString(), 'server-id');
        dom.window.appendMessage('hello', 'sent', new Date().toISOString(), 'server-id');
        assert.equal(dom.window.document.querySelectorAll('#messages .msg-row').length, 1);
        assert.equal(dom.window.document.querySelectorAll('.send-state').length, 0);
    } finally { dom.window.close(); }
});

test('history that arrived before success notification merges with optimistic row', () => {
    const dom = page();
    try {
        dom.window.runForTest(`stompClient={connected:true,publish(){}}`);
        dom.window.document.getElementById('msgInput').value = 'hello';
        dom.window.sendMessage();
        const id = dom.window.document.querySelector('[data-client-id]').dataset.clientId;
        dom.window.appendMessage('hello', 'sent', new Date().toISOString(), 'server-id');
        dom.window.markOutgoing(id, 'queued', 'server-id');
        assert.equal(dom.window.document.querySelectorAll('#messages .msg-row').length, 1);
    } finally { dom.window.close(); }
});

test('late history response from previous chat cannot change current messages', async () => {
    const dom = page();
    try {
        let release;
        dom.window.fetch = () => new Promise(resolve => { release = resolve; });
        const pending = dom.window.loadMessages('chat-A');
        dom.window.runForTest(`activeChatId='chat-B'; chatGeneration++`);
        release({ok:true,json:async()=>({content:[{id:'old',content:'wrong chat',senderId:'peer'}]})});
        await pending;
        assert.equal(dom.window.document.querySelectorAll('#messages .msg-row').length, 0);
    } finally { dom.window.close(); }
});

test('A to B to A rejects the response from the first visit to A', async () => {
    const dom = page();
    try {
        let release;
        dom.window.fetch = () => new Promise(resolve => { release = resolve; });
        const pending = dom.window.loadMessages('chat-A');
        dom.window.runForTest(`chatGeneration+=2; activeChatId='chat-A'`);
        release({ok:true,json:async()=>({content:[{id:'old',content:'stale',senderId:'peer'}]})});
        await pending;
        assert.equal(dom.window.document.querySelectorAll('#messages .msg-row').length, 0);
    } finally { dom.window.close(); }
});

test('reopening a chat with stored history clears the queued placeholder state', async () => {
    const dom = page();
    try {
        dom.window.runForTest(`stompClient={connected:true,publish(){}}`);
        dom.window.document.getElementById('msgInput').value = 'hello';
        dom.window.sendMessage();
        const id = dom.window.document.querySelector('[data-client-id]').dataset.clientId;
        dom.window.markOutgoing(id, 'queued', 'server-id');
        dom.window.document.getElementById('messages').innerHTML = '';
        dom.window.fetch = async () => ({ok:true,json:async()=>({content:[{
            id:'server-id',content:'hello',senderId:'00000000-0000-4000-8000-000000000001'
        }]})});
        await dom.window.loadMessages('chat-A');
        assert.equal(dom.window.document.querySelectorAll('#messages .msg-row').length, 1);
        assert.equal(dom.window.document.querySelectorAll('.send-state').length, 0);
    } finally { dom.window.close(); }
});
