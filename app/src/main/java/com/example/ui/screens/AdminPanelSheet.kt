package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Castle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AdminCustom3DModel
import com.example.data.AdminStudioSnapshot
import com.example.data.CustomModelRole
import com.example.engine.gl.Custom3DMeshPreset
import com.example.update.AdminAppUpdateTab
import java.util.UUID

private val ADMIN_COLOR_SWATCHES = listOf(
    0xFF29B6F6L to "Sky",
    0xFFFFD54FL to "Gold",
    0xFFEF5350L to "Crimson",
    0xFF66BB6AL to "Emerald",
    0xFFAB47BCL to "Royal",
    0xFFFF7043L to "Lava",
    0xFF26A69AL to "Teal",
    0xFF263238L to "Obsidian"
)

/**
 * Full-Featured Admin Studio ModalBottomSheet (Landscape-Safe & Scrollable):
 * - Secure First-Run Email & Password Claim / Login (PBKDF2-HMAC-SHA256, zero hardcoded passwords)
 * - Live Map Studio: Real-time Trees, Forests, Flowing Water Lakes, Waterfalls, Palace, Fortress, Volcano, Gold Mine, Village & Publish Live
 * - 3D Character & Enemy (Dushman) Uploader: Zero-permission SAF `.obj` uploader + 3D Presets + Public Shop Listing + Live Spawner
 * - Admin Security Controls: Password update & Lock session
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminPanelSheet(
    snapshot: AdminStudioSnapshot,
    inGameWorld: Boolean,
    defaultWorldTitle: String,
    onClaimAdmin: (String, String) -> Result<Unit>,
    onLoginAdmin: (String, String) -> Result<Unit>,
    onUpdatePassword: (String, String) -> Result<Unit>,
    onGeneratePassword: () -> String = { "" },
    onLockAdmin: () -> Unit,
    onLaunchFullEngine: (() -> Unit)? = null,
    onImportObjUri: (Uri) -> Result<Pair<String, Int>>,
    onSaveCustomModel: (AdminCustom3DModel, Boolean) -> Unit,
    onDeleteCustomModel: (String) -> Unit,
    onSpawnCustomModel: (AdminCustom3DModel) -> Unit,
    onPlantTree: () -> Unit,
    onPlantForest: () -> Unit,
    onCreateWaterLake: (Boolean) -> Unit,
    onBuildStructure: (String, String) -> Unit,
    onSetTimeDay: () -> Unit,
    onSetTimeNight: () -> Unit,
    onToggleRain: () -> Unit,
    onPublishLiveMap: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(if (inGameWorld) 0 else 1) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x33FFD54F)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AdminPanelSettings,
                            contentDescription = "Admin Studio",
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Admin Studio • Live Control Panel",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (snapshot.isUnlockedInSession) {
                                "Verified Admin: ${snapshot.adminEmail}"
                            } else if (snapshot.isAdminClaimed) {
                                "Protected Admin Access • Login Required"
                            } else {
                                "First-Time Admin Setup • Create Your Email & Password"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (snapshot.isUnlockedInSession) Color(0xFF53FF9E) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (snapshot.isUnlockedInSession) {
                    OutlinedButton(
                        onClick = onLockAdmin,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_admin_lock")
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = "Lock Admin", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lock", fontSize = 12.sp)
                    }
                }
            }

            if (!snapshot.isUnlockedInSession) {
                AdminAuthGateCard(
                    isClaimed = snapshot.isAdminClaimed,
                    savedEmail = snapshot.adminEmail,
                    lockoutRemainingSec = snapshot.lockoutRemainingSec,
                    onGeneratePassword = onGeneratePassword,
                    onClaimAdmin = onClaimAdmin,
                    onLoginAdmin = onLoginAdmin
                )
            } else {
                // Admin Navigation Tabs
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            leadingIcon = { Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Live Map Builder") },
                            modifier = Modifier.testTag("admin_tab_map")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            leadingIcon = { Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("3D Characters & Enemies (${snapshot.customModels.size})") },
                            modifier = Modifier.testTag("admin_tab_models")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            leadingIcon = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("APK Auto-Update") },
                            modifier = Modifier.testTag("admin_tab_apk_update")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            leadingIcon = { Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Admin Security") },
                            modifier = Modifier.testTag("admin_tab_security")
                        )
                    }
                }

                when (selectedTab) {
                    0 -> AdminLiveMapTab(
                        snapshot = snapshot,
                        inGameWorld = inGameWorld,
                        defaultWorldTitle = defaultWorldTitle,
                        onLaunchFullEngine = onLaunchFullEngine,
                        onPlantTree = onPlantTree,
                        onPlantForest = onPlantForest,
                        onCreateWaterLake = onCreateWaterLake,
                        onBuildStructure = onBuildStructure,
                        onSetTimeDay = onSetTimeDay,
                        onSetTimeNight = onSetTimeNight,
                        onToggleRain = onToggleRain,
                        onPublishLiveMap = onPublishLiveMap
                    )

                    1 -> Admin3DModelsTab(
                        snapshot = snapshot,
                        inGameWorld = inGameWorld,
                        onImportObjUri = onImportObjUri,
                        onSaveCustomModel = onSaveCustomModel,
                        onDeleteCustomModel = onDeleteCustomModel,
                        onSpawnCustomModel = onSpawnCustomModel
                    )

                    2 -> AdminAppUpdateTab()

                    3 -> AdminSecurityTab(
                        adminEmail = snapshot.adminEmail,
                        onUpdatePassword = onUpdatePassword,
                        onLockAdmin = onLockAdmin
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminAuthGateCard(
    isClaimed: Boolean,
    savedEmail: String,
    lockoutRemainingSec: Int,
    onGeneratePassword: () -> String,
    onClaimAdmin: (String, String) -> Result<Unit>,
    onLoginAdmin: (String, String) -> Result<Unit>
) {
    var email by remember(savedEmail) { mutableStateOf(savedEmail.ifBlank { "admin@blockforge.studio" }) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var generatedPasswordBanner by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.2.dp, Color(0x88FFD54F)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isClaimed) "Single-User Admin Login" else "एकमात्र Admin सेटअप (1 User Only)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = CircleShape,
                    color = Color(0x3353FF9E),
                    border = BorderStroke(1.dp, Color(0x8853FF9E))
                ) {
                    Text(
                        text = "🔒 1 USER • LOGIN ONCE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF53FF9E),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = if (isClaimed) {
                    "एक बार लॉगिन करने के बाद आपका सुरक्षित सेशन सेव रहेगा। केवल यही एक एडमिन अकाउंट अधिकृत है।"
                } else {
                    "पहली बार अपना सुरक्षित Single-User Admin Email व Password बनाएं (या नीचे 'Generate Secure Password' दबाएं)। एक बार सेट होने पर कोई दूसरा व्यक्ति Admin नहीं बन सकता!"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isClaimed) {
                OutlinedButton(
                    onClick = {
                        val generated = onGeneratePassword()
                        if (generated.isNotBlank()) {
                            password = generated
                            confirmPassword = generated
                            showPassword = true
                            generatedPasswordBanner = generated
                            errorMsg = null
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.2.dp, Color(0xFFFFD54F)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_admin_generate_password")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VpnKey,
                        contentDescription = "Generate Secure Admin Password",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🔐 Generate Strong Password (ऑटो पासवर्ड बनाएं)",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (generatedPasswordBanner != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x2253FF9E),
                    border = BorderStroke(1.dp, Color(0x8853FF9E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "आपका नया सुरक्षित Admin लॉगिन (कृपया नोट करके रख लें):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF53FF9E)
                        )
                        Text(
                            text = "Email: $email\nPassword: $generatedPasswordBanner",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (lockoutRemainingSec > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "सुरक्षा लॉक सक्रिय: कृपया $lockoutRemainingSec सेकंड प्रतीक्षा करें।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMsg = null },
                label = { Text("Admin Email") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_email_input")
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMsg = null },
                label = { Text(if (isClaimed) "Admin Password" else "Strong Password (min 6 chars)") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Toggle Password Visibility"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_password_input")
            )

            if (!isClaimed) {
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorMsg = null },
                    label = { Text("Confirm Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_confirm_password_input")
                )
            }

            if (errorMsg != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Button(
                onClick = {
                    if (!isClaimed && password != confirmPassword) {
                        errorMsg = "दोनों Password एक जैसे नहीं हैं।"
                        return@Button
                    }
                    val result = if (isClaimed) onLoginAdmin(email, password) else onClaimAdmin(email, password)
                    result.onFailure { err ->
                        errorMsg = err.localizedMessage ?: "Authentication failed."
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_admin_auth_submit")
            ) {
                Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isClaimed) "Unlock Admin Panel (Login Once)" else "Lock Single-Owner Admin & Unlock")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminLiveMapTab(
    snapshot: AdminStudioSnapshot,
    inGameWorld: Boolean,
    defaultWorldTitle: String,
    onLaunchFullEngine: (() -> Unit)?,
    onPlantTree: () -> Unit,
    onPlantForest: () -> Unit,
    onCreateWaterLake: (Boolean) -> Unit,
    onBuildStructure: (String, String) -> Unit,
    onSetTimeDay: () -> Unit,
    onSetTimeNight: () -> Unit,
    onToggleRain: () -> Unit,
    onPublishLiveMap: (String, String) -> Unit
) {
    var mapTitle by remember(snapshot.liveMap.title, defaultWorldTitle) {
        mutableStateOf(snapshot.liveMap.title.ifBlank { defaultWorldTitle })
    }
    var announcement by remember(snapshot.liveMap.announcementBanner) {
        mutableStateOf(snapshot.liveMap.announcementBanner)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (inGameWorld && onLaunchFullEngine != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF10242C),
                border = BorderStroke(1.6.dp, Color(0xFF00E5FF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🛠️ Full-Screen 3D Game Engine Studio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF7CEBFF)
                    )
                    Text(
                        text = "रियल-टाइम में उड़कर पूरे मैप में गड्ढा-गुड्डी, घास-फूस, झाड़ी, पेड़, फूल, जंगल, रोड, शादी मंडप, हवाई पट्टी, सेना व युद्ध सेट करें — फुल स्क्रीन Back बटन व #1 Rank Publish के साथ!",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD6EEF8)
                    )
                    Button(
                        onClick = onLaunchFullEngine,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor = Color(0xFF06161C)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_launch_full_engine")
                    ) {
                        Icon(Icons.Rounded.RocketLaunch, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Full-Screen 3D Map Engine", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        if (inGameWorld) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "रियल-टाइम वर्ल्ड टूल्स (आपके सामने तुरंत बनेगा)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AdminActionPill("पेड़ लगाओ", Icons.Rounded.Park, Color(0xFF4CAF50), onPlantTree, "admin_plant_tree")
                        AdminActionPill("घना जंगल (14 पेड़)", Icons.Rounded.Forest, Color(0xFF2E7D32), onPlantForest, "admin_plant_forest")
                        AdminActionPill("असली पानी झील", Icons.Rounded.Water, Color(0xFF29B6F6), { onCreateWaterLake(false) }, "admin_water_lake")
                        AdminActionPill("बहता झरना + साउंड", Icons.Rounded.WaterDrop, Color(0xFF00ACC1), { onCreateWaterLake(true) }, "admin_waterfall")
                        AdminActionPill("राजमहल (Palace)", Icons.Rounded.Castle, Color(0xFFFFB300), { onBuildStructure("PALACE", "शाही राजमहल") }, "admin_build_palace")
                        AdminActionPill("क़िला (Fortress)", Icons.Rounded.Terrain, Color(0xFF78909C), { onBuildStructure("FORTRESS", "मज़बूत क़िला") }, "admin_build_fortress")
                        AdminActionPill("ज्वालामुखी + लावा", Icons.Rounded.AutoAwesome, Color(0xFFFF5722), { onBuildStructure("VOLCANO", "ज्वालामुखी और लावा") }, "admin_build_volcano")
                        AdminActionPill("सोने-हीरे की खदान", Icons.Rounded.AutoAwesome, Color(0xFFFFD54F), { onBuildStructure("GOLD_MINE", "सोने-हीरे की खदान") }, "admin_build_mine")
                        AdminActionPill("पूरा गाँव बसाओ", Icons.Rounded.Castle, Color(0xFF8D6E63), { onBuildStructure("VILLAGE", "सुंदर गाँव") }, "admin_build_village")
                        AdminActionPill("दिन (Day)", Icons.Rounded.WbSunny, Color(0xFFFFCA28), onSetTimeDay, "admin_time_day")
                        AdminActionPill("रात (Night)", Icons.Rounded.WbSunny, Color(0xFF5C6BC0), onSetTimeNight, "admin_time_night")
                        AdminActionPill("बारिश (Rain)", Icons.Rounded.WaterDrop, Color(0xFF4FC3F7), onToggleRain, "admin_toggle_rain")
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tip: किसी भी वर्ल्ड में जाकर Admin बटन खोलें तो पेड़, जंगल, पानी, झरना, राजमहल और ज्वालामुखी सीधे 3D वर्ल्ड में रियल-टाइम बना सकते हैं!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Publish Map Live Box
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, Color(0x5553FF9E)),
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
                    Text(
                        text = "Publish Live Map & Broadcast",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (snapshot.liveMap.published) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x3353FF9E)
                        ) {
                            Text(
                                text = "● LIVE #${snapshot.liveMap.liveEditsCount}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF53FF9E),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = mapTitle,
                    onValueChange = { if (it.length <= 45) mapTitle = it },
                    label = { Text("Live Map Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = announcement,
                    onValueChange = { if (it.length <= 120) announcement = it },
                    label = { Text("Live Announcement Banner (सभी खिलाड़ियों को दिखेगा)") },
                    placeholder = { Text("जैसे: नया राजमहल और 3D दुश्मन बॉस लाइव हो गया है!") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onPublishLiveMap(mapTitle, announcement) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_admin_publish_live")
                ) {
                    Icon(Icons.Rounded.Publish, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Publish Map Real-Time Live")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Admin3DModelsTab(
    snapshot: AdminStudioSnapshot,
    inGameWorld: Boolean,
    onImportObjUri: (Uri) -> Result<Pair<String, Int>>,
    onSaveCustomModel: (AdminCustom3DModel, Boolean) -> Unit,
    onDeleteCustomModel: (String) -> Unit,
    onSpawnCustomModel: (AdminCustom3DModel) -> Unit
) {
    var role by remember { mutableStateOf(CustomModelRole.SHOP_CHARACTER) }
    var name by remember { mutableStateOf("Cyber Veer 3D") }
    var priceText by remember { mutableStateOf("120") }
    var hpSlider by remember { mutableFloatStateOf(140f) }
    var damageSlider by remember { mutableFloatStateOf(2f) }
    var speedSlider by remember { mutableFloatStateOf(3.2f) }
    var scaleSlider by remember { mutableFloatStateOf(1.08f) }
    var primaryHex by remember { mutableLongStateOf(0xFF29B6F6L) }
    var accentHex by remember { mutableLongStateOf(0xFFFFD54FL) }
    var selectedPreset by remember { mutableStateOf(Custom3DMeshPreset.CYBER_KNIGHT) }
    var listInShop by remember { mutableStateOf(true) }
    var hindiGreeting by remember {
        mutableStateOf("नमस्ते दोस्त! मैं आपका 3D साथी हूँ, बोलिए घर बनाना है या पानी लाना है?")
    }
    var uploadedFileName by remember { mutableStateOf<String?>(null) }
    var uploadedTriCount by remember { mutableIntStateOf(0) }
    var uploadStatus by remember { mutableStateOf<String?>(null) }

    // Zero-Permission Android Storage Access Framework (SAF) 3D .OBJ Picker
    val objPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportObjUri(uri).fold(
                onSuccess = { (savedName, triCount) ->
                    uploadedFileName = savedName
                    uploadedTriCount = triCount
                    uploadStatus = "✓ 3D .OBJ फ़ाइल अपलोड हो गई ($triCount triangles)!"
                },
                onFailure = { err ->
                    uploadStatus = "त्रुटि: ${err.localizedMessage ?: "मान्य .obj फ़ाइल चुनें।"}"
                }
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "नया 3D Character / Enemy (दुश्मन) बनाएं या .OBJ अपलोड करें",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                // Role toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CustomModelRole.entries.forEach { r ->
                        FilterChip(
                            selected = role == r,
                            onClick = {
                                role = r
                                if (r == CustomModelRole.ENEMY_DUSHMAN) {
                                    if (name == "Cyber Veer 3D") name = "Rakshas Boss 3D"
                                    primaryHex = 0xFFEF5350L
                                    selectedPreset = Custom3DMeshPreset.LAVA_DEMON
                                    hpSlider = 240f
                                    damageSlider = 3f
                                } else {
                                    if (name == "Rakshas Boss 3D") name = "Cyber Veer 3D"
                                    primaryHex = 0xFF29B6F6L
                                    selectedPreset = Custom3DMeshPreset.CYBER_KNIGHT
                                    hpSlider = 140f
                                }
                            },
                            label = {
                                Text(
                                    text = if (r == CustomModelRole.SHOP_CHARACTER) "Public 3D Shop Character" else "Hostile Enemy (दुश्मन)",
                                    fontSize = 11.sp
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Upload 3D .OBJ File button (SAF)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { objPickerLauncher.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_upload_3d_obj")
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = "Upload 3D OBJ File")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uploadedFileName != null) "Change .OBJ ($uploadedTriCount tris)" else "Upload 3D .OBJ File",
                            fontSize = 12.sp
                        )
                    }
                    if (uploadedFileName != null) {
                        OutlinedButton(
                            onClick = {
                                uploadedFileName = null
                                uploadedTriCount = 0
                                uploadStatus = "Built-in 3D Preset active"
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text("Clear", fontSize = 12.sp)
                        }
                    }
                }

                if (uploadStatus != null) {
                    Text(
                        text = uploadStatus!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uploadedFileName != null) Color(0xFF53FF9E) else MaterialTheme.colorScheme.error
                    )
                }

                // Built-in 3D Preset selector
                Text("3D Model Style Preset:", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Custom3DMeshPreset.entries) { preset ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = { selectedPreset = preset },
                            label = { Text(preset.label, fontSize = 11.sp) }
                        )
                    }
                }

                // Name & Price row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 32) name = it },
                        label = { Text("3D Character / Enemy Name") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.6f)
                            .testTag("admin_model_name_input")
                    )
                    if (role == CustomModelRole.SHOP_CHARACTER) {
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { priceText = it.filter { c -> c.isDigit() }.take(4) },
                            label = { Text("Shop Coins") },
                            singleLine = true,
                            modifier = Modifier.weight(0.9f)
                        )
                    }
                }

                // Colors
                Text("Primary 3D Armor/Body Color:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ADMIN_COLOR_SWATCHES.forEach { (hex, _) ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (primaryHex == hex) 2.5.dp else 1.dp,
                                    color = if (primaryHex == hex) Color.White else Color(0x55FFFFFF),
                                    shape = CircleShape
                                )
                                .clickable { primaryHex = hex }
                        )
                    }
                }

                // Sliders (HP, Damage, Scale)
                Text(
                    text = "HP: ${hpSlider.toInt()}  •  Damage: ${damageSlider.toInt()}  •  Speed: ${"%.1f".format(speedSlider)}  •  Size: ${"%.2f".format(scaleSlider)}x",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Slider(
                        value = hpSlider,
                        onValueChange = { hpSlider = it },
                        valueRange = 60f..500f,
                        modifier = Modifier.weight(1f)
                    )
                    Slider(
                        value = scaleSlider,
                        onValueChange = { scaleSlider = it },
                        valueRange = 0.75f..1.65f,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (role == CustomModelRole.SHOP_CHARACTER) {
                    OutlinedTextField(
                        value = hindiGreeting,
                        onValueChange = { if (it.length <= 140) hindiGreeting = it },
                        label = { Text("Hindi Live Voice Greeting (बोलने का डायलॉग)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Public Royal Bazaar Shop में लिस्ट करें (प्लेयर खरीदकर 3D Avatar बना सकेंगे)",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = listInShop,
                            onCheckedChange = { listInShop = it }
                        )
                    }
                }

                val buildModel: () -> AdminCustom3DModel = {
                    AdminCustom3DModel(
                        id = UUID.randomUUID().toString().take(10),
                        name = name.trim().ifBlank { if (role == CustomModelRole.ENEMY_DUSHMAN) "Boss Enemy 3D" else "Hero 3D" },
                        role = role,
                        listedInShop = (role == CustomModelRole.SHOP_CHARACTER && listInShop),
                        priceCoins = priceText.toIntOrNull()?.coerceIn(0, 9999) ?: 120,
                        maxHealth = hpSlider.toInt(),
                        attackDamage = damageSlider.toInt(),
                        moveSpeed = speedSlider,
                        scale = scaleSlider,
                        primaryColorHex = primaryHex,
                        accentColorHex = accentHex,
                        meshPreset = selectedPreset,
                        uploadedObjFileName = uploadedFileName,
                        uploadedTriangleCount = uploadedTriCount,
                        hindiGreeting = hindiGreeting.trim().ifBlank { "नमस्ते मित्र! मैं तैयार हूँ।" }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onSaveCustomModel(buildModel(), false) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_admin_save_model")
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (role == CustomModelRole.SHOP_CHARACTER) "List in Shop" else "Save Enemy", fontSize = 12.sp)
                    }
                    if (inGameWorld) {
                        Button(
                            onClick = { onSaveCustomModel(buildModel(), true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (role == CustomModelRole.ENEMY_DUSHMAN) Color(0xFFD32F2F) else Color(0xFF22A06B)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_admin_save_and_spawn")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save & Spawn Live", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Catalog of created 3D models
        if (snapshot.customModels.isNotEmpty()) {
            Text(
                text = "Published 3D Characters & Enemies (${snapshot.customModels.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            snapshot.customModels.forEach { model ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, Color(model.primaryColorHex).copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(model.primaryColorHex))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = model.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = buildString {
                                    append(if (model.role == CustomModelRole.ENEMY_DUSHMAN) "Enemy Boss" else "Shop 3D Avatar")
                                    append(" • HP ${model.maxHealth}")
                                    if (model.uploadedTriangleCount > 0) append(" • .OBJ (${model.uploadedTriangleCount} tris)")
                                    if (model.listedInShop) append(" • ${model.priceCoins} 🪙")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (inGameWorld) {
                            OutlinedButton(
                                onClick = { onSpawnCustomModel(model) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Spawn", fontSize = 11.sp)
                            }
                        }
                        IconButton(onClick = { onDeleteCustomModel(model.id) }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete ${model.name}",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminSecurityTab(
    adminEmail: String,
    onUpdatePassword: (String, String) -> Result<Unit>,
    onLockAdmin: () -> Unit
) {
    var currentPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Registered Admin Account",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Email: $adminEmail",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF53FF9E)
            )
            HorizontalDivider()
            Text("Change Admin Password", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = currentPass,
                onValueChange = { currentPass = it; feedback = null },
                label = { Text("Current Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = newPass,
                onValueChange = { newPass = it; feedback = null },
                label = { Text("New Password (min 6 chars)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (feedback != null) {
                Text(
                    text = feedback!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error else Color(0xFF53FF9E)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onUpdatePassword(currentPass, newPass).fold(
                            onSuccess = {
                                isError = false
                                feedback = "Admin Password सफलतापूर्वक बदल गया!"
                                currentPass = ""
                                newPass = ""
                            },
                            onFailure = { err ->
                                isError = true
                                feedback = err.localizedMessage ?: "Password update failed."
                            }
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Update Password")
                }
                OutlinedButton(
                    onClick = onLockAdmin,
                    modifier = Modifier.weight(0.7f)
                ) {
                    Text("Lock Now")
                }
            }
        }
    }
}

@Composable
private fun AdminActionPill(
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = accent, modifier = Modifier.size(16.dp))
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}
