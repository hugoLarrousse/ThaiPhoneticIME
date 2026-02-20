# ThaiPhoneticIME — Custom Android IME (Compose) Spec + Step-by-step Tasks

## Context / Goal

I (Hugo) am learning Thai pronunciation using a custom phonetic/IPA-like alphabet that includes characters such as:
- ʉ ə ɛ ŋ ɔ
- plus various accent marks / diacritics (tone-like accents)

I want a **full custom Android keyboard (IME)**, built **for fun**, only for my personal device.

### Requirements

- Platform: **Windows 11**
- IDE: **Android Studio** (installed; project already created and running)
- UI: **Jetpack Compose** for the keyboard UI (no legacy KeyboardView)
- Android version: **Android 16** (API 36)
- SDK: **compileSdk = 36**, **targetSdk = 36**; project can use **minSdk = 36** (only for me)
- Keyboard functionality:
  - Base layout = **French AZERTY**
  - Add “missing letters” via **long-press variants** and/or alternate layers:
    - u → ʉ
    - e → ə, ɛ
    - n → ŋ
    - o → ɔ
  - Add “missing accents” via **long-press on the letter key** (commit **precomposed** characters when available)
  - No autocorrect, no prediction, no dictionary
  - Basic keys: shift, backspace, enter, space, symbols layer, maybe numbers layer
  - Works in any text field (WhatsApp/Notes/etc.)

### Accent inventory (explicit)

Accents required (visual forms on vowels):
- Acute: á é í ó ú
- Grave: à è ì ò ù
- Circumflex: â ê î ô û
- Caron: ǎ ě ǐ ǒ ǔ

These accents must be accessible via **long-press on the base letter key** (no separate dead-key row for MVP).

Base letters that must expose these long-press accent variants:
- a, e, i, o, u  (AZERTY base vowels)

Extra phonetic letters needed:
- ʉ ə ɛ ŋ ɔ

Accent availability on phonetic letters:
- Not required for MVP, but can be added later via combining marks if desired.

Implementation preference:
- **Show/commit precomposed characters** when available (e.g. ǎ, ě, ǐ, ǒ, ǔ).
- If a precomposed form does not exist for a future base+accent combo, fall back to combining marks.


### Non-goals (explicitly out of scope)

- Autocorrect / suggestions / next-word prediction
- Cloud sync
- Permissions beyond what an IME needs
- Fancy theming (later maybe)

### Tools already available

- GitHub Copilot installed and working in Android Studio
- Codex CLI available and working

---

## Definition of Done (MVP)

1. Keyboard is selectable as a system input method.
2. Pressing keys commits characters using `currentInputConnection.commitText(...)`.
3. Backspace deletes selection if present; otherwise deletes 1 char before cursor.
4. Space + Enter work.
5. A minimal row of special letters works: `ʉ ə ɛ ŋ ɔ`.
6. AZERTY base layer is implemented and usable.

---

## Key Technical Notes (for the implementing LLM)

- IME is an Android **Service** extending `InputMethodService`.
- Keyboard UI is a **ComposeView** returned from `onCreateInputView()`.
- Use `commitText` for text insertion. Avoid sending raw key events except for Enter (optional).
- For accents, prefer **precomposed characters** when available; fall back to Unicode combining marks only when needed.
  - Example: combining acute = U+0301
  - To type “á” you commit `"a\u0301"`.

---

## Tasks — Correct Order, Detailed

### Task 0 — Verify project baseline
- Confirm app runs in emulator/device.
- Confirm Gradle sync is clean.

Acceptance:
- App installs and launches.
- No build errors.

---

### Task 1 — Add IME Service class (Compose-based)
Create file:
- `app/src/main/java/<package>/PhoneticImeService.kt`

Implement:
- `class PhoneticImeService : InputMethodService()`
- override `onCreateInputView()` and return a `ComposeView`
- UI shows a minimal keyboard with:
  - special keys row: `ʉ ə ɛ ŋ ɔ`
  - bottom row: Space, Backspace, Enter

