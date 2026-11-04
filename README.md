# YAP-Final-Project-PAPB

Android app builder using Kotlin and Jetpack Compose. Send an app idea from Chat Studio to **Muse AI** and download the APK built in the Muse Secure VM.

Set up the [YAP gateway and Muse hook](server/MUSE-APK.md), then enter the gateway's HTTPS origin and the YAP access token in **Pengaturan AI**.

Build Android with JDK 17+ and Android SDK 37: `./gradlew :app:assembleDebug :app:testDebugUnitTest`. Run gateway checks from `server/` with `npm test` (Node.js 22.9+).

Account screens and chat history are still UI prototypes; generating an app does not run a real multi-agent audit or automated browser test.
