---
name: yap-apk-quality
description: Build and verify a polished Android APK for a task claimed from YAP.
---

Treat the claimed prompt as the application specification. Keep the existing YAP connector, lease, and upload workflow. Work in a new directory for each job ID so earlier APKs and HTML cannot accidentally be reused.

## Skills to load

Read the three required skills for each job. Fetch their pinned Markdown and relevant companion references from the same revision, and reuse a local cache. Apply upstream guides within this build's scope. Use the tools already available in the VM and keep installation, publishing, and agent configuration steps outside the job.

- **Required — [Impeccable](https://github.com/pbakaus/impeccable/blob/f864521bbb1c9350e8f6cf7742c139a04a1020af/.agents/skills/impeccable/SKILL.md).** Set a visual direction and use its critique and polish guidance before packaging.
- **Required — [Ask Matt](https://github.com/mattpocock/skills/blob/4ae2317ddb22b046e26455e4f9c9b3aaaf62212d/skills/engineering/ask-matt/SKILL.md).** Use its engineering skill routing to plan, clarify, or debug the work. Route any user-facing question through YAP's clarification endpoint.
- **Required — [Anti Slop](https://github.com/miqdadbadjuber/anti-slop/blob/941b1532f9cc8ffde766bca5921d75257c9e5762/skills/antislop/SKILL.md).** Use its `during` mode as the YAP project default. Apply it while planning and implementing, then check for generic layouts, filler copy, placeholder interactions, and needless code before packaging. Use the job's design brief as direction and ask through YAP only when a missing decision materially affects the app.
- **For WebView and web UI — [Vercel Web Interface Guidelines](https://github.com/vercel-labs/web-interface-guidelines/blob/434b7f91364665f2f733b310ec54809bf8f37937/command.md).** Check accessibility, keyboard behavior, forms, and responsive layout.
- **For runnable web builds — [Anthropic Web App Testing](https://github.com/anthropics/skills/blob/ca45fc496a3436251f191354091d557da31c15b5/skills/webapp-testing/SKILL.md).** Use Playwright when available to exercise the actual app in a browser before packaging.
- **For multi-screen flows — [UX Audit](https://github.com/Flagrare/agent-skills/blob/0d6f99db2c7da5d7df4532ca62a6ea9fb9db2ca0/plugins/flagrare/skills/ux-audit/SKILL.md).** Walk the main flow as a first-time user with the available browser or emulator. Check unclear labels, dead ends, and recovery from empty or error states.
- **For bugs and failed checks — [Systematic Debugging](https://github.com/obra/superpowers/blob/22f0b463e7a5e579904dd6211a520321035526f7/skills/systematic-debugging/SKILL.md).** Reproduce the failure and isolate its cause before changing the code.
- **Before delivery — [Verification Before Completion](https://github.com/obra/superpowers/blob/22f0b463e7a5e579904dd6211a520321035526f7/skills/verification-before-completion/SKILL.md).** Run fresh checks on the final artifact and report what the results actually cover.

## Additional references

Select the references that fit the task. For a new UI, use one primary visual direction and treat the other design guides as checks or inspiration. The required skills still apply. Keep working notes with the job ID so they can be recovered after a clarification. Apply the Markdown as guidance within the existing worker and toolchain.

### UI and UX

- [Frontend Design](https://github.com/anthropics/skills/blob/ca45fc496a3436251f191354091d557da31c15b5/skills/frontend-design/SKILL.md). Choose visual direction for a new UI.
- [UI UX Pro Max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill/blob/dd9a1b4cf9f67ca2e326f646d93e101b2778db30/.claude/skills/ui-ux-pro-max/SKILL.md). Choose typography, colors, and interaction patterns for the product category.
- [gstack Design Review](https://github.com/garrytan/gstack/blob/ca90f0ff2df501f5caca9a2ba9cfd40405f48f0a/design-review/SKILL.md). Review rendered screens when browser or screenshots are available.
- [Awesome DESIGN.md](https://github.com/VoltAgent/awesome-design-md/blob/fcc70284932276d085f01ef9f83edde90b1258cb/README.md). Pick a relevant design reference and adapt its principles to the brief.
- [Taste Skill](https://github.com/Leonxlnx/taste-skill/blob/12af471f627f866411b4bd785b19646d3672784c/skills/taste-skill/SKILL.md). Refine visual hierarchy and interaction when the brief needs a stronger visual identity.

### Engineering and QA

- [Writing Plans](https://github.com/obra/superpowers/blob/22f0b463e7a5e579904dd6211a520321035526f7/skills/writing-plans/SKILL.md). Break multi-step work into verifiable tasks.
- [ECC Security Review](https://github.com/affaan-m/ECC/blob/b38be6a7f6350964d59328b52f0624e1d1d552d1/.agents/skills/security-review/SKILL.md). Review builds that handle authentication, sensitive data, network requests, or untrusted input.
- [Karpathy Guidelines](https://github.com/multica-ai/andrej-karpathy-skills/blob/2c606141936f1eeef17fa3043a72095b4765b9c2/skills/karpathy-guidelines/SKILL.md). Keep implementation simple and tie changes to verifiable goals.
- [Agent Browser](https://github.com/vercel-labs/agent-browser/blob/6682da310c75bed8af340b39cc10b7f71bded155/skills/agent-browser/SKILL.md). Exercise actual UI flows when the browser and CLI are available.
- [Vercel React Best Practices](https://github.com/vercel-labs/agent-skills/blob/1a2c79d4e6c4a2283dc2c614b6538cbe1ed5ca62/skills/react-best-practices/SKILL.md). Review performance when the generated project uses React or Next.js.
- [Planning with Files](https://github.com/OthmanAdi/planning-with-files/blob/0fa6c79885e14534074cb7df8535e1efa3dc7149/.agents/skills/planning-with-files/SKILL.md). Keep plans, findings, and progress in the job directory for long builds or jobs waiting on a reply.
- [Trellis](https://github.com/mindfold-ai/Trellis/blob/f089cb3286071199e84118dee86b6d76aab032f1/README.md). Use its spec and cross-session workflow as a reference for large projects.

## Build workflow

Apply the full clarification history returned by claim. Ask one focused question in the user's language if a missing decision materially affects what the app does. Include options or a sensible default when helpful. Use `PUT /api/build/{id}/question` with the active lease, then release the session's work on that job. Resume it when the gateway supplies the answer on a new claim. Make ordinary styling choices directly.

1. **Define what must work.** Turn the prompt into a short checklist of visible screens, core actions, and expected results. Use the prompt's language and domain. Fill ordinary design gaps with reasonable choices so the job can run unattended.
2. **Design for the phone.** Give the core action a clear place on the first screen. Use readable type, consistent spacing, touch targets at least 44 CSS px, sufficient contrast, visible focus, and a viewport that fits a narrow phone without sideways scrolling. Cover empty, invalid, and completed states when relevant. Use a visual direction that fits the request. For a tracker, for example, prioritize the current value and primary action before history. Avoid generic filler, placeholder buttons, and unnecessary libraries.
3. **Implement the actual behavior.** For a self-contained WebView app, use local HTML, CSS, and JavaScript and keep all needed assets in the APK. Choose native code when the requested feature needs Android capabilities the WebView cannot provide. Only request permissions needed by that feature. Keep user-entered data local unless the requested app requires a network integration and its configuration is available.
4. **Test before building.** Exercise every main action with representative inputs and at least one applicable edge case. Check a narrow viewport and fix obvious layout defects. Use a browser or emulator when available. Otherwise, run the strongest available checks and keep track of what they did and did not verify. Reading the source alone is not evidence that a button works.
5. **Build and verify.** Use the Android SDK already prepared in Secure VM. Confirm the APK opens the expected activity, includes this job's assets, and passes `apksigner verify`. When Android is available, launch the built APK and run a smoke test. Fix defects before uploading. If a requirement cannot be met, report the limitation instead of claiming it works.
6. **Deliver.** Upload this job's signed APK through the existing connector with its own ID and lease. If no usable artifact can be built, report the failure through the worker. Keep tokens and private keys out of source, logs, and output. Do not publish generated source or change the YAP repo from a build job.
