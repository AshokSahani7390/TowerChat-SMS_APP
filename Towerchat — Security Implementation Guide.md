TOWERCHAT / SECURITY [Problem 1](#p1) [Problem 2](#p2) [Problem 3](#p3) [Problem 4](#p4) [Double Ratchet](#ratchet) [Audit](#audit) [Roadmap](#roadmap)

Security Implementation Guide v1.0

# Towerchat WhatsApp-level security on a carrier-native SMS app.

Four problems stand between Towerchat and a secure, Play Store-ready messenger. This document solves all four with exact Kotlin code, libraries, and implementation steps — from plaintext SMS to Double Ratchet forward secrecy.

Current state

Plaintext SMS

After v1 security

AES-256-GCM encrypted

After v2 security

Double Ratchet (Signal-level)

What never changes

Metadata visible to carrier

Problem 01

The carrier sees everything

Every SMS Towerchat sends today is plaintext. Jio reads it. IMSI catchers read it. Fix: encrypt the payload before it enters the SMS body.

## How the fix works

Two users generate X25519 key pairs on their devices. They swap public keys over one SMS each — visible to the carrier, but a public key is safe to expose. Using Diffie-Hellman key exchange, both devices independently derive the same shared secret without ever sending it over the network. From that point, every message is encrypted with AES-256-GCM before going into the SMS body. The carrier receives a Base64-encoded ciphertext blob. Unreadable.

1

### Generate X25519 key pair on first launch

Each device creates a public/private key pair using Google Tink. The private key lives in Android Keystore — hardware-backed, never leaves the device. Public key is stored in SharedPreferences (safe to expose).

2

### Exchange public keys via a handshake SMS

When User A opens a chat with User B for the first time, the app sends a special handshake SMS containing A's public key with a `TC_HANDSHAKE:` prefix. User B's app intercepts this, stores A's public key, and sends back B's public key the same way. This one-time exchange is the only SMS the carrier can meaningfully read.

3

### Derive shared secret via ECDH

Both devices run Elliptic Curve Diffie-Hellman on their own private key and the other party's public key. They arrive at the same 32-byte shared secret independently — no secret ever travels over the network.

4

### Encrypt every message with AES-256-GCM

The shared secret becomes the encryption key. Each message gets a random 12-byte nonce. AES-256-GCM produces ciphertext + authentication tag, serialized as `Base64(nonce + ciphertext)` and sent as the SMS body. The tag ensures the message wasn't tampered with in transit.

## Kotlin Implementation

build.gradle.kts — add Tink dependency

Kotlin DSL build.gradle.kts

```
// Google Tink — battle-tested crypto, never roll your own
implementation("com.google.crypto.tink:tink-android:1.11.0")
```

Key generation — runs once on first launch

Kotlin CryptoManager.kt

```
object CryptoManager {

    private const val PREFS_KEY = "tc_crypto"
    private const val PUBLIC_KEY_PREF = "public_key"

    // Generate X25519 key pair and store in Android Keystore
    fun initKeys(context: Context) {
        TinkConfig.register()
        val keysetHandle = KeysetHandle.generateNew(
            KeyTemplates.get("ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM")
        )
        // Store keyset encrypted in Android Keystore
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val sharedPref = EncryptedSharedPreferences.create(
            context, PREFS_KEY, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        AndroidKeysetManager.Builder()
            .withSharedPref(context, "tc_keyset", "tc_keyset_pref")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://tc_master_key")
            .build()
    }

    // Encrypt a message with the shared secret derived from ECDH
    fun encrypt(plaintext: String, sharedSecret: ByteArray): String {
        val key = SecretKeySpec(sharedSecret.copyOf(32), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        val ciphertext = cipher.doFinal(plaintext.toByteArray())
        // Prepend nonce to ciphertext so receiver can extract it
        return Base64.encodeToString(nonce + ciphertext, Base64.NO_WRAP)
    }

    // Decrypt — extracts nonce from first 12 bytes
    fun decrypt(encoded: String, sharedSecret: ByteArray): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val nonce = bytes.copyOfRange(0, 12)
        val ciphertext = bytes.copyOfRange(12, bytes.size)
        val key = SecretKeySpec(sharedSecret.copyOf(32), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        return String(cipher.doFinal(ciphertext))
    }
}
```

Handshake SMS — sending and receiving public keys

Kotlin HandshakeManager.kt

```
const val HANDSHAKE_PREFIX = "TC_HANDSHAKE:"
const val MESSAGE_PREFIX = "TC_MSG:"

// Send your public key to the other party on first message
fun sendHandshake(recipientNumber: String, myPublicKey: String) {
    val smsManager = SmsManager.getDefault()
    smsManager.sendTextMessage(
        recipientNumber, null,
        "$HANDSHAKE_PREFIX$myPublicKey",
        null, null
    )
}

// In BroadcastReceiver — detect handshake vs encrypted message
fun processIncomingSms(sender: String, body: String) {
    when {
        body.startsWith(HANDSHAKE_PREFIX) -> {
            val theirPublicKey = body.removePrefix(HANDSHAKE_PREFIX)
            // Store their public key, derive shared secret, reply with ours
            storePublicKey(sender, theirPublicKey)
            deriveAndStoreSharedSecret(sender, theirPublicKey)
            sendHandshake(sender, myPublicKey) // reply with our key
        }
        body.startsWith(MESSAGE_PREFIX) -> {
            val encrypted = body.removePrefix(MESSAGE_PREFIX)
            val sharedSecret = getSharedSecret(sender)
            val plaintext = CryptoManager.decrypt(encrypted, sharedSecret)
            addToMessageList(sender, plaintext)
        }
        else -> /* not a Towerchat message — pass to system */
    }
}
```

**SMS length after encryption.** Base64-encoded AES-256-GCM output is roughly 1.4x the plaintext size plus 28 bytes overhead. A 100-character message becomes \~170 characters — still one SMS segment. A 160-character message may split into two. Show a character counter with an estimated "SMS segments" indicator so users aren't surprised by quota usage.

Problem 02

You can't verify who you're talking to

A SIM swap gives an attacker your phone number. Your app has no way to detect it without key fingerprint verification.

When keys are exchanged, each side derives a fingerprint — a short hash of both public keys combined. If both users compare this fingerprint and it matches, the conversation is genuine. If someone did a SIM swap or is performing a man-in-the-middle attack, the fingerprint changes and the mismatch is immediately visible.

## Fingerprint generation

Kotlin FingerprintManager.kt

```
// Generate a 12-digit safety number from both public keys
// Both users must see the same number — compare over a phone call
fun generateSafetyNumber(
    myPublicKey: ByteArray,
    theirPublicKey: ByteArray
): String {
    // Sort keys so both sides produce the same hash regardless of order
    val sorted = listOf(myPublicKey, theirPublicKey)
        .sortedWith(compareBy { it.contentHashCode() })
    val combined = sorted[0] + sorted[1]
    val digest = MessageDigest.getInstance("SHA-256").digest(combined)
    // Take first 6 bytes → 12 decimal digits, grouped as 4 groups of 3
    val num = digest.take(6).fold(0L) { acc, b -> acc * 256 + (b.toLong() and 0xFF) }
    return num.toString().padStart(12, '0')
        .chunked(3).joinToString(" ")  // "047 183 291 504"
}

// Call this when keys change — warns user immediately in UI
fun detectKeyChange(sender: String, newPublicKey: ByteArray): Boolean {
    val storedKey = getStoredPublicKey(sender)
    return storedKey != null && !storedKey.contentEquals(newPublicKey)
}
```

## What the UI shows

A

### Safety number screen in chat info

Accessible from the chat header. Shows the 12-digit safety number for this conversation. Both users open it and compare over a phone call. If the numbers match, the chat is verified.

B

### Key change warning banner

If Towerchat receives a new handshake from a number it already has a key for, it shows a yellow warning banner in the chat: *"Security key changed for this contact. Verify the safety number before continuing."* The user must explicitly dismiss it.

C

### First-time notice on every new chat

When a chat is opened for the first time, show a one-line notice: *"Messages in this chat are encrypted. Carrier cannot read them."* Tappable — expands to explain what that means and how to verify.

Problem 03

Message interception at the device level

Malicious apps with RECEIVE_SMS can intercept raw SMS before your BroadcastReceiver. Encryption at the transport layer (Problem 1) already defeats this — but there are four more hardening steps.

1

### Set BroadcastReceiver priority to 999

In `AndroidManifest.xml`, set `android:priority="999"` on your SMS receiver. This puts Towerchat at the front of the broadcast queue. Combined with `abortBroadcast()`, no other app sees the SMS after you process it.

2

### Zero logcat output for message content

Never call `Log.d()`, `Log.e()`, or `println()` with any message content, key material, or phone numbers. Android logs are readable by any app on a rooted device. Use `BuildConfig.DEBUG` guards and strip all logs in release builds via ProGuard.

3

### Android Keystore for all key material

Private keys never exist in memory longer than needed for a single operation. Use `AndroidKeyStore` provider — keys are hardware-backed on devices with a secure element and cannot be extracted even on rooted devices.

4

### Block screenshots and screen recording

Add `window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)` to every Activity. This prevents screenshots, screen recordings, and appearance in the recent apps thumbnail. The same flag WhatsApp uses.

## ProGuard rules for release build

ProGuard proguard-rules.pro

```
# Keep Tink classes intact
-keep class com.google.crypto.tink.** { *; }

# Remove all logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Obfuscate everything except Android framework calls
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
```

**Never store decrypted message content anywhere on disk.** Not in SharedPreferences, not in a log file, not in a crash report. If you integrate a crash reporting SDK like Firebase Crashlytics, configure it to exclude message content from crash reports explicitly. Crash reports are sent to external servers.

Problem 04

Google Play Store requirements

RECEIVE_SMS and CALL_PHONE are restricted permissions. Google reviews them manually. Here is exactly what to prepare.

## Permissions Declaration Form

After you submit your app, Google will ask you to fill a Permissions Declaration form in the Play Console under "App content." These are the exact answers for Towerchat.

| Google asks | Towerchat's answer |
| --- | --- |
| Why does your app need RECEIVE_SMS? | Towerchat is an SMS-based messenger that operates without internet. Receiving SMS is the core transport mechanism for all messages. The app intercepts incoming SMS from known contacts and displays them in the chat UI. |
| Why does your app need SEND_SMS? | All outgoing messages are sent as SMS via SmsManager. There is no internet-based messaging backend. SMS is the only transport layer. |
| Why does your app need CALL_PHONE? | Users can initiate cellular voice calls directly from the chat screen. The permission is used to trigger Intent.ACTION_CALL without requiring the user to open the dialer separately. |
| Does your app store SMS content on your servers? | No. There are no servers. Messages exist only in device RAM during an active session and are discarded when the app is closed. |

## Privacy Policy — required sections

Host this at a real URL (e.g., towerchat.app/privacy) before submitting to Play Store. It must include all of these sections.

What the privacy policy must state

- ✓Towerchat does not collect, store, or transmit any user data to any server.
- ✓Messages are encrypted on-device before transmission and exist only in device RAM during an active session.
- ✓No message history is retained after the app is closed.
- ✓Phone numbers are used solely to identify conversation participants and are never uploaded to any server.
- ✓SMS messages travel over the carrier's network. The carrier (e.g., Jio, Airtel) may have access to message metadata (sender, recipient, timestamp) but not message content, which is end-to-end encrypted.
- ✓Contact list access is used only to let users select recipients. Contacts are never uploaded or stored outside the device.
- ✓The app contains no advertising SDKs, analytics SDKs, or third-party tracking.

## Data Safety Form — Play Console answers

| Field | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | No |
| Is all of the user data collected by your app encrypted in transit? | Yes |
| Do you provide a way for users to request that their data is deleted? | No data is stored — nothing to delete |
| Messages — collected? | No |
| Phone number — collected? | No (read from SIM, never uploaded) |
| Contacts — collected? | No |

## Technical checklist before submission

Play Store technical requirements 2024

- ○Target SDK 34 (Android 14) — mandatory for all new apps submitted after August 2024.
- ○Submit as Android App Bundle (.aab) not .apk — Google requires AAB for new apps.
- ○64-bit support — include arm64-v8a ABI. 32-bit only builds are rejected.
- ○App icon at 512x512px PNG with no transparency.
- ○At least 2 screenshots per device type (phone required, tablet optional).
- ○Privacy policy URL live and accessible before submission.
- ○MobSF security audit passed — no high severity findings.
- ○No hardcoded API keys, secrets, or phone numbers in the APK.
- ○FLAG_SECURE set on all Activities.
- ○Network Security Config set to block cleartext traffic.

XML res/xml/network_security_config.xml

```
<!-- Block all cleartext HTTP traffic — Towerchat has no server anyway -->
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system"/>
        </trust-anchors>
    </base-config>
</network-security-config>
```

v2 Security Upgrade

Double Ratchet Algorithm

Forward secrecy — stealing today's key cannot decrypt yesterday's messages. This is exactly what WhatsApp and Signal use.

The v1 encryption (X25519 + AES-256-GCM) uses one shared secret for the entire conversation. If an attacker steals that key, they can decrypt every past and future message. The Double Ratchet fixes this by generating a new encryption key for every single message. Old keys are deleted immediately after use. Stealing the current key gives you at most the last message.

## How it works — two ratchets combined

## Implementation path for v2

Don't implement Double Ratchet from scratch. Use the open-source **libsignal-client** library — this is the actual Signal Protocol library that WhatsApp, Signal, and Google Messages use. It has Android bindings.

Kotlin DSL build.gradle.kts — v2 dependency

```
// Official Signal Protocol library — battle-tested, audited
implementation("org.signal:libsignal-android:0.36.0")

// What it gives you out of the box:
// - X3DH key agreement (Extended Triple Diffie-Hellman)
// - Double Ratchet with forward secrecy
// - Sealed sender (hides metadata within the protocol)
// - Session management across key rotations
```

**Build v1 encryption first. Ship it. Then upgrade to Double Ratchet in v2.** The key exchange infrastructure you build in v1 (X25519 handshake, public key storage, ECDH shared secret) is the same foundation libsignal builds on. The upgrade is additive, not a rewrite.

Pre-submission

Security Audit with MobSF

Run this before every Play Store submission. Free, open source, catches what human review misses.

## Running MobSF

Shell Terminal — run MobSF via Docker

```
# Pull and run MobSF
docker pull opensecurity/mobile-security-framework-mobsf
docker run -it --rm -p 8000:8000 opensecurity/mobile-security-framework-mobsf

# Open http://localhost:8000
# Upload your .apk or .aab
# MobSF runs static + dynamic analysis
# Fix everything rated HIGH before submitting to Play Store
```

## What MobSF checks for Towerchat specifically

| Check | What to fix if flagged |
| --- | --- |
| Hardcoded secrets or keys in APK | Move everything to Android Keystore. Nothing hardcoded. |
| Cleartext traffic allowed | Set `cleartextTrafficPermitted="false"` in network security config. |
| Exported components without protection | Add `android:exported="false"` to all receivers except the SMS one. |
| Weak crypto (MD5, SHA1, DES, ECB mode) | Use AES-256-GCM only. No ECB. MobSF flags ECB explicitly. |
| Log statements with sensitive data | ProGuard strips all Log calls in release. Verify with MobSF. |
| Missing FLAG_SECURE | Add to all Activities. MobSF detects its absence. |
| Overprivileged app | Declare only the 7 permissions in the SRS. Remove any extras. |

Implementation Roadmap

Build order — what to implement when

Do these in sequence. Each phase is usable and shippable on its own.

| Phase | What to build | Security level after |
| --- | --- | --- |
| Phase 1 | Core SMS chat + voice call. No encryption yet. Internal testing only — never release plaintext. | Plaintext — internal only |
| Phase 2 | X25519 key exchange handshake. AES-256-GCM message encryption. Android Keystore integration. FLAG_SECURE on all Activities. | Encrypted — Play Store ready |
| Phase 3 | Safety number UI. Key change warning banner. First-time encryption notice. MobSF audit. ProGuard release config. Privacy policy live. | Encrypted + verified — v1.0 release |
| Phase 4 | Replace AES-256-GCM with libsignal Double Ratchet. Forward secrecy per message. Session management across key rotations. | Signal-level forward secrecy — v2.0 |
| Phase 5 | Disappearing messages (in-session timer). Sealed sender to hide initiator identity within protocol. Independent security audit by a third party. | WhatsApp-level — v2.5 |

**The metadata wall.** No version of Towerchat can hide from the carrier that two phone numbers communicated, when, and how many SMS were exchanged. Encryption hides the content. The carrier always sees the envelope. This is true of WhatsApp too — Meta knows who you message and when, just not what you say. Only Signal's sealed sender partially attacks this problem, and only within Signal's own protocol layer, not at the carrier level. Be honest with users about this in the privacy policy and onboarding.