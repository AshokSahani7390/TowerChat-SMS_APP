# Changelog

All notable changes to the Towerchat project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.1.0] - 2026-11-02

### Added
- **Calls Tab & Navigation**:
  - Interactive Keypad supporting touch digits and T9 Smart Dial searching contacts by name and phone number.
  - Recents list reading system `CallLog.Calls` with consecutive call grouping by contact and date, call direction icons (incoming, outgoing, missed), duration, timestamps, and direct "Call Back" and "Message" shortcuts.
  - Favorites screen showing starred contacts from `ContactsContract`.
  - Contacts list showing full device contacts with instant search and alphabetical grouping.
  - Dual-SIM selection dialog for multi-SIM devices with carrier name, slot index, and persistent default selection.
- **Default Dialer & InCallService**:
  - InCallService implementation (`TowerInCallService`) registered with `BIND_INCALL_SERVICE` and in-call UI metadata.
  - System default dialer role management via `RoleManager.ROLE_DIALER` on Android 10+ (API 29+) and `TelecomManager.ACTION_CHANGE_DEFAULT_DIALER` on API 26–28.
  - Fallback delegation to `Intent.ACTION_DIAL` when default dialer role is declined or revoked.
- **In-Call UI & Controls (`InCallActivity`)**:
  - Wake and show over lock screen (`showWhenLocked`, `turnScreenOn`) with `FLAG_SECURE` screen capture protection.
  - Full-screen incoming call notifications via `USE_FULL_SCREEN_INTENT` on high-priority telephony channel.
  - In-call controls: Mute microphone, toggle speakerphone, hold call, DTMF keypad sheet, add call, swap calls, and merge conference calls.
  - Adaptive audio routing covering earpiece, speaker, wired headset, and Bluetooth headsets with version branching (`CallAudioState` on API 26–33, `CallEndpoint` on API 34+).
  - Proximity sensor wake lock (`PROXIMITY_SCREEN_OFF_WAKE_LOCK`) turning display off when earpiece is brought to ear.
  - Call waiting banner for incoming second call ("Hold & Answer", "End & Answer", "Decline").
  - Quick SMS reject sending canned text templates via plain `SmsManager` without cryptographic overhead.
  - Missed call heads-up notifications with one-tap "Call Back" and E2EE "Message" actions.
- **Emergency Call Zero-Bypass**:
  - Direct routing of emergency numbers (112, 911, 100, 101, 102, 108) directly to `TelecomManager.placeCall()` without confirmation dialogs, SIM pickers, or custom delay.
- **Number Blocking (`BlockedNumberContract`)**:
  - System-wide number blocking and unblocking via `BlockedNumberContract` when TowerChat holds default dialer status.
  - Dedicated Blocked Numbers management screen with manual add and unblock dialogs.
  - Block/Unblock toggle integrated directly into Call History Detail screen.
- **Theming & Accessibility**:
  - Dynamic system dark and light theme switching.
  - Minimum 48dp touch targets on all interactive keypad, call action, and list controls.
  - TalkBack accessibility content descriptions on all UI elements.
- **Compatibility & Edge Case Test Suite**:
  - Comprehensive unit test suite covering API 26, 29, 31, 34, 35 branches, OEM background restrictions (MIUI, One UI, ColorOS), single & dual SIM, Bluetooth audio routing, call waiting, airplane mode, role revocation, and screen rotation state preservation.

---

## [1.0.0] - 2026-10-23

### Added
- Initial release of Towerchat SMS-native cellular messenger.
- Ephemeral Elliptic Curve Diffie-Hellman (**ECDH** on `secp256r1`) key exchange over initial SMS handshake (`TC_HANDSHAKE:`).
- Authenticated **AES-256-GCM** per-message encryption with 12-byte random nonces and 128-bit authentication tags (`TC_MSG:`).
- 12-digit safety number fingerprint generation for in-person or verbal verification.
- Anti-SIM swap and public key change detection banners.
- RAM-only persistence model: no messages or session keys written to flash or SharedPreferences.
- `FLAG_SECURE` window protection against screenshots and screen recording.
- Offline cellular quota tracker for daily carrier SMS limits.
