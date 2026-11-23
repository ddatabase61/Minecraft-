package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.AdminStudioStore
import com.example.data.model.BiomeType
import com.example.data.model.BlockType
import com.example.data.model.BuilderProfile
import com.example.data.model.GameMode
import com.example.data.model.VoxelWorldDoc
import com.example.engine.MiniVoxelBlockIcon
import com.example.ui.viewmodel.HubViewModel
import com.example.ui.viewmodel.UiState
import kotlin.random.Random

@Composable
fun WorldsHubScreen(
    viewModel: HubViewModel,
    onOpenWorld: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val worldsState by viewModel.worldsState.collectAsStateWithLifecycle()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val busyMessage by viewModel.busyMessage.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val adminStore = remember(context) { AdminStudioStore.getInstance(context) }
    val adminSnapshot by adminStore.snapshot.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showHubAdminSheet by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<VoxelWorldDoc?>(null) }
    var deleteTarget by remember { mutableStateOf<VoxelWorldDoc?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedbackMessage) {
        val msg = feedbackMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    val navItems = listOf(
        Triple(stringResource(R.string.nav_worlds), Icons.Rounded.Public, Icons.Outlined.Public),
        Triple(stringResource(R.string.nav_codex), Icons.Rounded.AutoStories, Icons.Outlined.AutoStories),
        Triple(stringResource(R.string.nav_profile), Icons.Rounded.Person, Icons.Outlined.Person)
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        navItems.forEachIndexed { idx, (label, filled, outlined) ->
                            NavigationBarItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == idx) filled else outlined,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_tab_$idx")
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (selectedTab == 0) {
                    ExtendedFloatingActionButton(
                        onClick = { showCreateDialog = true },
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.action_new_world)) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("create_world_fab")
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))
                        navItems.forEachIndexed { idx, (label, filled, outlined) ->
                            NavigationRailItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == idx) filled else outlined,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    val profile = (profileState as? UiState.Success)?.data
                    val worldCount = (worldsState as? UiState.Success)?.data?.size ?: 0

                    when (selectedTab) {
                        0 -> WorldsTabContent(
                            worldsState = worldsState,
                            profile = profile,
                            isCloudBacked = viewModel.isCloudBacked,
                            liveAnnouncement = if (adminSnapshot.liveMap.published) adminSnapshot.liveMap.announcementBanner else "",
                            liveMapTitle = if (adminSnapshot.liveMap.published) adminSnapshot.liveMap.title else "",
                            onOpenAdminStudio = { showHubAdminSheet = true },
                            onOpenWorld = onOpenWorld,
                            onCreateWorldClick = { showCreateDialog = true },
                            onQuickCreateStarter = { name, biome, mode ->
                                viewModel.createWorld(
                                    name = name,
                                    biome = biome,
                                    gameMode = mode,
                                    seed = Random.nextLong(10_000L, 999_999L),
                                    onCreated = onOpenWorld
                                )
                            },
                            onRenameRequest = { renameTarget = it },
                            onDeleteRequest = { deleteTarget = it }
                        )

                        1 -> CodexTabContent()

                        2 -> ProfileTabContent(
                            profileState = profileState,
                            worldCount = worldCount,
                            isCloudBacked = viewModel.isCloudBacked,
                            onUpdateDisplayName = viewModel::updateDisplayName,
                            onSignOut = onSignOut
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = busyMessage != null,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            tonalElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp
                                )
                                Text(
                                    text = busyMessage ?: "",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateWorldDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, biome, mode, seed ->
                showCreateDialog = false
                viewModel.createWorld(
                    name = name,
                    biome = biome,
                    gameMode = mode,
                    seed = seed,
                    onCreated = onOpenWorld
                )
            }
        )
    }

    renameTarget?.let { world ->
        RenameWorldDialog(
            initialName = world.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                viewModel.renameWorld(world.id, newName)
            }
        )
    }

    deleteTarget?.let { world ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${world.name}\"?") },
            text = {
                Text("This permanently removes the world and all custom voxel builds.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteTarget = null
                        viewModel.deleteWorld(world.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_world_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showHubAdminSheet) {
        AdminPanelSheet(
            snapshot = adminSnapshot,
            inGameWorld = false,
            defaultWorldTitle = "Admin Live Royal World",
            onClaimAdmin = adminStore::claimAdminCredentials,
            onLoginAdmin = adminStore::loginAdmin,
            onUpdatePassword = adminStore::updateAdminPassword,
            onLockAdmin = adminStore::lockAdminSession,
            onImportObjUri = adminStore::importObjFileFromUri,
            onSaveCustomModel = { model, _ -> adminStore.saveCustomModel(model) },
            onDeleteCustomModel = adminStore::deleteCustomModel,
            onSpawnCustomModel = {},
            onPlantTree = {},
            onPlantForest = {},
            onCreateWaterLake = {},
            onBuildStructure = { _, _ -> },
            onSetTimeDay = {},
            onSetTimeNight = {},
            onToggleRain = {},
            onPublishLiveMap = { title, banner ->
                adminStore.publishLiveMap(title, BiomeType.EMERALD_VALLEY, banner)
            },
            onDismiss = { showHubAdminSheet = false }
        )
    }
}

@Composable
private fun WorldsTabContent(
    worldsState: UiState<List<VoxelWorldDoc>>,
    profile: BuilderProfile?,
    isCloudBacked: Boolean,
    liveAnnouncement: String,
    liveMapTitle: String,
    onOpenAdminStudio: () -> Unit,
    onOpenWorld: (String) -> Unit,
    onCreateWorldClick: () -> Unit,
    onQuickCreateStarter: (String, BiomeType, GameMode) -> Unit,
    onRenameRequest: (VoxelWorldDoc) -> Unit,
    onDeleteRequest: (VoxelWorldDoc) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 760.dp),
        contentPadding = PaddingValues(bottom = 104.dp)
    ) {
        item {
            HeroWorldHeader(
                profile = profile,
                onOpenAdminStudio = onOpenAdminStudio
            )
        }

        if (liveMapTitle.isNotBlank() || liveAnnouncement.isNotBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF281E0B),
                    border = BorderStroke(1.dp, Color(0xFFFFD54F)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Campaign,
                            contentDescription = "Admin Live Map",
                            tint = Color(0xFFFFD54F)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ADMIN LIVE: ${liveMapTitle.ifBlank { "Royal World" }}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFE57F)
                            )
                            if (liveAnnouncement.isNotBlank()) {
                                Text(
                                    text = liveAnnouncement,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFFF8E1)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Explore 8 Minecraft-Style Maps (1-Tap Play)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val featuredMaps = listOf(
                        Triple("Mega Flower Valley", BiomeType.FLOWER_PARADISE, GameMode.CREATIVE),
                        Triple("Royal Flower Kingdom", BiomeType.EMERALD_VALLEY, GameMode.SURVIVAL),
                        Triple("Amazon Jungle", BiomeType.JUNGLE_RIVER, GameMode.SURVIVAL),
                        Triple("Floating Sky Islands", BiomeType.SKY_KINGDOM, GameMode.CREATIVE),
                        Triple("Coral Archipelago", BiomeType.OCEAN_ISLANDS, GameMode.SURVIVAL),
                        Triple("Pyramid Oasis", BiomeType.DESERT_DUNES, GameMode.SURVIVAL),
                        Triple("Himalaya Citadel", BiomeType.FROST_PEAKS, GameMode.CREATIVE),
                        Triple("Jwalamukhi & Caves", BiomeType.VOLCANIC_BADLANDS, GameMode.SURVIVAL)
                    )
                    items(featuredMaps, key = { it.second.name }) { (title, biome, mode) ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.2.dp, biome.accentColor.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .width(156.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onQuickCreateStarter(title, biome, mode) }
                                .testTag("quick_map_${biome.name.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MiniVoxelBlockIcon(block = biomePreviewBlock(biome), size = 30.dp)
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = biome.accentColor,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Text(
                                    text = biome.tagline,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isCloudBacked) "Your Cloud Worlds" else "Guest Local Worlds",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isCloudBacked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDone,
                            contentDescription = null,
                            tint = if (isCloudBacked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isCloudBacked) "LIVE SYNC" else "GUEST LOCAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCloudBacked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        when (worldsState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = worldsState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                val worlds = worldsState.data
                if (worlds.isEmpty()) {
                    item {
                        EmptyWorldsCard(
                            onCreateCustom = onCreateWorldClick,
                            onQuickCreateStarter = onQuickCreateStarter
                        )
                    }
                } else {
                    items(worlds, key = { it.id }) { world ->
                        WorldCard(
                            world = world,
                            onPlay = { onOpenWorld(world.id) },
                            onRename = { onRenameRequest(world) },
                            onDelete = { onDeleteRequest(world) },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroWorldHeader(
    profile: BuilderProfile?,
    onOpenAdminStudio: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_hero_world_1795270880117),
            contentDescription = stringResource(R.string.cd_world_banner),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0x8808120F),
                            0.45f to Color(0x4408120F),
                            1.0f to MaterialTheme.colorScheme.background
                        )
                    )
                )
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xCC1C1609),
            border = BorderStroke(1.2.dp, Color(0xFFFFD54F)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 34.dp, end = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenAdminStudio)
                .testTag("hub_admin_studio_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AdminPanelSettings,
                    contentDescription = "Open Admin Studio",
                    tint = Color(0xFFFFD54F),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Admin Panel",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFE57F)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MiniVoxelBlockIcon(block = BlockType.GRASS, size = 32.dp)
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.player_custom_avatar),
                    contentDescription = "Your Character Portrait",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                ) {
                    Text(
                        text = "LVL ${profile?.builderLevel ?: 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = "Welcome back, ${profile?.displayName ?: "Builder"}",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFFE7F5EE),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyWorldsCard(
    onCreateCustom: () -> Unit,
    onQuickCreateStarter: (String, BiomeType, GameMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MiniVoxelBlockIcon(block = BlockType.DIAMOND_ORE, size = 54.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.empty_worlds_title),
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.empty_worlds_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onCreateCustom,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("empty_create_world_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Forge Custom World")
                }
            }
        }

        Text(
            text = "Quick-Launch Starter Realms",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val starters = listOf(
            Triple("Mega Flower Valley", BiomeType.FLOWER_PARADISE, GameMode.CREATIVE),
            Triple("Royal Flower Kingdom", BiomeType.EMERALD_VALLEY, GameMode.SURVIVAL),
            Triple("Amazon Jungle River", BiomeType.JUNGLE_RIVER, GameMode.SURVIVAL),
            Triple("Floating Sky Islands", BiomeType.SKY_KINGDOM, GameMode.CREATIVE),
            Triple("Volcanic Jwalamukhi", BiomeType.VOLCANIC_BADLANDS, GameMode.SURVIVAL)
        )
        starters.forEach { (title, biome, mode) ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, biome.accentColor.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onQuickCreateStarter(title, biome, mode) }
                    .testTag("starter_realm_${biome.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val previewBlock = biomePreviewBlock(biome)
                    MiniVoxelBlockIcon(block = previewBlock, size = 40.dp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${biome.displayName} • ${mode.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = biome.accentColor
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Launch $title",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun biomePreviewBlock(biome: BiomeType): BlockType = when (biome) {
    BiomeType.EMERALD_VALLEY -> BlockType.GRASS
    BiomeType.FLOWER_PARADISE -> BlockType.FLOWER_PINK
    BiomeType.DESERT_DUNES -> BlockType.SANDSTONE
    BiomeType.FROST_PEAKS -> BlockType.SNOW
    BiomeType.VOLCANIC_BADLANDS -> BlockType.LAVA
    BiomeType.JUNGLE_RIVER -> BlockType.LEAVES
    BiomeType.OCEAN_ISLANDS -> BlockType.WATER
    BiomeType.SKY_KINGDOM -> BlockType.CHERRY_LEAVES
}

@Composable
private fun WorldCard(
    world: VoxelWorldDoc,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val biome = world.resolvedBiome()
    val mode = world.resolvedGameMode()
    var menuExpanded by remember { mutableStateOf(false) }

    val iconBlock = biomePreviewBlock(biome)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, biome.accentColor.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .testTag("world_card_${world.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = biome.badgeColor,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MiniVoxelBlockIcon(block = iconBlock, size = 38.dp)
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = world.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = biome.badgeColor
                        ) {
                            Text(
                                text = biome.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = biome.accentColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = mode.displayName.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "World options"
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "SEED ${world.seed}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "EDITS ${world.blockDeltas.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Button(
                    onClick = onPlay,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("play_world_button_${world.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_play))
                }
            }
        }
    }
}

@Composable
private fun CreateWorldDialog(
    onDismiss: () -> Unit,
    onCreate: (String, BiomeType, GameMode, Long) -> Unit
) {
    var worldName by remember { mutableStateOf("Emerald Keep") }
    var selectedBiome by remember { mutableStateOf(BiomeType.EMERALD_VALLEY) }
    var selectedMode by remember { mutableStateOf(GameMode.SURVIVAL) }
    var seedText by remember { mutableStateOf(Random.nextInt(10_000, 999_999).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.Terrain, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Forge New World", style = MaterialTheme.typography.headlineMedium)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = worldName,
                    onValueChange = { if (it.length <= 50) worldName = it },
                    label = { Text("World Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("world_name_input")
                )

                Text("Select Biome", style = MaterialTheme.typography.titleSmall)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BiomeType.entries.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { biome ->
                                val selected = biome == selectedBiome
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) biome.badgeColor else MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) biome.accentColor else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { selectedBiome = biome }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = biome.displayName,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (selected) biome.accentColor else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = biome.tagline,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Text("Game Mode", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GameMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode.displayName) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = seedText,
                        onValueChange = { input ->
                            val digits = input.filter { it.isDigit() }.take(9)
                            seedText = digits
                        },
                        label = { Text("World Seed") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            seedText = Random.nextInt(10_000, 999_999).toString()
                        },
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Icon(Icons.Rounded.Casino, contentDescription = "Randomize seed")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = worldName.trim().ifBlank { selectedBiome.displayName }
                    val seed = seedText.toLongOrNull() ?: Random.nextLong(10_000L, 999_999L)
                    onCreate(clean, selectedBiome, selectedMode, seed)
                },
                modifier = Modifier.testTag("confirm_create_world_button")
            ) {
                Text("Forge World")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RenameWorldDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename World") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 50) name = it },
                label = { Text("New World Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
