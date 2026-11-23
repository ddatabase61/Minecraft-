package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TempleHindu
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.AdminCustom3DModel
import com.example.data.CustomModelRole
import com.example.data.model.GameMode
import com.example.data.model.ROYAL_BAZAAR_PRODUCTS
import com.example.data.model.ShopCategory
import com.example.data.model.ShopProduct
import com.example.engine.gl.AiGoal
import com.example.engine.gl.TalkableNpcSnapshot
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.GameWorldUiState

/**
 * Mandatory Landscape Gate Overlay: enforces rotating the phone to Landscape before playing.
 */
@Composable
fun LandscapeRotationGateOverlay(
    onRequestLandscape: () -> Unit,
    onExitToHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xF508120E), Color(0xF511261E), Color(0xF5070E0B))
                )
            )
            .testTag("landscape_rotation_gate"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF13221C),
            border = BorderStroke(1.5.dp, Color(0xFF39D98A)),
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0x3339D98A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ScreenRotation,
                        contentDescription = "Rotate phone to landscape",
                        tint = Color(0xFF53FF9E),
                        modifier = Modifier.size(38.dp)
                    )
                }
                Text(
                    text = "फोन को आड़ा (Landscape) घुमाएं",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Rotate your phone horizontally to play BlockForge. Gameplay starts automatically once your screen is in Landscape mode.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFBBD2C7),
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onRequestLandscape,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_force_landscape")
                ) {
                    Icon(Icons.Rounded.ScreenRotation, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Switch to Landscape Now")
                }
                OutlinedButton(
                    onClick = onExitToHub,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back to Worlds")
                }
            }
        }
    }
}

