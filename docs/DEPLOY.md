# Deploying a New Version of Thai Phonetic IME

## Important rule: always deploy as a **release** build

Debug builds (the default when clicking ▶ in Android Studio) are marked `testOnly` by Android and can be silently disabled by the system after a while. Always use release builds for daily use.

---

## Option A — Command line (fastest)

```powershell
# From the project root
.\gradlew.bat assembleRelease

# Install on connected phone (USB debugging must be on)
adb install -r app\build\outputs\apk\release\app-release.apk
```

The `-r` flag replaces the existing installation without uninstalling first (preserves settings).

If the phone asks "Allow USB debugging?" tap **Always allow**.

---

## Option B — Android Studio UI

1. Open the **Build Variants** panel (bottom-left of Android Studio, or **View → Tool Windows → Build Variants**).
2. Change `app` row from `debug` to **`release`**.
3. Connect your phone via USB.
4. Click the green **▶ Run** button (or **Shift+F10**).

Android Studio will build and install the release APK directly.

> Remember to switch back to `debug` if you want to use breakpoints / live debugging afterwards.

---

## Bumping the version (good practice before each deploy)

Edit [app/build.gradle.kts](../app/build.gradle.kts):

```kotlin
defaultConfig {
    versionCode = 2          // increment by 1 each time
    versionName = "1.1"      // human-readable version shown in Settings
}
```

`versionCode` must always increase. `versionName` is only for display.

---

## After installing, verify it works

1. **Settings → System → Languages & Input → On-screen keyboard** — confirm "Thai Phonetic IME" is listed and enabled.
2. Open any text field and switch to the keyboard.
3. If it doesn't appear in the list after reinstalling, go to **Settings → System → Keyboard → Manage keyboards** and toggle it on again.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `adb: device not found` | Enable **Developer options** on phone, turn on **USB debugging**, accept the prompt on the phone |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Uninstall the old version first: `adb uninstall com.example.thaiphoneticime` |
| Keyboard disappears from the list after a few days | You deployed a debug build — use release as above |
| Phone not recognised by `adb` | Run `adb devices` and check; try a different USB cable or port |
