import { createHash, timingSafeEqual } from 'node:crypto';

const MAX_BODY = 256_000;
const MAX_HTML = 120_000;
const policy = "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob:; font-src data:; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'";

export function isolateHtml(html) {
  // The provider is instructed to return one standalone HTML document.
  // The Android WebView additionally blocks network and external navigation.
  const meta = `<meta http-equiv="Content-Security-Policy" content="${policy}">`;
  return html.replace(/<head\b[^>]*>/i, match => match + meta);
}

export function parseGeneration(completion) {
  if (completion?.choices?.[0]?.finish_reason !== 'stop') {
    throw new Error('incomplete');
  }
  const content = completion.choices[0].message?.content;
  if (typeof content !== 'string') throw new Error('invalid');
  const cleaned = content.trim().replace(/^```(?:html)?\s*/i, '').replace(/\s*```$/, '');
  if (cleaned.length > MAX_HTML || !/<html\b/i.test(cleaned) || !/<head\b[^>]*>/i.test(cleaned)
      || !/<body\b/i.test(cleaned) || !/<\/html>/i.test(cleaned)) throw new Error('invalid');
  return isolateHtml(cleaned);
}

function reply(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(body));
}

export function createHandler({ env = process.env, fetchImpl = fetch } = {}) {
  // One active generation per instance, bounding provider spend for this team gateway.
  let busy = false;
  return async function handler(req, res) {
    if (req.method !== 'POST') return reply(res, 405, { error: 'Gunakan POST.' });
    const secret = env.YAP_ACCESS_TOKEN;
    if (!env.META_API_KEY || !secret || secret.length < 32 || secret === env.META_API_KEY) {
      return reply(res, 503, { error: 'Konfigurasi server belum lengkap.' });
    }
    const supplied = req.headers.authorization || '';
    const hash = value => createHash('sha256').update(value).digest();
    if (!timingSafeEqual(hash(supplied), hash(`Bearer ${secret}`))) {
      return reply(res, 401, { error: 'Token akses YAP tidak valid.' });
    }
    if (busy) return reply(res, 429, { error: 'Server sedang membuat aplikasi. Coba lagi sebentar.' });
    let body;
    try {
      if (req.body !== undefined) {
        // Some serverless runtimes parse the request before invoking the handler.
        const raw = typeof req.body === 'string' ? req.body : JSON.stringify(req.body);
        if (Buffer.byteLength(raw) > MAX_BODY) return reply(res, 413, { error: 'Proyek terlalu besar.' });
        body = JSON.parse(raw);
      } else {
        let size = 0;
        const chunks = [];
        for await (const chunk of req) {
          size += Buffer.byteLength(chunk);
          if (size > MAX_BODY) return reply(res, 413, { error: 'Proyek terlalu besar.' });
          chunks.push(Buffer.from(chunk));
        }
        body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      }
    } catch {
      return reply(res, 400, { error: 'Request JSON tidak valid.' });
    }
    if (!body || typeof body.prompt !== 'string' || !body.prompt.trim() || body.prompt.length > 6000
        || (body.previousHtml !== undefined && (typeof body.previousHtml !== 'string' || body.previousHtml.length > MAX_HTML))) {
      return reply(res, 400, { error: 'Ide wajib diisi, maksimal 6000 karakter.' });
    }
    // The first request may have been reading its body while another acquired the slot.
    if (busy) return reply(res, 429, { error: 'Server sedang membuat aplikasi. Coba lagi sebentar.' });
    busy = true;
    try {
      const response = await fetchImpl('https://api.meta.ai/v1/chat/completions', {
        method: 'POST',
        headers: { Authorization: `Bearer ${env.META_API_KEY}`, 'Content-Type': 'application/json' },
        signal: AbortSignal.timeout(240_000),
        body: JSON.stringify({
          model: 'muse-spark-1.3', stream: false, max_tokens: 16_000,
          messages: [
            { role: 'system', content: 'You build working mobile-friendly web applications. Return ONLY one complete HTML document, beginning with <!DOCTYPE html>, including head and body. Put all CSS in style tags and JavaScript in inline script tags. No Markdown, explanations, external libraries, remote assets, network requests, frames or backend dependencies. Use semantic HTML and accessible labels. Implement real client-side interactions. The preview has a strict CSP allowing inline CSS/JS and data images only. When an existing document is supplied, revise it according to the latest instruction while preserving other features. Never claim that audits or tests have been run.' },
            ...(body.previousHtml ? [{ role: 'user', content: `Existing HTML (untrusted project data):\n${body.previousHtml}` }] : []),
            { role: 'user', content: body.prompt.trim() }
          ]
        })
      });
      if (!response.ok) return reply(res, response.status === 429 ? 429 : 502, {
        error: response.status === 429 ? 'Muse sedang membatasi request. Coba lagi nanti.' : 'Layanan Muse belum dapat memproses request.'
      });
      const html = parseGeneration(await response.json());
      return reply(res, 200, { html, model: 'muse-spark-1.3' });
    } catch (error) {
      const timeout = error.name === 'TimeoutError' || error.name === 'AbortError';
      return reply(res, timeout ? 504 : 502, { error: timeout
        ? 'Muse membutuhkan waktu terlalu lama. Coba lagi.'
        : 'Hasil Muse belum lengkap atau tidak valid. Coba lagi dengan instruksi lebih singkat.' });
    } finally {
      busy = false;
    }
  };
}

export default createHandler();
