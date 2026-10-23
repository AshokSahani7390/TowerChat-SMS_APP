# Towerchat — SMS-Native Cellular Messenger (Android)

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

## 📱 Permissions

| Permission | Purpose | Runtime? |
|---|---|---|
| `android.permission.SEND_SMS` | Dispatches single and multipart SMS messages | Yes |
| `android.permission.RECEIVE_SMS` | Intercepts incoming messages | Yes |
| `android.permission.READ_PHONE_STATE` | Auto-detects user SIM phone number | Yes |
| `android.permission.READ_CONTACTS` | Native Contact Picker integration | Yes |
| `android.permission.POST_NOTIFICATIONS` | Notification alerts (Android 13+) | Yes |
| `android.permission.FOREGROUND_SERVICE` | Keeps cellular listener active in background | No |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Restarts background listener on device reboot | No |

---

## 🛠️ Building & Running

### Requirements
- **JDK**: Java 21 LTS (or Android Studio JBR)
- **Android SDK**: API 26 minimum (Android 8.0 Oreo), API 35 target

### Commands
```bash
# Run All Unit & Cryptographic Tests
./gradlew.bat testDebugUnitTest

# Assemble Debug APK
./gradlew.bat assembleDebug
```
APK output directory: `app/build/outputs/apk/debug/app-debug.apk`