Core behavior:
- `commit(text: String)` → `currentInputConnection?.commitText(text, 1)`
- `backspace()` → `deleteSurroundingText(1,0)` (works with selection too)
- `enter()` → `sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)` or commit "\n" depending on field

Acceptance:
- Service compiles.

---

### Task 2 — Create IME metadata XML
Create directory/file:
- `app/src/main/res/xml/method.xml`

Contents (minimal):
```xml
<?xml version="1.0" encoding="utf-8"?>
<input-method xmlns:android="http://schemas.android.com/apk/res/android">
  <subtype
      android:label="Thai phonetic"
      android:imeSubtypeLocale="fr_FR"
      android:imeSubtypeMode="keyboard" />
</input-method>
```

Acceptance:
- Resource exists and is referenced by manifest later.

---

### Task 3 — Register IME service in AndroidManifest.xml
Modify:
- `app/src/main/AndroidManifest.xml`

Inside `<application>`, add:
```xml
<service
    android:name=".PhoneticImeService"
    android:label="Thai Phonetic IME"
    android:permission="android.permission.BIND_INPUT_METHOD"
    android:exported="true">
  <intent-filter>
    <action android:name="android.view.InputMethod" />
  </intent-filter>

  <meta-data
      android:name="android.view.im"
      android:resource="@xml/method" />
</service>
```

Acceptance:
- App builds.

---

### Task 4 — Install + enable the keyboard (manual verification steps)
On device/emulator:
1. Install the debug build.
2. Settings → System → Languages & input → On-screen keyboard → Manage keyboards
3. Enable **Thai Phonetic IME**
4. Open any text input (Notes) → switch keyboard → select Thai Phonetic IME
5. Type: `ʉəɛŋɔ` and confirm output

Acceptance:
- Keyboard appears in picker.
- Pressing keys inserts correct Unicode symbols.

---

### Task 5 — Refactor keyboard UI into data-driven layout
Goal: define keyboard rows as data, not hardcoded UI.

Create:
- `KeyboardModel.kt` (or similar) containing:
  - `sealed class KeyAction` (CommitText, Backspace, Shift, Space, Enter, ToggleLayer)
  - `data class KeySpec(label: String, action: KeyAction, widthWeight: Float = 1f, longPress: List<KeySpec>? = null)`
  - Layout definitions: `val azertyLayer`, `val symbolsLayer`, etc.

Update Compose UI:
- Render rows by iterating `List<List<KeySpec>>`
- Implement consistent key size + row spacing
- Use `Modifier.weight(...)` for variable key widths (spacebar etc.)

Acceptance:
- UI still works, but layout is driven by data structures.

---

### Task 6 — Implement AZERTY base layer (letters only first)
Implement the French AZERTY letter rows (no punctuation yet), e.g.:

Row 1: `a z e r t y u i o p`  
Row 2: `q s d f g h j k l m`  
Row 3: `w x c v b n`

Add:
- Backspace key on row 3 (right side)
- Space + Enter on bottom row
- Shift key on row 3 (left side) (logic can be stubbed first)

Acceptance:
- Can type normal AZERTY letters.
- Backspace works.

---

### Task 7 — Implement Shift state (basic)
In IME service (or a simple state holder):
- `var isShiftOn by mutableStateOf(false)`

Shift behavior:
- If `isShiftOn`, letters commit uppercase
- If `isShiftOn` is one-shot: after committing one letter, shift turns off (optional)
- Later: double-tap shift to lock (optional)

Acceptance:
- Shift toggles uppercase.
- Does not break special IPA letters.

---

### Task 8 — Add long-press variants for extra phonetic letters
Implement long-press popup UI in Compose:
- On long-press a key, show a small overlay row/box of variant keys
- Selecting a variant commits that character

