---
name: yap-apk-quality
description: Build and verify a polished Android APK for a task claimed from YAP.
---

Treat the claimed prompt as the application specification. Keep the existing YAP connector, lease, and upload workflow. Work in a new directory for each job ID so earlier APKs and HTML cannot accidentally be reused.

## Skills to load

Read the two required skills for each job. Fetch their pinned Markdown and relevant companion references from the same revision, and reuse a local cache. Use the guides within this build's scope and inspect any upstream helper before executing it.

- **Required — [Impeccable](https://github.com/pbakaus/impeccable/blob/f864521bbb1c9350e8f6cf7742c139a04a1020af/.agents/skills/impeccable/SKILL.md).** Set a visual direction and use its critique and polish guidance before packaging.
- **Required — [Ask Matt](https://github.com/mattpocock/skills/blob/4ae2317ddb22b046e26455e4f9c9b3aaaf62212d/skills/engineering/ask-matt/SKILL.md).** Use its engineering skill routing to plan, clarify, or debug the work. Route any user-facing question through YAP's clarification endpoint.
- **For WebView and web UI — [Vercel Web Interface Guidelines](https://github.com/vercel-labs/web-interface-guidelines/blob/434b7f91364665f2f733b310ec54809bf8f37937/command.md).** Check accessibility, keyboard behavior, forms, and responsive layout.
- **For runnable web builds — [Anthropic Web App Testing](https://github.com/anthropics/skills/blob/ca45fc496a3436251f191354091d557da31c15b5/skills/webapp-testing/SKILL.md).** Use Playwright when available to exercise the actual app in a browser before packaging.

## Build workflow

Apply the full clarification history returned by claim. Ask one focused question in the user's language if a missing decision materially affects what the app does. Include options or a sensible default when helpful. Use `PUT /api/build/{id}/question` with the active lease, then release the session's work on that job. Resume it when the gateway supplies the answer on a new claim. Make ordinary styling choices directly.

1. **Define what must work.** Turn the prompt into a short checklist of visible screens, core actions, and expected results. Use the prompt's language and domain. Fill ordinary design gaps with reasonable choices so the job can run unattended.
2. **Design for the phone.** Give the core action a clear place on the first screen. Use readable type, consistent spacing, touch targets at least 44 CSS px, sufficient contrast, visible focus, and a viewport that fits a narrow phone without sideways scrolling. Cover empty, invalid, and completed states when relevant. Use a visual direction that fits the request. For a tracker, for example, prioritize the current value and primary action before history. Avoid generic filler, placeholder buttons, and unnecessary libraries.
3. **Implement the actual behavior.** For a self-contained WebView app, use local HTML, CSS, and JavaScript and keep all needed assets in the APK. Choose native code when the requested feature needs Android capabilities the WebView cannot provide. Only request permissions needed by that feature. Keep user-entered data local unless the requested app requires a network integration and its configuration is available.
4. **Test before building.** Exercise every main action with representative inputs and at least one applicable edge case. Check a narrow viewport and fix obvious layout defects. Use a browser or emulator when available. Otherwise, run the strongest available checks and keep track of what they did and did not verify. Reading the source alone is not evidence that a button works.
5. **Build and verify.** Use the Android SDK already prepared in Secure VM. Confirm the APK opens the expected activity, includes this job's assets, and passes `apksigner verify`. When Android is available, launch the built APK and run a smoke test. Fix defects before uploading. If a requirement cannot be met, report the limitation instead of claiming it works.
6. **Deliver.** Upload this job's signed APK through the existing connector with its own ID and lease. If no usable artifact can be built, report the failure through the worker. Keep tokens and private keys out of source, logs, and output. Do not publish generated source or change the YAP repo from a build job.
