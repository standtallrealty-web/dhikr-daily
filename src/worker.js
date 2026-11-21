// Dhikr Daily sync: stores each person's records in KV, protected by a token derived from their PIN.
const J = (o, s = 200) => new Response(JSON.stringify(o), { status: s, headers: { 'content-type': 'application/json', 'cache-control': 'no-store' } });

export default {
  async fetch(req, env) {
    const u = new URL(req.url);
    if (u.pathname !== '/api/sync') return env.ASSETS.fetch(req);
    const p = u.searchParams.get('p');
    if (p !== 'k' && p !== 'h') return J({ e: 'bad profile' }, 400);
    const tok = req.headers.get('x-tok') || '';
    if (!/^[a-f0-9]{64}$/.test(tok)) return J({ e: 'auth' }, 401);
    if (req.method !== 'GET' && req.method !== 'PUT') return J({ e: 'method' }, 405);

    const key = 'p:' + p, lk = 'l:' + p;
    const lock = await env.SYNC.get(lk, 'json');
    if (lock && lock.until > Date.now()) return J({ e: 'locked' }, 429);
    const rec = await env.SYNC.get(key, 'json');

    if (rec && rec.tok !== tok) {
      const f = ((lock && lock.f) || 0) + 1;
      await env.SYNC.put(lk, JSON.stringify({ f, until: f >= 8 ? Date.now() + 15 * 60000 : 0 }), { expirationTtl: 3600 });
      return J({ e: 'auth' }, 401);
    }
    if (lock) await env.SYNC.delete(lk);

    if (req.method === 'GET') {
      return rec ? J({ ts: rec.ts, data: rec.data }) : new Response(null, { status: 204 });
    }
    let b;
    try { b = await req.json(); } catch (e) { return J({ e: 'json' }, 400); }
    if (!b || typeof b.ts !== 'number' || !b.data || typeof b.data !== 'object' || JSON.stringify(b.data).length > 400000) return J({ e: 'bad body' }, 400);
    if (rec && b.ts < rec.ts) return J({ ts: rec.ts, data: rec.data }, 409);
    await env.SYNC.put(key, JSON.stringify({ tok, ts: b.ts, data: b.data }));
    return J({ ok: 1, ts: b.ts });
  }
};
