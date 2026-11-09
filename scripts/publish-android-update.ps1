param()
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
git diff --quiet HEAD -- app scripts
if ($LASTEXITCODE -ne 0) { throw 'Commit Android changes before publishing.' }
git diff --quiet HEAD origin/main
if ($LASTEXITCODE -ne 0) { throw 'Push this commit to origin before publishing.' }
git diff --quiet origin/main yap/main
if ($LASTEXITCODE -ne 0) { throw 'Push matching trees to both repositories before publishing.' }
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio1/jbr'
$env:ANDROID_HOME = Join-Path $env:USERPROFILE 'Android'
$env:GIT_TERMINAL_PROMPT = '0'

& ./gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest --console=plain --offline
if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }
$config = Get-Content app/build.gradle.kts -Raw
$versionCode = [long]([regex]::Match($config, 'versionCode = (\d+)').Groups[1].Value)
$versionName = [regex]::Match($config, 'versionName = "([^"]+)"').Groups[1].Value
if (!$versionCode -or !$versionName) { throw 'Android version is missing.' }
$output = (Resolve-Path app/build/outputs/apk/release).Path
$signing = Join-Path $env:LOCALAPPDATA 'YAP/signing'
$signer = Join-Path $env:ANDROID_HOME 'build-tools/36.0.0/apksigner.bat'
$env:YAP_RELEASE_SIGNING_PASSWORD = [System.Net.NetworkCredential]::new('', (
    Get-Content (Join-Path $signing 'release-password.dpapi') | ConvertTo-SecureString
)).Password
try {
    & $signer sign --ks (Join-Path $signing 'yap-release.p12') --ks-key-alias yap-release --ks-pass env:YAP_RELEASE_SIGNING_PASSWORD --key-pass env:YAP_RELEASE_SIGNING_PASSWORD --out "$output/YAP-release.apk" "$output/app-release-unsigned.apk"
    if ($LASTEXITCODE -ne 0) { throw 'Signing failed.' }
    & $signer verify "$output/YAP-release.apk"
    if ($LASTEXITCODE -ne 0) { throw 'Signature verification failed.' }
} finally {
    Remove-Item Env:YAP_RELEASE_SIGNING_PASSWORD
}
$manifest = Join-Path $output 'update.json'
@{versionCode=$versionCode; versionName=$versionName} | ConvertTo-Json | Set-Content -LiteralPath $manifest -Encoding utf8NoBOM
$files = @(
    @{Path="$output/YAP-release.apk"; Name='YAP-release.apk'; Type='application/vnd.android.package-archive'},
    @{Path=(Resolve-Path app/build/outputs/apk/debug/app-debug.apk).Path; Name='YAP-update-debug.apk'; Type='application/vnd.android.package-archive'},
    @{Path=$manifest; Name='update.json'; Type='application/json'}
)
$targets = @(
    @{Repo='TOBIANDA/YAP'; Remote='yap'},
    @{Repo='EdgarJason/YAP-Final-Project-PAPB'; Remote='origin'}
)
foreach ($target in $targets) {
    $repo = $target.Repo
    $credential = "protocol=https`nhost=github.com`npath=$repo.git`nusername=TOBIANDA`n`n" | git credential fill
    $secret = ($credential | Where-Object { $_ -like 'password=*' } | Select-Object -First 1) -replace '^password=', ''
    if (!$secret) { throw 'GitHub credential unavailable.' }
    $headers = @{Authorization="Bearer $secret"; Accept='application/vnd.github+json'}
    $base = "https://api.github.com/repos/$repo/releases"
    $commit = (git rev-parse "$($target.Remote)/main").Trim()
    $body = @{
        tag_name="v$versionName"; target_commitish=$commit; name="YAP $versionName"; draft=$true
        body='Pembaruan langsung dari menu Pembaruan YAP. Pilih YAP-release.apk untuk instalasi baru, atau YAP-update-debug.apk untuk memperbarui instalasi debug.'
    } | ConvertTo-Json
    $releases = Invoke-RestMethod "${base}?per_page=30" -Headers $headers -TimeoutSec 30
    $release = $releases | Where-Object { $_.tag_name -eq "v$versionName" } | Select-Object -First 1
    if (!$release) {
        $release = Invoke-RestMethod $base -Method Post -Headers $headers -ContentType 'application/json' -Body $body -TimeoutSec 30
    }
    $upload = ($release.upload_url -split '\{')[0]
    foreach ($file in $files) {
        $hash = (Get-FileHash -LiteralPath $file.Path -Algorithm SHA256).Hash.ToLowerInvariant()
        $existing = $release.assets | Where-Object { $_.name -eq $file.Name } | Select-Object -First 1
        if ($existing) {
            if ($existing.digest -ne "sha256:$hash") { throw 'Existing release asset differs. Use a new version.' }
            continue
        }
        if (!$release.draft) { throw 'Published release is missing an asset. Use a new version.' }
        $asset = Invoke-RestMethod "${upload}?name=$($file.Name)" -Method Post -Headers $headers -ContentType $file.Type -InFile $file.Path -TimeoutSec 180
        if ($asset.state -ne 'uploaded' -or $asset.size -ne (Get-Item -LiteralPath $file.Path).Length -or $asset.digest -ne "sha256:$hash") {
            throw 'Uploaded asset verification failed. Release stays draft.'
        }
    }
    $published = Invoke-RestMethod "$base/$($release.id)" -Method Patch -Headers $headers -ContentType 'application/json' -Body '{"draft":false}' -TimeoutSec 30
    Write-Output $published.html_url
}
