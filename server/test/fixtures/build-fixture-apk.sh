#!/usr/bin/env bash
# Build server/test/fixtures/test-app.apk with the installed toolchain.
# Test keystore in /tmp only — NOT a real signing key.
# NOTE: aapt2 link does NOT package classes.dex; the dex produced by d8
# must be added to the APK explicitly (zip) before zipalign + apksigner.
# Regenerate with:  bash test/fixtures/build-fixture-apk.sh
set -euo pipefail
export JAVA_HOME="$HOME/workspace/yap-tools/jdk-21"
SDK="$HOME/workspace/yap-tools/android-sdk"
BT="$SDK/build-tools/34.0.0"
export PATH="$JAVA_HOME/bin:$BT:$PATH"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$SCRIPT_DIR/test-app.apk"
W=/tmp/yap-fixture-apk
rm -rf "$W"; mkdir -p "$W/src/com/example/fixture" "$W/res/values" "$W/assets" "$W/classes"
cat > "$W/AndroidManifest.xml" <<'EOF'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.fixture">
  <application android:label="Fixture Uji" android:theme="@android:style/Theme.Material.Light">
    <activity android:name=".MainActivity" android:exported="true">
      <intent-filter><action android:name="android.intent.action.MAIN"/>
      <category android:name="android.intent.category.LAUNCHER"/></intent-filter>
    </activity>
  </application>
</manifest>
EOF
cat > "$W/res/values/strings.xml" <<'EOF'
<resources><string name="app_name">Fixture Uji</string></resources>
EOF
cat > "$W/assets/index.html" <<'EOF'
<!doctype html><html><head><meta charset="utf-8"><title>Fixture Uji</title></head>
<body><h1>Fixture Uji</h1><p>preview-marker-7f3a9</p></body></html>
EOF
cat > "$W/src/com/example/fixture/MainActivity.java" <<'EOF'
package com.example.fixture;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
public class MainActivity extends Activity {
  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    WebView w = new WebView(this);
    w.getSettings().setJavaScriptEnabled(true);
    w.loadUrl("file:///android_asset/index.html");
    setContentView(w);
  }
}
EOF
cd "$W"
javac --release 17 -cp "$SDK/platforms/android-34/android.jar" -d classes src/com/example/fixture/MainActivity.java
d8 --output . classes/com/example/fixture/MainActivity.class
aapt2 compile --dir res -o compiled.zip
aapt2 link -o app-unsigned.apk --manifest AndroidManifest.xml -I "$SDK/platforms/android-34/android.jar" -A assets compiled.zip
zip -q app-unsigned.apk classes.dex
zipalign -f 4 app-unsigned.apk app-aligned.apk
keytool -genkeypair -keystore test.keystore -storepass testpass123 -keypass testpass123 -alias fixture-test -keyalg RSA -keysize 2048 -validity 365 -dname "CN=Fixture Test, O=YAP Test, C=ID" -noprompt
apksigner sign --ks test.keystore --ks-pass pass:testpass123 --key-pass pass:testpass123 --out app-signed.apk app-aligned.apk
apksigner verify --verbose app-signed.apk
unzip -l app-signed.apk | grep -q 'classes.dex' || { echo 'fixture APK missing classes.dex' >&2; exit 1; }
unzip -l app-signed.apk | grep -q 'assets/index.html' || { echo 'fixture APK missing assets/index.html' >&2; exit 1; }
cp app-signed.apk "$OUT"
aapt2 dump badging "$OUT" | head -3
echo "FIXTURE-OK size=$(stat -c%s "$OUT") sha256=$(sha256sum "$OUT" | cut -d' ' -f1)"
