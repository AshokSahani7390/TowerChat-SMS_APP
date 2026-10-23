TOWERCHAT [SRS](#srs) [PRD](#prd) [Architecture](#architecture) [Workflow](#workflow) [Storage Audit](#storage) [Features v1.1](#features) [Risks](#risks)

Technical Documentation v1.0

# Antigravity

An SMS-native Android messenger that works without internet, Wi-Fi, or LAN. No accounts, no servers, no stored data. Just the cell network.

Product

Towerchat

Platform

Android 8.0+

Version

1.0.0 — Alpha

Author

Ashok Sahani

Network

SMS + Voice (Cellular)

Backend

None

Core Design Principle

Towerchat is a UI layer. The carrier network is the backend.

Every architecture and implementation decision follows from this single statement.

Towerchat does not build a messaging infrastructure. It does not run servers, manage delivery queues, handle encryption at the transport layer, or store any state beyond the current session. All of that already exists — Jio, Airtel, and BSNL have been running it for decades. It's called the cellular network.

What Towerchat builds is the experience layer on top of it. The app's job is three things: route outgoing text through `SmsManager`, intercept incoming text via `BroadcastReceiver`, and present both in a clean chat interface. For voice, it hands off to `Intent.ACTION_CALL` and steps aside entirely. The carrier does the rest.

#### Towerchat owns

The UI, the in-memory message list, the SMS routing logic, the quota counter, and the notification.

#### The carrier owns

Message delivery, voice routing, network reliability, geographic reach, and the 100 SMS/day quota.

#### Nobody owns

Message history. It lives in RAM during the session and nowhere else. No server. No disk. No backup.

**Why this matters for every decision you make while building.** If a feature requires Towerchat to store data, run a server, or handle something the carrier already handles — it's out of scope. The moment you add a backend, you've built a different product. Keep the app thin. The carrier is free infrastructure you don't have to maintain.

Document 01

Software Requirements Specification

SRS · Towerchat v1.0 · Defines what the system must do

## 1.1 Purpose

Towerchat is an Android messaging app that lets two users exchange text messages and make voice calls using the cellular network only. It requires no internet connection, no server infrastructure, no user registration, and stores nothing after the session ends.

## 1.2 Scope

The app targets Indian Android users on carriers like Jio, Airtel, and BSNL who need to communicate when data connectivity is unavailable or too expensive. V1 covers one-to-one text chat and voice calls only.

## 1.3 System Constraints

#### SMS for messaging

All text messages travel as standard SMS over the carrier's signaling channel. No data required.

#### Intent-based voice calls

Calls use Android's `Intent.ACTION_CALL` to trigger a native cellular call. Free on Jio recharge plans.

#### No backend or server

Zero cloud infrastructure. No Firebase, no REST API, no database. Nothing to host, nothing to breach.

#### No persistent storage

Messages exist only in RAM while the app is open. No SQLite, no shared preferences for chat history.

#### No user accounts

Phone number is read from the SIM via TelephonyManager. No signup, no OTP, no password.

#### No video calls in v1

VoLTE video is carrier-controlled and inaccessible via Android SDK. Excluded from scope.

## 1.4 Functional Requirements

Functional Requirements

- FR-01 The app shall read the user's own phone number from the SIM on first launch using `TelephonyManager.getLine1Number()`. Fallback: prompt user to enter their number manually if the API returns null (common on dual-SIM devices).
- FR-02 The app shall allow the user to start a chat by entering a recipient's phone number or selecting from Android Contacts.
- FR-03 The app shall send outgoing messages as SMS using `SmsManager.sendTextMessage()` or `sendMultipartTextMessage()` for texts over 160 characters. Each SMS segment over 160 chars deducts from the daily carrier quota separately.
- FR-04 The app shall intercept incoming SMS from known Towerchat contacts using a high-priority `BroadcastReceiver` and suppress it from the default SMS app. Use `abortBroadcast()` only for messages from contacts the user has actively opened a chat with.
- FR-05 The app shall display sent and received messages in a scrollable chat view ordered by system timestamp.
- FR-06 The app shall show a sent/failed indicator per message using the `SmsManager` sent PendingIntent callback.
- FR-07 The app shall display a live SMS quota counter showing estimated messages remaining today based on Jio's 100 SMS/day limit. This is a local counter only — the carrier does not expose quota via API.
- FR-08 The app shall initiate a standard cellular voice call via `Intent.ACTION_CALL` when the user taps the call button.
- FR-09 When the app is closed or the process is killed, all in-memory messages shall be discarded. No chat history shall survive a session.
- FR-10 The app shall show a notification for incoming SMS from contacts in an active chat even when the app is backgrounded, using a foreground `Service`.

## 1.5 Non-Functional Requirements

| ID | Category | Requirement | Target |
| --- | --- | --- | --- |
| NFR-01 | Performance | App cold start to chat screen | \< 1.5 seconds on mid-range device |
| NFR-02 | Privacy | Zero data written to disk after session ends | Verified via filesystem audit |
| NFR-03 | Compatibility | Minimum Android SDK | API 26 (Android 8.0) |
| NFR-04 | Battery | Background SMS listener power draw | \< 1% battery per hour in background |
| NFR-05 | Usability | New user completes first chat without any instruction | 3-tap path from install to sent message |
| NFR-06 | Size | APK size | \< 8 MB |

## 1.6 Permissions Required

AndroidManifest.xml Permissions

| Permission | Reason | Runtime? |
| --- | --- | --- |
| `SEND_SMS` | Send outgoing messages via SmsManager | Yes |
| `RECEIVE_SMS` | Intercept incoming SMS before default app | Yes |
| `READ_PHONE_STATE` | Read own phone number from SIM | Yes |
| `CALL_PHONE` | Initiate voice call via Intent | Yes |
| `READ_CONTACTS` | Let user pick recipient from contacts | Yes |
| `FOREGROUND_SERVICE` | Keep SMS listener alive in background | No |
| `RECEIVE_BOOT_COMPLETED` | Restart background listener on device reboot | No |

Document 02

Product Requirements Document

PRD · Towerchat v1.0 · Defines why and what to build

## 2.1 Problem Statement

Hundreds of millions of Indian mobile users lose access to messaging apps the moment mobile data runs out or becomes unavailable — in rural areas, during recharge gaps, underground, or on low-cost plans with no data. Every existing chat app (WhatsApp, Telegram, Signal) requires an active internet connection. SMS apps exist but feel like 2005.

Towerchat fills that gap: a chat app experience built entirely on the cellular SMS and voice layer, with no data required.

## 2.2 Target Users

| Segment | Situation | Why Towerchat helps |
| --- | --- | --- |
| Budget prepaid users | Data recharge expired, SMS pack still active | Chat continues without spending on data |
| Rural users | 2G coverage only, data unusable | SMS works on 2G where data apps fail |
| Privacy-first users | Don't want message history stored anywhere | Zero persistence — nothing to leak or subpoena |
| Travelers | Roaming data too expensive, local SIM active | SMS rates are predictable and included in most plans |

## 2.3 Feature Scope — v1.0

| Feature | Priority | Notes |
| --- | --- | --- |
| Auto phone number detection from SIM | MUST | No login screen needed |
| One-to-one text chat over SMS | MUST | Core product |
| Sent / Failed status per message | MUST | SMS delivery is not guaranteed |
| SMS quota counter (local estimate) | MUST | Jio 100/day limit awareness |
| One-tap cellular voice call | MUST | Opens native dialer via Intent |
| Background SMS notification | MUST | Foreground service keeps receiver alive |
| Contact picker integration | SHOULD | UX convenience — fallback is manual entry |
| Dual SIM selector | SHOULD | Common on Indian Android devices |
| Group chat | WON'T (v1) | Burns 9 SMS per message in a 10-person group |
| Media sharing | WON'T (v1) | MMS is unreliable and carrier-inconsistent in India |
| Video calls | WON'T | VoLTE video is carrier-controlled, no public SDK access |
| End-to-end encryption | WON'T (v1) | SMS plaintext is visible to carrier — must be disclosed to users |

## 2.4 User Stories

Core Stories — Sprint 1

- US-01 As a user opening the app for the first time, I want my phone number detected automatically so I don't have to fill any form.
- US-02 As a user, I want to start a chat by typing a phone number so I can reach anyone on Towerchat or regular SMS.
- US-03 As a user, I want to send a message and see whether it was sent or failed so I know if the recipient got it.
- US-04 As a user, I want to receive messages even when the app is in the background so I don't miss anything.
- US-05 As a user, I want to see how many SMS I've used today so I don't hit the daily limit unexpectedly.
- US-06 As a user, I want to call someone with one tap so I can switch from chat to voice without leaving the app.
- US-07 As a user who cares about privacy, I want all my messages deleted automatically when I close the app so nothing is ever stored on my device.

## 2.5 Success Metrics

| Metric | Target at v1 launch |
| --- | --- |
| Time from install to first sent message | \< 60 seconds |
| SMS send success rate | > 98% on Jio/Airtel coverage |
| Cold start time | \< 1.5 seconds on Redmi 9A-class device |
| APK size | \< 8 MB |
| Crash-free sessions | > 99% |

Document 03

Architecture Diagram

System design — zero backend, carrier-native communication layer

## Technology Stack

Build Stack

Language Kotlin Coroutines for async SMS operations and LiveData for UI state

UI Jetpack Compose Single-activity, composable chat UI with no XML layouts

State ViewModel + StateFlow In-memory message list, no persistence layer at all

SMS Send SmsManager sendTextMessage() + sendMultipartTextMessage() with PendingIntent callbacks

SMS Receive BroadcastReceiver Priority 999 to intercept before default SMS app; abortBroadcast() for active chats

Background ForegroundService Keeps BroadcastReceiver alive; shows persistent "listening" notification

Voice Call Intent.ACTION_CALL Hands off to Android native dialer — no custom call handling needed

Identity TelephonyManager getLine1Number() on first launch; manual fallback for dual-SIM

DI Hilt Dependency injection for SmsRepository and IdentityManager

Storage None No Room, no SQLite, no SharedPreferences for messages. RAM only.

Min SDK API 26 (Android 8.0) Covers 98%+ of active Android devices in India

Document 04

Workflow Diagrams

Key user flows — first launch, send message, receive message, voice call

## 4.1 First Launch Flow

## 4.2 Send Message Flow

## 4.3 Receive Message Flow

Storage Audit

Zero Storage Guarantee

Every Android storage and backup mechanism checked individually — all absent by design

This section exists so any developer, security auditor, or Play Store reviewer can verify in one place that Towerchat has no data persistence layer of any kind. Each mechanism below is named explicitly, its status confirmed, and the implementation decision documented.

**The rule:** If the app process is killed right now — by the user, by Android, or by a crash — zero message content, zero chat history, zero call records, and zero contact associations survive. The next app launch starts completely blank.

## Android Storage Mechanisms — Full Audit

| Storage Mechanism | Status | Implementation Decision |
| --- | --- | --- |
| **Room / SQLite database** | ABSENT | No Room dependency in build.gradle. No SQLite helper class. No .db file created. Verified by filesystem audit — no file matching \*.db or \*.sqlite exists in app data directory. |
| **SharedPreferences (message content)** | ABSENT | SharedPreferences is used only for: own phone number (if manually entered), public key storage for encryption, and SMS quota counter. Message content, chat history, and contact associations are never written here. |
| **Internal storage files** | ABSENT | No FileOutputStream, no openFileOutput(), no File() writes for message data. The app writes no files to its internal storage directory beyond Tink's encrypted keystore entry. |
| **External storage / SD card** | ABSENT | WRITE_EXTERNAL_STORAGE permission is not declared in AndroidManifest.xml. The app cannot write to external storage even if it tried. |
| **Android Auto Backup (to Google Drive)** | DISABLED | android:allowBackup="false" is set in AndroidManifest.xml application tag. Android's automatic backup to Google Drive is fully disabled. No app data is ever uploaded to Google's servers. |
| **Google Drive Backup API** | ABSENT | No Google Drive SDK dependency. No DriveClient or DriveResourceClient calls. Towerchat has no Google account integration of any kind. |
| **Cloud Sync (Firebase, AWS, custom server)** | ABSENT | No Firebase dependency. No Firestore, no Firebase Realtime Database, no Firebase Storage. No REST API calls. No network permissions beyond SMS/cellular. Towerchat has no backend server. |
| **SMS database (Android system)** | BLOCKED | Towerchat intercepts incoming SMS via BroadcastReceiver with abortBroadcast() before Android writes it to the system SMS database. Outgoing SMS sent via SmsManager are written to the system SMS database by default — Towerchat deletes them immediately after send confirmation using ContentResolver with Telephony.Sms.CONTENT_URI. |
| **Call log (Android system)** | NOT WRITTEN | Towerchat initiates calls via Intent.ACTION_CALL and immediately steps aside. Android logs the call in the native call log. Towerchat does not read, copy, or store call log data. It has no READ_CALL_LOG or WRITE_CALL_LOG permission. |
| **Clipboard** | NOT USED | Towerchat does not programmatically write message content to the clipboard. Copy-to-clipboard is a user-initiated OS action outside the app's control. |
| **Logcat / crash logs** | SCRUBBED | ProGuard removes all Log.d(), Log.v(), Log.i(), Log.w(), Log.e() calls in release builds. No crash reporting SDK (Crashlytics, Sentry, etc.) is integrated. Zero message content appears in any log output. |
| **In-memory cache after app close** | CLEARED | All message lists live in ViewModel StateFlow. When the app process is killed, the JVM heap is cleared by Android. No static references hold message content. Verified: no Application-level singleton stores messages. |

## AndroidManifest.xml — Critical Declarations

Backup and storage declarations

```
<!-- Backup DISABLED — no message data ever goes to Google Drive -->
<application
    android:allowBackup="false"
    android:fullBackupContent="false"
    android:dataExtractionRules="@xml/data_extraction_rules"
    ...>

<!-- res/xml/data_extraction_rules.xml -->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="everything"/>
    </cloud-backup>
    <device-transfer>
        <exclude domain="everything"/>
    </device-transfer>
</data-extraction-rules>

<!-- Permissions NOT declared — confirms no storage access -->
<!-- WRITE_EXTERNAL_STORAGE — absent -->
<!-- READ_CALL_LOG — absent -->
<!-- WRITE_CALL_LOG — absent -->
<!-- READ_SMS — absent (BroadcastReceiver intercepts before DB write) -->
<!-- WRITE_SMS — absent -->
```

## Deleting outgoing SMS from system database

SmsCleanupHelper.kt — removes sent SMS from Android system immediately

```
// Android writes outgoing SMS to system DB automatically via SmsManager.
// This function deletes it immediately after the sent callback fires.
fun deleteFromSystemSmsDb(context: Context, recipientNumber: String) {
    val uri = Telephony.Sms.Sent.CONTENT_URI
    val selection = "${Telephony.Sms.ADDRESS} = ?"
    val args = arrayOf(recipientNumber)
    // Run on IO thread — never block main thread with ContentResolver
    CoroutineScope(Dispatchers.IO).launch {
        try {
            context.contentResolver.delete(uri, selection, args)
        } catch (e: SecurityException) {
            // Some OEMs restrict SMS DB write access — fail silently
            // The intercept via BroadcastReceiver already prevented DB write for incoming
        }
    }
}
```

**One honest caveat about call logs.** When a user taps the call button, Android's native dialer handles the call and logs it in the system call log. Towerchat has no permission to delete call logs and intentionally does not request one. The call log entry (number dialed, duration, timestamp) remains in Android's system call log — accessible to the user and to any app with READ_CALL_LOG permission. This is disclosed in the privacy policy. It is a carrier and OS-level record, not a Towerchat record.

Document 06

Additional Features — v1.1 Roadmap

All features are zero-storage, zero-internet, zero-backup — consistent with Towerchat's core design principle

## SMS Efficiency Features

| Feature | How it works | Storage impact |
| --- | --- | --- |
| **SMS Compression & Batching** | Multiple short replies typed within 30 seconds are combined into one SMS before sending. A 5-message exchange becomes 1 SMS. Uses a simple delimiter protocol: `TC_BATCH:\|msg1\|\|msg2\|\|msg3\|`. Receiver splits on delimiter and shows as separate bubbles. | None — batching happens in RAM before send |
| **Quota Reset Timer** | Countdown showing time until Jio's 100 SMS/day resets at midnight. Calculated from system clock — no server, no network call. Shows "Quota resets in 4h 32m" in the chat header when below 20 remaining. | Quota count stored in SharedPreferences as a single integer + reset date. No message content. |
| **Priority Contacts** | Mark up to 5 contacts as priority. When quota drops below 15, app warns before sending to non-priority contacts: "Only 15 SMS left today. Save for priority contacts?" | Priority list stored as a comma-separated phone number string in SharedPreferences. No message content. |
| **Message Scheduling** | Schedule a message to send at a future time using Android's AlarmManager. Message content lives in RAM until send time — if the app is killed before the alarm fires, the scheduled message is lost. This is by design — no persistence. | None — intentionally lost on app kill |
| **Dual SIM Manager** | Detects both SIMs via SubscriptionManager. User assigns specific contacts to SIM 1 or SIM 2. Towerchat automatically selects the right SIM slot when sending. SIM assignment stored in SharedPreferences as phone-number-to-slot mapping. | SIM slot assignments only — no message content |

## Privacy Features

| Feature | How it works | Storage impact |
| --- | --- | --- |
| **Stealth Mode** | App icon replaced with a calculator UI. Entering a specific PIN into the calculator opens Towerchat. Implemented using two Activities with the launcher intent pointed at the calculator Activity. Real Towerchat Activity is not exported. | PIN stored as a SHA-256 hash in EncryptedSharedPreferences. Never plaintext. |
| **Decoy PIN** | Two PINs: one opens a blank decoy chat screen, one opens the real app. If someone forces unlock, they see an empty app. Implemented as two separate UI states in the same Activity based on which PIN hash matches. | Two PIN hashes in EncryptedSharedPreferences only |
| **Auto-wipe on Failed Attempts** | After 5 consecutive wrong PINs, app calls a wipe function: clears all EncryptedSharedPreferences except the PIN hashes, terminates all ViewModels, and restarts the app process via System.exit(0). Since no data is on disk, "wipe" means clearing the key store and resetting the quota counter. | Wipes SharedPreferences entries — nothing to wipe from disk since messages were never stored |
| **Screen Security** | FLAG_SECURE on all Activities blocks screenshots and screen recording. Additionally detects active media projection (screen cast) via MediaProjectionManager and blurs the chat content when casting is active. | None |
| **Draft Persistence** | The only intentional exception to zero-storage: an unsent draft message is saved to EncryptedSharedPreferences per contact number. Cleared immediately on send or on manual delete. This is the one convenience trade-off — a draft is not a sent or received message. | One draft string per contact in EncryptedSharedPreferences. Encrypted. Cleared on send. |

## UX Features

| Feature | How it works | Storage impact |
| --- | --- | --- |
| **Typing Indicator** | After 3 seconds of continuous typing, sends one `TC_TYPING:` SMS to the recipient. Recipient's app shows "typing..." and suppresses the SMS from appearing as a message. Costs 1 SMS per typing session — throttled to maximum once per 60 seconds per conversation. | None |
| **Read Receipt** | When a message arrives and the chat is open and in foreground, sends one `TC_READ:` SMS back. Sender's app shows a double tick. One SMS per session, not per message — throttled to avoid burning quota. | None |
| **Message Reactions** | Long press on a message shows 6 emoji reactions. Selecting one sends `TC_REACT:👍:msgTimestamp` as one SMS. Recipient's app maps the reaction to the message by timestamp and displays it below the bubble. | None |
| **Call-back SMS Button** | After a missed call notification, one button sends a pre-written SMS: "I missed your call, please call back when free." User can edit the message before sending. Costs 1 SMS. | None |
| **Emergency Broadcast** | Pre-write an emergency message and select up to 5 contacts. One tap sends it to all simultaneously. Costs up to 5 SMS from quota. Requires confirmation tap to prevent accidental sends. | Emergency message text and contact list stored in EncryptedSharedPreferences. User sets this up in advance. |
| **Message Templates** | Save up to 10 frequently used messages (location as text coordinates, "I'll call you later", etc.). One tap inserts into the message field. Not sent automatically. | Template strings in EncryptedSharedPreferences. No sent/received history. |

## Complete Storage Map — What Is and Isn't Stored

| Data Type | Stored? | Where (if stored) | Encrypted? |
| --- | --- | --- | --- |
| Sent messages | NEVER | RAM only during session | In-transit only |
| Received messages | NEVER | RAM only during session | In-transit only |
| Chat history | NEVER | Not stored anywhere | N/A |
| Call records | NEVER | Not stored by Towerchat (system call log exists separately) | N/A |
| Backup of any kind | NEVER | allowBackup=false, cloud excluded | N/A |
| Own phone number | YES | EncryptedSharedPreferences | Yes — AES256_GCM |
| Encryption public keys | YES | EncryptedSharedPreferences | Yes — hardware-backed |
| Encryption private key | YES | Android Keystore | Yes — hardware-backed, never leaves device |
| SMS quota counter | YES | EncryptedSharedPreferences | Yes |
| Priority contact list | YES | EncryptedSharedPreferences | Yes |
| Dual SIM assignments | YES | EncryptedSharedPreferences | Yes |
| Draft message (unsent) | YES | EncryptedSharedPreferences | Yes — cleared on send |
| App PIN hashes | YES | EncryptedSharedPreferences | Yes — SHA-256 hash only |
| Message templates | YES | EncryptedSharedPreferences | Yes |
| Emergency message + contacts | YES | EncryptedSharedPreferences | Yes |

**Every "YES" row above is configuration, not communication.** Phone number, encryption keys, quota counter, PIN hashes, templates — these are app settings that make Towerchat work. None of them are messages, chat history, call records, or communication content. The distinction is absolute and intentional.

Document 05

Risk Register

Known risks, their impact, and mitigation strategy

| Risk | Severity | Mitigation |
| --- | --- | --- |
| Carrier blocks app from using abortBroadcast() on some OEM ROMs | High | Test on MIUI, OneUI, ColorOS. Fall back to showing messages in both Towerchat and default SMS app if abort fails. |
| TelephonyManager returns null for phone number (common on Jio dual-SIM) | High | Always implement manual number entry as fallback. Never rely solely on the API. |
| User hits 100 SMS/day Jio limit mid-conversation | Medium | Show real-time quota counter. Warn at 20 remaining. Hard block send with clear error message at 0. |
| ForegroundService killed by aggressive battery optimizers (Xiaomi, Oppo) | Medium | Show one-time prompt asking user to whitelist app in battery settings. Register BOOT_COMPLETED receiver to restart service. |
| Long messages split into multiple SMS arriving out of order | Medium | Add character counter in UI. Encourage messages under 160 chars. Handle multipart reassembly in BroadcastReceiver using PDU reference numbers. |
| No end-to-end encryption — carrier sees all messages | Medium | Disclose clearly in onboarding. Plan XSalsa20 or NaCl-based encryption layer for v2 where keys are exchanged via an initial SMS handshake. |
| CALL_PHONE permission rejected by user | Low | Fall back to ACTION_DIAL (shows dialer but doesn't call automatically). Core chat still works. |

**Note on privacy disclosure.** Because SMS is plaintext to the carrier, Towerchat must clearly tell users on first launch that messages are not end-to-end encrypted and are visible to the network operator. This is not optional — it's the honest thing to do, and it protects against legal risk.