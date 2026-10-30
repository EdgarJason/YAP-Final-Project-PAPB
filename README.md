# YAP-Final-Project-PAPB

Android AI web app builder using Kotlin and Jetpack Compose. Chat Studio generates and revises standalone web applications with **Muse Spark 1.3**, then displays the resulting HTML in Preview and Code.

The provider API key belongs on the server, never in the APK. Set up and deploy the [Muse gateway](server/README.md), then enter its HTTPS origin and a separate YAP access token in **Pengaturan AI**. A VPS is optional; the gateway can run as a serverless function.

Build Android with JDK 17+ and Android SDK 37: `./gradlew :app:assembleDebug :app:testDebugUnitTest`. Run gateway checks from `server/` with `npm test` (Node.js 22.9+).

Account screens and chat history are still UI prototypes; generating an app does not run a real multi-agent audit or automated browser test.
