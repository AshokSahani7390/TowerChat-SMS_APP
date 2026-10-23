package com.towerchat.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.towerchat.app.service.TowerchatForegroundService
import com.towerchat.app.ui.screens.ChatDetailScreen
import com.towerchat.app.ui.screens.HomeScreen
import com.towerchat.app.ui.screens.OnboardingScreen
import com.towerchat.app.ui.theme.TowerchatTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    // Contact Picker Launcher
    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { contactUri: Uri? ->
        contactUri?.let { uri ->
            extractPhoneNumberFromContact(uri)?.let { phone ->
                viewModel.openChat(phone)
            }
        }
    }

    // Permission Launcher for runtime permissions
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.initSimIdentity()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Block screenshots, screen recording, and recent app thumbnails for privacy in release builds
        if (!com.towerchat.app.BuildConfig.DEBUG) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }

        requestRequiredPermissions()
        handleIntent(intent)

        setContent {
            TowerchatTheme {
                val userNumber by viewModel.userPhoneNumber.collectAsState()
                val activeRecipient by viewModel.activeRecipient.collectAsState()
                val quota by viewModel.quotaState.collectAsState()
                val conversations by viewModel.conversations.collectAsState()
                val activeMessages by viewModel.activeMessages.collectAsState()
                val inputText by viewModel.inputText.collectAsState()
                val priorityContacts by viewModel.priorityContacts.collectAsState()
                val isTyping by viewModel.isRecipientTyping.collectAsState()
                val isAppOnlyMode by viewModel.isAppOnlyMode.collectAsState()

                AnimatedContent(
                    targetState = when {
                        userNumber == null -> ScreenState.ONBOARDING
                        activeRecipient != null -> ScreenState.CHAT_DETAIL
                        else -> ScreenState.HOME
                    },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "screen_transition"
                ) { screenState ->
                    when (screenState) {
                        ScreenState.ONBOARDING -> {
                            OnboardingScreen(
                                detectedNumber = userNumber,
                                onComplete = { number ->
                                    viewModel.setManualPhoneNumber(number)
                                }
                            )
                        }
                        ScreenState.HOME -> {
                            HomeScreen(
                                quota = quota,
                                conversations = conversations,
                                priorityContacts = priorityContacts,
                                quotaResetCountdown = viewModel.getQuotaResetCountdown(),
                                onOpenChat = { recipient ->
                                    viewModel.openChat(recipient)
                                },
                                onPickContact = {
                                    contactPickerLauncher.launch(null)
                                },
                                onClearSession = {
                                    viewModel.clearSession()
                                },
                                onSendEmergencyBroadcast = {
                                    viewModel.sendEmergencyBroadcast()
                                },
                                onPanicWipe = {
                                    viewModel.panicWipe()
                                },
                                onDeleteConversation = { recipient ->
                                    viewModel.deleteConversation(recipient)
                                }
                            )
                        }
                        ScreenState.CHAT_DETAIL -> {
                            activeRecipient?.let { recipient ->
                                ChatDetailScreen(
                                    state = com.towerchat.app.ui.screens.ChatDetailState(
                                        recipientNumber = recipient,
                                        messages = activeMessages,
                                        inputText = inputText,
                                        quota = quota,
                                        safetyNumber = viewModel.getSafetyNumber(recipient),
                                        isKeyChanged = viewModel.isKeyChanged(recipient),
                                        isTyping = isTyping,
                                        isPriority = viewModel.isPriorityContact(recipient),
                                        isBurnOnExit = viewModel.isBurnOnExit(recipient),
                                        isAppOnlyMode = isAppOnlyMode,
                                        templates = viewModel.getTemplates()
                                    ),
                                    actions = com.towerchat.app.ui.screens.ChatDetailActions(
                                        onDismissKeyChange = { viewModel.dismissKeyChangeWarning(recipient) },
                                        onTogglePriority = { viewModel.togglePriorityContact(recipient) },
                                        onToggleBurnOnExit = { viewModel.toggleBurnOnExit(recipient) },
                                        onToggleAppOnlyMode = { viewModel.toggleAppOnlyMode(recipient) },
                                        onToggleSaveMessage = { messageId -> viewModel.toggleSaveMessage(recipient, messageId) },
                                        onDeleteMessage = { messageId -> viewModel.deleteMessage(recipient, messageId) },
                                        onDeleteConversation = { viewModel.deleteConversation(recipient) },
                                        onWipeChatNow = { viewModel.wipeChatNow(recipient) },
                                        onInputTextChange = { viewModel.updateInputText(it) },
                                        onSendMessage = { viewModel.sendMessage(recipient) },
                                        onSendReaction = { emoji, timestamp ->
                                            viewModel.sendReaction(recipient, emoji, timestamp)
                                        },
                                        onSelectTemplate = { template ->
                                            viewModel.sendTemplate(recipient, template)
                                        },
                                        onVoiceCall = { viewModel.makeCall(recipient) },
                                        onBack = { viewModel.closeChatWithBurnOnExit(recipient) }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val recipient = intent.getStringExtra("recipient_number")
        if (!recipient.isNullOrBlank()) {
            viewModel.openChat(recipient)
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionsLauncher.launch(missing.toTypedArray())
        } else {
            viewModel.initSimIdentity()
        }
    }

    private fun extractPhoneNumberFromContact(contactUri: Uri): String? {
        val contentResolver = contentResolver
        contentResolver.query(contactUri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val hasPhoneIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                if (idIndex != -1 && hasPhoneIndex != -1) {
                    val id = cursor.getString(idIndex)
                    val hasPhone = cursor.getInt(hasPhoneIndex)

                    if (hasPhone > 0) {
                        contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(id),
                            null
                        )?.use { phoneCursor ->
                            if (phoneCursor.moveToFirst()) {
                                val numberIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                if (numberIndex != -1) {
                                    return phoneCursor.getString(numberIndex)
                                }
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    private enum class ScreenState {
        ONBOARDING,
        HOME,
        CHAT_DETAIL
    }
}