Long-press mapping (MVP) for phonetic extras (vowel accents are handled in Task 9):
- `u` long-press → `u`, `ʉ`
- `e` long-press → `e`, `ə`, `ɛ`
- `n` long-press → `n`, `ŋ`
- `o` long-press → `o`, `ɔ`

Implementation notes:
- Use `pointerInput` with `detectTapGestures(onLongPress = ...)`
- Popup can be a `DropdownMenu` or custom `Box` overlay
- Ensure popup closes after selection

Acceptance:
- Long-press `u` can insert `ʉ`, etc.

---

### Task 9 — Add accents via long-press variants (precomposed)
Goal: long-press on `a e i o u` must show and commit these **precomposed** forms:

- `a` → `à á â ǎ`
- `e` → `è é ê ě`
- `i` → `ì í î ǐ`
- `o` → `ò ó ô ǒ`
- `u` → `ù ú û ǔ`

Implementation approach:
- In `KeySpec`, use `longPress: List<KeySpec>` for variants.
- For each vowel key, add the above variants (and optionally the base letter itself as the first item).

UX notes:
- Use long-press popup UI (Compose overlay / dropdown) that appears near the pressed key.
- Selecting a variant commits the chosen precomposed character with `commitText(...)`.
- No “dead-key pending accent” state for MVP.

Acceptance tests:
- Long-press `a` then choose `ǎ` → commits **ǎ**
- Long-press `o` then choose `ǒ` → commits **ǒ**
- Long-press `u` then choose `ǔ` → commits **ǔ**

---

### Task 10 — Add a Symbols/Numbers layer (minimal)
Implement a `KeyboardLayer` enum:
- `LETTERS`, `SYMBOLS`

Add a toggle key:
- `?123` switches to symbols
- `ABC` switches back

Symbols layer can be minimal:
- digits row
- punctuation row
- accent keys live here too (if not elsewhere)

Acceptance:
- Layer toggle works.
- No crashes.

---

### Task 11 — Polish MVP IME behavior
Implement common IME ergonomics:
- Repeat backspace on long-press (optional, but nice)
- Better delete behavior:
  - if there is selected text, delete selection
  - else delete 1 char
- Respect input type:
  - for password fields, still works (no prediction anyway)
- Keep app offline, no extra permissions

Acceptance:
- Keyboard is usable for daily typing.

---

## Suggested File/Folder Structure

- `app/src/main/java/<package>/`
  - `PhoneticImeService.kt`
  - `keyboard/KeyboardModel.kt`
  - `keyboard/KeyboardUi.kt`
  - `keyboard/PopupVariants.kt` (if separated)
- `app/src/main/res/xml/method.xml`
- `app/src/main/AndroidManifest.xml`

---

## Minimal MVP Code (Reference Snippets)

### `PhoneticImeService.kt` (skeleton reference)
Implement:
- `onCreateInputView()` returning `ComposeView`
- callbacks: commit/backspace/space/enter
- Compose keyboard UI uses data-driven `KeySpec`

(LLM should generate the full code in the project.)

---

## Manual Test Checklist

1. Enable keyboard in settings
2. Type normal AZERTY letters
3. Long-press and type: `ʉ ə ɛ ŋ ɔ`
4. Accents (long-press on vowel):
   - long-press `a` → pick **ǎ**
   - long-press `e` → pick **ě**
   - long-press `i` → pick **ǐ**
   - long-press `o` → pick **ǒ**
   - long-press `u` → pick **ǔ**
5. Backspace deletes correctly
6. Space and Enter work in multiple apps

---

## Future Ideas (Not required now)
- Shift lock (double-tap shift)
- One-handed mode
- Config screen Activity to edit long-press mappings
- Export/import mappings as JSON
- Haptic feedback toggle

---

## Implementation Constraints / Guidance
- Use Compose; do NOT use deprecated `KeyboardView`.
- Keep it minimal: no prediction, no network.
- Focus on correctness of Unicode + dead-key behavior.
- Prefer configuration-driven keyboard layout (rows + long-press maps + layers).
