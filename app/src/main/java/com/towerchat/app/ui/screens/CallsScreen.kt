package com.towerchat.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SimCardAlert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towerchat.app.data.model.CallLogEntry
import com.towerchat.app.data.model.CallLogGroup
import com.towerchat.app.data.model.CallType
import com.towerchat.app.data.model.PhoneContact
import com.towerchat.app.ui.CallsSubTab
import com.towerchat.app.ui.components.GoogleAvatar
import com.towerchat.app.ui.theme.DarkBorder
import com.towerchat.app.ui.theme.GmBackground
import com.towerchat.app.ui.theme.GmOnPrimary
import com.towerchat.app.ui.theme.GmPrimary
import com.towerchat.app.ui.theme.GmSurface
import com.towerchat.app.ui.theme.GmSurfaceVariant
import com.towerchat.app.ui.theme.GmTextMuted
import com.towerchat.app.ui.theme.GmTextPrimary
import com.towerchat.app.ui.theme.GmTextSecondary
import com.towerchat.app.ui.theme.QuotaExhaustedColor
import com.towerchat.app.ui.theme.QuotaSafeColor
import com.towerchat.app.ui.theme.QuotaWarningColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallsScreen(
    currentSubTab: CallsSubTab,
    onSelectSubTab: (CallsSubTab) -> Unit,
    recents: List<CallLogGroup>,
    favorites: List<PhoneContact>,
    contacts: List<PhoneContact>,
    smartDialMatches: List<PhoneContact>,
    keypadInput: String,
    searchQuery: String,
    hasReadCallLogPermission: Boolean,
    hasReadContactsPermission: Boolean,
    isAirplaneMode: Boolean,
    hasSim: Boolean,
    onKeypadDigit: (Char) -> Unit,
    onKeypadBackspace: () -> Unit,
    onKeypadClear: () -> Unit,
    onKeypadCall: (String) -> Unit,
    onKeypadVoicemail: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenCallDetails: (CallLogGroup) -> Unit,
    onLoadMoreRecents: () -> Unit,
    onRequestCallLogPermission: () -> Unit,
    onRequestContactsPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GmBackground)
    ) {
        // Warning Banners (Airplane mode / No SIM)
        if (isAirplaneMode) {
            BannerWarning(
                icon = Icons.Default.AirplanemodeActive,
                text = "Airplane mode is on. Cellular voice calls are disabled."
            )
        } else if (!hasSim) {
            BannerWarning(
                icon = Icons.Default.SimCardAlert,
                text = "No SIM card detected. Emergency calls only (112)."
            )
        }

        // Sub-Navigation Tabs: Favorites, Recents, Contacts, Keypad
        ScrollableTabRow(
            selectedTabIndex = currentSubTab.ordinal,
            containerColor = GmSurface,
            contentColor = GmPrimary,
            edgePadding = 12.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[currentSubTab.ordinal]),
                    color = GmPrimary
                )
            },
            divider = {
                HorizontalDivider(color = DarkBorder, thickness = 1.dp)
            }
        ) {
            Tab(
                selected = currentSubTab == CallsSubTab.FAVORITES,
                onClick = { onSelectSubTab(CallsSubTab.FAVORITES) },
                text = { Text("Favorites", fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = currentSubTab == CallsSubTab.RECENTS,
                onClick = { onSelectSubTab(CallsSubTab.RECENTS) },
                text = { Text("Recents", fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = currentSubTab == CallsSubTab.CONTACTS,
                onClick = { onSelectSubTab(CallsSubTab.CONTACTS) },
                text = { Text("Contacts", fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = currentSubTab == CallsSubTab.KEYPAD,
                onClick = { onSelectSubTab(CallsSubTab.KEYPAD) },
                text = { Text("Keypad", fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.Dialpad, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        // Tab Content
        Box(modifier = Modifier.weight(1f)) {
            when (currentSubTab) {
                CallsSubTab.RECENTS -> {
                    RecentsTabContent(
                        recents = recents,
                        hasPermission = hasReadCallLogPermission,
                        onRequestPermission = onRequestCallLogPermission,
                        onOpenSettings = onOpenSettings,
                        onCallNumber = onKeypadCall,
                        onOpenCallDetails = onOpenCallDetails,
                        onLoadMore = onLoadMoreRecents
                    )
                }
                CallsSubTab.FAVORITES -> {
                    FavoritesTabContent(
                        favorites = favorites,
                        hasPermission = hasReadContactsPermission,
                        onRequestPermission = onRequestContactsPermission,
                        onOpenSettings = onOpenSettings,
                        onCallContact = { onKeypadCall(it.number) }
                    )
                }
                CallsSubTab.CONTACTS -> {
                    ContactsTabContent(
                        contacts = contacts,
                        searchQuery = searchQuery,
                        hasPermission = hasReadContactsPermission,
                        onSearchQueryChange = onSearchQueryChange,
                        onRequestPermission = onRequestContactsPermission,
                        onOpenSettings = onOpenSettings,
                        onCallContact = { onKeypadCall(it.number) }
                    )
                }
                CallsSubTab.KEYPAD -> {
                    KeypadTabContent(
                        digits = keypadInput,
                        smartDialMatches = smartDialMatches,
                        onDigit = onKeypadDigit,
                        onBackspace = onKeypadBackspace,
                        onClear = onKeypadClear,
                        onCall = onKeypadCall,
                        onVoicemail = onKeypadVoicemail
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Banners & Permissions
// -------------------------------------------------------------

@Composable
private fun BannerWarning(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF3E2723))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = QuotaWarningColor, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = GmTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PermissionRationaleCard(
    title: String,
    description: String,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = GmSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    color = GmTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    color = GmTextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = GmPrimary, contentColor = GmOnPrimary),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Grant Permission", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onOpenSettings) {
                    Text("Open App Settings", color = GmPrimary)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 1. Recents Tab
// -------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentsTabContent(
    recents: List<CallLogGroup>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onCallNumber: (String) -> Unit,
    onOpenCallDetails: (CallLogGroup) -> Unit,
    onLoadMore: () -> Unit
) {
    if (!hasPermission) {
        PermissionRationaleCard(
            title = "Call Log Access Required",
            description = "TowerChat needs permission to view your recent call history on this phone. No call data is ever uploaded or stored externally.",
            onRequestPermission = onRequestPermission,
            onOpenSettings = onOpenSettings
        )
        return
    }

    if (recents.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.History, contentDescription = null, tint = GmTextMuted, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No recent calls", color = GmTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Your recent incoming and outgoing calls will show here", color = GmTextSecondary, fontSize = 13.sp)
            }
        }
        return
    }

    val listState = rememberLazyListState()

    // Pagination trigger when scrolling near bottom
    val isNearBottom by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 5
        }
    }

    LaunchedEffect(isNearBottom) {
        if (isNearBottom) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(recents, key = { "${it.id}_${it.latestTimestamp}" }) { group ->
            RecentsListItem(
                group = group,
                onClick = { onCallNumber(group.number) },
                onDetailsClick = { onOpenCallDetails(group) }
            )
            HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
        }
    }
}

@Composable
private fun RecentsListItem(
    group: CallLogGroup,
    onClick: () -> Unit,
    onDetailsClick: () -> Unit
) {
    val formattedTime = remember(group.latestTimestamp) {
        SimpleDateFormat("h:mm a · MMM d", Locale.getDefault()).format(Date(group.latestTimestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GoogleAvatar(identifier = group.number, size = 44.dp)
        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.displayName,
                    color = if (group.latestType == CallType.MISSED) QuotaExhaustedColor else GmTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (group.callCount > 1) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "(${group.callCount})",
                        color = GmTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                CallTypeIcon(type = group.latestType)
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "$formattedTime${if (group.totalDurationSeconds > 0) " · ${group.formattedDuration}" else ""}",
                    color = GmTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        IconButton(onClick = onDetailsClick) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Call details",
                tint = GmPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CallTypeIcon(type: CallType) {
    when (type) {
        CallType.INCOMING -> {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CallReceived,
                contentDescription = "Incoming",
                tint = QuotaSafeColor,
                modifier = Modifier.size(14.dp)
            )
        }
        CallType.OUTGOING -> {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CallMade,
                contentDescription = "Outgoing",
                tint = GmPrimary,
                modifier = Modifier.size(14.dp)
            )
        }
        CallType.MISSED -> {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CallMissed,
                contentDescription = "Missed",
                tint = QuotaExhaustedColor,
                modifier = Modifier.size(14.dp)
            )
        }
        CallType.REJECTED, CallType.BLOCKED -> {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = "Blocked",
                tint = GmTextMuted,
                modifier = Modifier.size(14.dp)
            )
        }
        CallType.VOICEMAIL -> {
            Icon(
                imageVector = Icons.Default.Voicemail,
                contentDescription = "Voicemail",
                tint = GmPrimary,
                modifier = Modifier.size(14.dp)
            )
        }
        CallType.UNKNOWN -> {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = null,
                tint = GmTextMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// 2. Favorites Tab
// -------------------------------------------------------------

@Composable
private fun FavoritesTabContent(
    favorites: List<PhoneContact>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onCallContact: (PhoneContact) -> Unit
) {
    if (!hasPermission) {
        PermissionRationaleCard(
            title = "Contacts Access Required",
            description = "TowerChat needs contacts permission to show your favorite starred contacts for one-tap calling.",
            onRequestPermission = onRequestPermission,
            onOpenSettings = onOpenSettings
        )
        return
    }

    if (favorites.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Default.Star, contentDescription = null, tint = GmTextMuted, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No favorites yet", color = GmTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Star contacts in your phone's address book to pin them here for instant calling",
                    color = GmTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(favorites, key = { it.id }) { contact ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCallContact(contact) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GoogleAvatar(identifier = contact.name, size = 46.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(contact.name, color = GmTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(contact.number, color = GmTextSecondary, fontSize = 13.sp)
                }
                IconButton(onClick = { onCallContact(contact) }) {
                    Icon(Icons.Default.Call, contentDescription = "Call", tint = QuotaSafeColor, modifier = Modifier.size(20.dp))
                }
            }
            HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
        }
    }
}

// -------------------------------------------------------------
// 3. Contacts Tab
// -------------------------------------------------------------

@Composable
private fun ContactsTabContent(
    contacts: List<PhoneContact>,
    searchQuery: String,
    hasPermission: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onCallContact: (PhoneContact) -> Unit
) {
    if (!hasPermission) {
        PermissionRationaleCard(
            title = "Contacts Access Required",
            description = "TowerChat needs contacts permission to show your address book and let you call contacts directly.",
            onRequestPermission = onRequestPermission,
            onOpenSettings = onOpenSettings
        )
        return
    }

    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) {
            contacts
        } else {
            val q = searchQuery.trim().lowercase()
            contacts.filter {
                it.name.lowercase().contains(q) || it.number.contains(q)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Contacts Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(GmSurfaceVariant)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null, tint = GmTextMuted, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    textStyle = TextStyle(color = GmTextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(GmPrimary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text("Search contacts", color = GmTextMuted, fontSize = 14.sp)
                        }
                        innerTextField()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = GmTextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (filteredContacts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (searchQuery.isBlank()) "No contacts found" else "No matching contacts",
                    color = GmTextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredContacts, key = { it.id }) { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCallContact(contact) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GoogleAvatar(identifier = contact.name, size = 42.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(contact.name, color = GmTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(contact.number, color = GmTextSecondary, fontSize = 13.sp)
                        }
                        IconButton(onClick = { onCallContact(contact) }) {
                            Icon(Icons.Default.Call, contentDescription = "Call", tint = GmPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                    HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 4. Keypad Tab with Smart Dial
// -------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeypadTabContent(
    digits: String,
    smartDialMatches: List<PhoneContact>,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onCall: (String) -> Unit,
    onVoicemail: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Smart Dial Match Suggestions Header (T9 Results)
        if (digits.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (smartDialMatches.isNotEmpty()) 60.dp else 40.dp)
            ) {
                if (smartDialMatches.isNotEmpty()) {
                    val topMatch = smartDialMatches.first()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(GmSurface)
                            .clickable { onCall(topMatch.number) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GoogleAvatar(identifier = topMatch.name, size = 36.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(topMatch.name, color = GmTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(topMatch.number, color = GmPrimary, fontSize = 12.sp)
                        }
                        if (smartDialMatches.size > 1) {
                            Text("+${smartDialMatches.size - 1} more", color = GmTextMuted, fontSize = 11.sp)
                        }
                    }
                } else {
                    Text(
                        text = "No contact match (direct dial)",
                        color = GmTextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(40.dp))
        }

        // Digits Display
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = digits.ifEmpty { " " },
                color = GmTextPrimary,
                fontSize = if (digits.length > 12) 24.sp else 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (digits.isNotEmpty()) {
                IconButton(
                    onClick = onBackspace,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = GmTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 12-Button Keypad Grid
        val rows = listOf(
            listOf("1" to "", "2" to "ABC", "3" to "DEF"),
            listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
            listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
            listOf("*" to "", "0" to "+", "#" to "")
        )

        for (row in rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for ((digit, letters) in row) {
                    KeypadButton(
                        digit = digit,
                        letters = letters,
                        onClick = { onDigit(digit[0]) },
                        onLongClick = {
                            if (digit == "1") {
                                onVoicemail()
                            } else if (digit == "0") {
                                onDigit('+')
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Large Green Call Action Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = {
                    if (digits.isNotBlank()) {
                        onCall(digits)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF34A853), // Standard Call Green
                    contentColor = Color.White
                ),
                shape = CircleShape,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Place call",
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeypadButton(
    digit: String,
    letters: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(GmSurface)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                color = GmTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            if (letters.isNotBlank()) {
                Text(
                    text = letters,
                    color = GmTextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else if (digit == "1") {
                Icon(
                    imageVector = Icons.Default.Voicemail,
                    contentDescription = null,
                    tint = GmTextMuted,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}