/**
 * AI Friends & Real Hindi Voice Conversation Sheet:
 * - Talk to Raja, Pujari, Majdur, Amir Seth, Garib Kisan, Shri Krishna, Shri Ram, Hanuman, Bheem, Raju
 * - Live Hindi Microphone (`SpeechRecognizer` `hi-IN`) + Real Human Hindi Voice (`TextToSpeech` `hi-IN`)
 * - Powered by Firebase AI Logic (`gemini-3.8-flash`) with independent in-world action decisions!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HindiAiFriendSheet(
    uiState: GameWorldUiState,
    onSelectNpc: (TalkableNpcSnapshot) -> Unit,
    onMakeFriend: () -> Unit,
    onCommandAction: (AiGoal) -> Unit,
    onSendHindiMessage: (String) -> Unit,
    onStartVoiceMic: () -> Unit,
    onStopVoiceMic: () -> Unit,
    onStopSpeaking: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var textInput by remember { mutableStateOf("") }
    var micPermissionNotice by remember { mutableStateOf<String?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micPermissionNotice = null
            onStartVoiceMic()
        } else {
            micPermissionNotice = "हिंदी आवाज़ में बात करने के लिए Microphone अनुमति आवश्यक है। नीचे टाइप करके भी बात कर सकते हैं।"
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            onStopVoiceMic()
            onStopSpeaking()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AI Dost • हिंदी आवाज़ बातचीत",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiState.voiceStatusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.isVoiceListening) Color(0xFF53FF9E) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (uiState.isVoiceSpeaking) {
                    OutlinedButton(
                        onClick = onStopSpeaking,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeOff, contentDescription = "Stop Voice", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("चुप करें", fontSize = 12.sp)
                    }
                }
            }

            // Selectable Nearby Autonomous AI Characters
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.talkableNpcs, key = { it.id }) { npc ->
                    val selected = uiState.activeNpc?.id == npc.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) Color(0xFF1F3B2F) else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) Color(npc.kind.badgeColorHex) else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .clickable { onSelectNpc(npc) }
                            .testTag("npc_chip_${npc.kind.name.lowercase()}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(npc.kind.badgeColorHex))
                                )
                                Text(
                                    text = npc.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${npc.distanceBlocks}m • ${if (npc.isFriend) "दोस्त ★" else "दोस्ती ${npc.friendshipLevel}%"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            val active = uiState.activeNpc
            if (active != null) {
                // Character Status, Friendship Progress & Autonomous World Actions
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${active.displayName} • ${active.goalLabelHindi}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = active.kind.roleSummaryHindi,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = onMakeFriend,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("btn_make_npc_friend")
                            ) {
                                Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (active.isFriend) "पक्का दोस्त" else "दोस्त बनाएं", fontSize = 12.sp)
                            }
                        }

                        LinearProgressIndicator(
                            progress = { active.friendshipLevel / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFFFF5C8A),
                            trackColor = Color(0x33FFFFFF)
                        )

                        // Quick World Action Suggestions
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = { onCommandAction(AiGoal.BUILD_HOUSE) },
                                    leadingIcon = { Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("घर बनाओ") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = { onCommandAction(AiGoal.BRING_WATER) },
                                    leadingIcon = { Icon(Icons.Rounded.WaterDrop, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("पानी यहाँ लाओ") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = { onCommandAction(AiGoal.PLANT_TREES) },
                                    leadingIcon = { Icon(Icons.Rounded.Park, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("पेड़ लगाओ") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = { onCommandAction(AiGoal.BUILD_MANDIR) },
                                    leadingIcon = { Icon(Icons.Rounded.TempleHindu, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("मंदिर बनाओ") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = { onCommandAction(AiGoal.DO_PUJA) },
                                    leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("पूजा करो") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = { onCommandAction(AiGoal.FOLLOW_FRIEND) },
                                    leadingIcon = { Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("साथ चलो") }
                                )
                            }
                        }
                    }
                }
            }

            // Error display (transparent AI / Mic error reporting)
            val errText = micPermissionNotice ?: uiState.npcAiError
            if (errText != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Hindi Conversation Feed
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
            ) {
                if (uiState.npcChatHistory.isEmpty() && !uiState.npcAiThinking) {
                    Box(modifier = Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "माइक बटन दबाएं और हिंदी में बोलें (जैसे: 'नमस्ते दोस्त, मेरे लिए एक घर या मंदिर बनाओ' या 'आप क्या कर रहे हैं?')",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.npcChatHistory) { msg ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = if (msg.isPlayer) Alignment.End else Alignment.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (msg.isPlayer) Color(0xFF1D4D39) else MaterialTheme.colorScheme.surfaceContainerHighest
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                                        Text(
                                            text = msg.senderName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (msg.isPlayer) Color(0xFF7CFFB2) else Color(0xFFFFD54F)
                                        )
                                        Text(
                                            text = msg.textHindi,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White
                                        )
                                        if (msg.decidedActionHindi != null) {
                                            Text(
                                                text = "★ निर्णय: ${msg.decidedActionHindi}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF53FF9E)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (uiState.npcAiThinking) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text("सोच रहे हैं और हिंदी में जवाब तैयार कर रहे हैं...", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            // Voice Mic + Hindi Text Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (uiState.isVoiceListening) {
                            onStopVoiceMic()
                        } else {
                            val hasMic = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasMic) {
                                onStartVoiceMic()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isVoiceListening) Color(0xFFE53935) else Color(0xFF22A06B)
                    ),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("btn_hindi_voice_mic")
                ) {
                    Icon(
                        imageVector = if (uiState.isVoiceListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = if (uiState.isVoiceListening) "Stop Hindi Voice" else "Speak in Hindi",
                        tint = Color.White
                    )
                }

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("हिंदी में लिखें या माइक से बोलें...") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("npc_hindi_chat_input")
                )

                Button(
                    onClick = {
                        if (textInput.isNotBlank() && !uiState.npcAiThinking) {
                            onSendHindiMessage(textInput)
                            textInput = ""
                        }
                    },
                    enabled = !uiState.npcAiThinking && textInput.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("npc_hindi_chat_send")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send Hindi Message")
                }
            }
        }
    }
}

/**
 * Royal Bazaar In-Game Shop Sheet ("Bahut Sare Purchase Products") with Gold Coins!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoyalBazaarShopSheet(
    uiState: GameWorldUiState,
    onPurchase: (ShopProduct) -> Unit,
    onEquipCustom3DModel: (AdminCustom3DModel, Boolean) -> Unit,
    onResetDefaultAvatar: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCustom3DTab by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(ShopCategory.GEAR) }
    val filtered = remember(selectedCategory) {
        ROYAL_BAZAAR_PRODUCTS.filter { it.category == selectedCategory }
    }
    val publicCustomModels = remember(uiState.adminSnapshot.customModels) {
        uiState.adminSnapshot.customModels.filter {
            it.role == CustomModelRole.SHOP_CHARACTER && it.listedInShop
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Royal Bazaar • शाही दुकान",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Buy weapons, kits & Admin-uploaded Public 3D Character Models!",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF3A2B0E),
                    border = BorderStroke(1.5.dp, Color(0xFFFFD54F))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MonetizationOn,
                            contentDescription = "Gold Coins",
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (uiState.gameMode == GameMode.CREATIVE) "∞" else "${uiState.goldCoins}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFE57F)
                        )
                    }
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = showCustom3DTab,
                        onClick = { showCustom3DTab = true },
                        leadingIcon = { Icon(Icons.Rounded.ViewInAr, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("3D Characters (${publicCustomModels.size})") },
                        modifier = Modifier.testTag("shop_tab_custom_3d")
                    )
                }
                items(ShopCategory.entries) { category ->
                    FilterChip(
                        selected = !showCustom3DTab && selectedCategory == category,
                        onClick = {
                            showCustom3DTab = false
                            selectedCategory = category
                        },
                        label = { Text(category.label) }
                    )
                }
            }

            if (showCustom3DTab) {
                if (uiState.adminSnapshot.equippedPublicModelId != null) {
                    OutlinedButton(
                        onClick = onResetDefaultAvatar,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Switch Back to Default Avatar")
                    }
                }
                if (publicCustomModels.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "अभी Admin ने कोई Public 3D Character लिस्ट नहीं किया है। ऊपर Admin (Shield) बटन से 3D .OBJ फ़ाइल अपलोड कर Shop में लिस्ट करें!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(publicCustomModels, key = { it.id }) { model ->
                            val equipped = uiState.adminSnapshot.equippedPublicModelId == model.id
                            val owned = uiState.ownedProductIds.contains("custom_3d_${model.id}") ||
                                uiState.gameMode == GameMode.CREATIVE
                            val affordable = owned || uiState.goldCoins >= model.priceCoins

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    width = if (equipped) 2.dp else 1.dp,
                                    color = if (equipped) Color(0xFF53FF9E) else Color(model.primaryColorHex).copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(model.primaryColorHex).copy(alpha = 0.3f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ViewInAr,
                                                contentDescription = model.name,
                                                tint = Color(model.primaryColorHex),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = model.name + if (equipped) " ★ EQUIPPED" else "",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = buildString {
                                                    append(model.meshPreset.label)
                                                    if (model.uploadedTriangleCount > 0) append(" • Custom .OBJ (${model.uploadedTriangleCount} tris)")
                                                    append(" • ${if (owned) "Unlocked" else "${model.priceCoins} 🪙"}")
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { onEquipCustom3DModel(model, false) },
                                            enabled = affordable,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("shop_use_3d_${model.id}")
                                        ) {
                                            Text(
                                                text = if (equipped) "Active 3D Avatar" else if (owned) "Use as My 3D Model" else "Buy & Use (${model.priceCoins} 🪙)",
                                                fontSize = 11.sp
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { onEquipCustom3DModel(model, true) },
                                            enabled = affordable,
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Spawn Friend", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { product ->
                        val owned = product.oneTimeUnlock && uiState.ownedProductIds.contains(product.id)
                        val affordable = uiState.gameMode == GameMode.CREATIVE || uiState.goldCoins >= product.priceCoins

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, product.accentColor.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(product.accentColor.copy(alpha = 0.22f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (owned) Icons.Rounded.CheckCircle else Icons.Rounded.AutoAwesome,
                                        contentDescription = product.title,
                                        tint = product.accentColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = product.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = product.subtitleHindi,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { onPurchase(product) },
                                    enabled = !owned && affordable,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("shop_buy_${product.id}")
                                ) {
                                    Text(
                                        text = when {
                                            owned -> "Owned"
                                            uiState.gameMode == GameMode.CREATIVE -> "Free"
                                            else -> "${product.priceCoins} 🪙"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
