# Towerchat — SMS-Native Cellular Messenger & Dialer (Android)

> **"Towerchat is a UI layer. The carrier network is the backend."**

Towerchat is an ultra-lean, cellular-native Android messaging and voice communication app designed to operate without internet, Wi-Fi, or cloud servers. Built with **Kotlin**, **Jetpack Compose**, and **Hilt**, it treats the cellular signaling channel as its transport layer and the carrier network as its backend.

---

## 🔒 Security Architecture & Cryptographic Engine

- **End-to-End Encryption (E2EE)**:
  - **Key Exchange**: Ephemeral Elliptic Curve Diffie-Hellman (**ECDH** on curve `secp256r1`) key agreement exchanged via initial SMS handshake with `TC_HANDSHAKE:` prefix.
  - **Message Encryption**: Authenticated **AES-256-GCM** encryption per message with a unique 12-byte random nonce and 128-bit authentication tag (`TC_MSG:` prefix). Carrier sees only Base64 ciphertext.
  - **Authentication & Integrity**: Messages failing GCM authentication are rejected to protect against tampering or active bit-flipping in transit.
- **Identity Verification & Anti-SIM Swap**:
  - **12-Digit Safety Numbers**: Lexicographically sorted SHA-256 fingerprint generated from both public keys, formatted as 4 triplets (e.g., `047 183 291 504`) for verbal verification over a voice call.
  - **Key Change Detection**: Instant alert banner if a contact's public key changes mid-conversation, warning of possible SIM swap or MITM attacks.
- **Device & Application Hardening**:
  - **Screenshot & Recording Block**: `WindowManager.LayoutParams.FLAG_SECURE` applied to prevent screenshots, screen recording, and exposure in recent task switcher thumbnails.
  - **Zero Cleartext Traffic**: `network_security_config.xml` explicitly sets `cleartextTrafficPermitted="false"`.
  - **ProGuard Log Stripping**: Automatically strips all `android.util.Log` calls in release builds to eliminate logcat data leakage.
  - **Component Protection**: Strict `android:exported="false"` on non-essential components and `BROADCAST_SMS` permission protection on incoming SMS receivers.
  - **RAM-Only Persistence**: Messages live in volatile memory and are cleared when the session ends or app process terminates (`allowBackup="false"` and `data_extraction_rules.xml` active).

---

## 📞 Cellular Voice Calling & Native Dialer

- **Calls Tab**:
  - **Keypad & T9 Smart Dial**: Interactive dialpad matching digits in real-time against contact names and phone numbers.
  - **Recents**: Reads system call history on demand via `CallLog.Calls`, grouping consecutive records by contact and date with call direction icons, duration, timestamps, and direct "Call Back" or E2EE "Message" shortcuts.
  - **Favorites & Contacts**: Displays starred and all contacts directly from `ContactsContract` with instant search and alphabetical grouping.
  - **Dual-SIM Selection**: Interactive account picker on multi-SIM devices with carrier name, slot index, and persistent default selection.
- **Default Dialer & In-Call Management**:
  - **Dialer Role Integration**: Native role requests via `RoleManager.ROLE_DIALER` (API 29+) and `TelecomManager.ACTION_CHANGE_DEFAULT_DIALER` (API 26–28), with graceful fallback to `Intent.ACTION_DIAL` when unassigned.
  - **In-Call UI**: Comprehensive in-call activity (`InCallActivity`) waking and showing over lock screen (`showWhenLocked`, `turnScreenOn`).
  - **In-Call Controls**: Mute microphone, speakerphone, hold, DTMF dialpad sheet, add second call, swap calls, merge conference calls, and disconnect.
  - **Audio Routing**: Adaptive audio routing supporting earpiece, speakerphone, wired headset, and Bluetooth headsets (branching between `CallAudioState` on API 26–33 and `CallEndpoint` on API 34+).
  - **Proximity Sensor**: Acquires `PROXIMITY_SCREEN_OFF_WAKE_LOCK` during earpiece audio route to prevent accidental touch inputs when held to ear.
  - **Call Waiting**: Banner for incoming second calls offering "Hold & Answer", "End & Answer", and "Decline".
  - **Quick SMS Reject**: Canned decline templates sent directly via standard `SmsManager` (zero crypto impact).
  - **Missed Call Notifications**: Heads-up alerts with direct "Call Back" and "Message" intent actions.
  - **Zero-Bypass Emergency Calls**: Standard emergency codes (112, 911, 100, 101, 102, 108) and carrier emergency numbers bypass all confirmation dialogs, SIM selection, and custom logic straight to `TelecomManager.placeCall()`.
  - **Number Blocking**: System-wide blocking and unblocking powered by `BlockedNumberContract` (when designated default dialer) with dedicated Blocked Numbers screen and call detail toggle.
  - **Theming & Accessibility**: Automatic system dark/light theme switching, TalkBack accessibility content descriptions, and minimum 48dp touch targets on all interactive controls.

---

## 📱 Permissions

| Permission | Purpose | Runtime? |
|---|---|---|
| `android.permission.SEND_SMS` | Dispatches single and multipart SMS messages | Yes |
| `android.permission.RECEIVE_SMS` | Intercepts incoming messages | Yes |
| `android.permission.READ_PHONE_STATE` | Auto-detects user SIM phone number and cellular network state | Yes |
| `android.permission.CALL_PHONE` | Initiates cellular voice calls directly via TelecomManager | Yes |
| `android.permission.READ_CONTACTS` | Native Contact Picker, Favorites, and T9 Smart Dial search | Yes |
| `android.permission.READ_CALL_LOG` | Reads system call history on demand for the Recents tab | Yes |
| `android.permission.WRITE_CALL_LOG` | Manages and updates call history entries | Yes |
| `android.permission.POST_NOTIFICATIONS` | Notification alerts, incoming call heads-up, and missed call notices (Android 13+) | Yes |
| `android.permission.USE_FULL_SCREEN_INTENT` | Launches incoming call UI over the lock screen (Android 14+) | No (Special/Install) |
| `android.permission.FOREGROUND_SERVICE` | Keeps cellular SMS listener and InCallService active in background | No |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | Special-use background listener declaration for Android 14+ | No |
| `android.permission.WAKE_LOCK` | Powers proximity sensor screen-off during active calls | No |
| `android.permission.VIBRATE` | Haptic feedback for incoming ringtone and dialpad button presses | No |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Restarts background listener on device reboot | No |

---

## 🛠️ Building & Running

### Requirements
- **JDK**: Java 21 LTS (or Android Studio JBR)
- **Android SDK**: API 26 minimum (Android 8.0 Oreo), API 35 target

### Commands
```bash
# Run All Unit, Telephony & Cryptographic Tests
./gradlew.bat testDebugUnitTest

# Assemble Debug APK
./gradlew.bat assembleDebug
```
APK output directory: `app/build/outputs/apk/debug/app-debug.apk`
