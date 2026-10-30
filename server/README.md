# YAP Muse gateway

Android calls this gateway; only the gateway holds the Meta API key. The model is fixed to `muse-spark-1.3` at `https://api.meta.ai/v1`.

## Local development

Requires Node.js 22.9 or newer.

1. Copy `.env.example` to `.env` inside `server/`.
2. Set `META_API_KEY` to your provider key. Set `YAP_ACCESS_TOKEN` to a **different**, random token of at least 32 characters. Generate one with `node -e "console.log(require('node:crypto').randomBytes(32).toString('hex'))"`.
3. From `server/`, run `npm start`. The gateway listens on loopback, port 8787.
4. In a debug Android build, open **Pengaturan AI**. Use `http://10.0.2.2:8787` on the standard Android emulator, and enter the YAP access token. The Meta key is never entered in Android.
5. For a USB-connected device, run `adb reverse tcp:8787 tcp:8787`, then use `http://127.0.0.1:8787` in the debug app.

## Deploy without a VPS

`api/generate.mjs` exports a Node request/response handler that can be hosted as a serverless function. For a Vercel project, use `server` as the Root Directory, the Other framework preset, and no build command. Configure `META_API_KEY` and `YAP_ACCESS_TOKEN` as server-side environment secrets, then deploy. The function is available at `/api/generate`.

The platform must allow the function to run for up to 300 seconds. Check the limits of the hosting plan before deploying. Do not add either secret to public/client environment variables or commit `.env`. Set the deployed HTTPS origin (without `/api/generate`) and the YAP token in Android's Pengaturan AI. Release builds require HTTPS.

References: [Node.js functions](https://vercel.com/docs/functions/runtimes/node-js), [function duration configuration](https://vercel.com/docs/functions/configuring-functions/duration).

This is a team/prototype gateway: anyone holding the YAP access token can generate applications. Give it only to trusted testers and rotate it if shared publicly. The in-memory concurrency limit applies per running instance, not globally. For a public release, replace the shared token with verified individual user sessions, persistent per-user quotas and an upstream spending limit. A static token embedded in an APK is not user authentication.

## Behavior

- `POST /api/generate`, `Authorization: Bearer <YAP_ACCESS_TOKEN>`.
- JSON input: `{ "prompt": "Buat kalkulator", "previousHtml": "optional existing document" }`.
- Output: `{ "html": "<!DOCTYPE html>...", "model": "muse-spark-1.3" }`.
- The model generates one HTML document with inline CSS and JS. Follow-up instructions revise the previous document.
- Truncated/malformed results are rejected, preserving the previous app. Provider error bodies and secrets are never returned to Android.
- Preview blocks external navigation/network/file access and applies a CSP. Generated apps run as local frontend demos; remote assets, API calls and external login flows are unsupported.
- `npm test` covers authorization, request limits, model selection, revisions, result validation, concurrency and secret redaction.

No hosting account or production URL is provisioned by this repository. Pushing source code does not deploy the gateway.
