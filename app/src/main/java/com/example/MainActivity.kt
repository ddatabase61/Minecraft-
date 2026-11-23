package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.GuestLocalDataSource
import com.example.data.WorldDataSource
import com.example.data.firebase.WorldRepository
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.configureAppCheck
import com.example.data.firebase.isFirebaseConfigured
import com.example.data.firebase.resolveFirestoreDatabaseId
import com.example.data.firebase.signOutUser
import com.example.data.model.BlockType
import com.example.engine.MiniVoxelBlockIcon
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.GameWorldScreen
import com.example.ui.screens.WorldsHubScreen
import com.example.ui.theme.BlockForgeTheme
import com.example.ui.viewmodel.GameViewModel
import com.example.ui.viewmodel.HubViewModel
import com.example.update.AppUpdateHost
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

const val ROUTE_HUB = "hub"
const val ROUTE_WORLD = "world/{worldId}"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            BlockForgeTheme(darkTheme = true) {
                BlockForgeApp()
            }
        }
    }
}

@Composable
fun BlockForgeApp() {
    val context = LocalContext.current
    val firebaseAvailable = remember(context) { isFirebaseConfigured(context) }
    val authFlow = remember(firebaseAvailable) {
        if (firebaseAvailable) Firebase.auth.authStateFlow() else flowOf(null)
    }
    val initialUser = remember(firebaseAvailable) {
        if (firebaseAvailable) Firebase.auth.currentUser else null
    }
    val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = initialUser)
    var isGuestMode by rememberSaveable { mutableStateOf(!firebaseAvailable) }
    var showOpeningAnimation by rememberSaveable { mutableStateOf(true) }

    val user = currentUser

    Box(modifier = Modifier.fillMaxSize()) {
        if (user == null && !isGuestMode && firebaseAvailable) {
            AuthScreen(
                onAuthSuccess = { isGuestMode = false },
                onContinueAsGuest = { isGuestMode = true }
            )
        } else {
            val isGuestSession = (user == null || !firebaseAvailable)
            val sessionId = user?.uid ?: GuestLocalDataSource.GUEST_UID
            val displayName = user?.displayName?.takeIf { it.isNotBlank() }
                ?: if (isGuestSession) "Guest Builder" else "Voxel Builder"

            key(sessionId) {
                SessionNavHost(
                    currentUserId = sessionId,
                    defaultDisplayName = displayName,
                    isGuestSession = isGuestSession,
                    onExitGuest = {
                        if (firebaseAvailable) {
                            isGuestMode = false
                        }
                    }
                )
            }
        }

        // Cinematic Opening Animation Overlay (tap anywhere to skip)
        AnimatedVisibility(
            visible = showOpeningAnimation,
            exit = fadeOut(animationSpec = tween(durationMillis = 420))
        ) {
            OpeningAnimationOverlay(
                onFinished = { showOpeningAnimation = false }
            )
        }

        // Firebase Firestore Automatic APK Update Checker & Dialog
        AppUpdateHost()
    }
}

@Composable
private fun OpeningAnimationOverlay(
    onFinished: () -> Unit
) {
    val scale = remember { Animatable(0.62f) }
    val alpha = remember { Animatable(0f) }
    val sunRise = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(420))
        scale.animateTo(1.06f, animationSpec = tween(520, easing = FastOutSlowInEasing))
        sunRise.animateTo(1f, animationSpec = tween(650, easing = FastOutSlowInEasing))
        scale.animateTo(1f, animationSpec = tween(220))
        delay(350L)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF071510),
                        Color(0xFF0E291F),
                        Color(0xFF091310)
                    )
                )
            )
            .clickable(onClick = onFinished)
            .testTag("opening_animation_overlay"),
        contentAlignment = Alignment.Center
    ) {
        // Rising golden sun halo & floating voxel sparkles
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sunY = size.height * (0.58f - sunRise.value * 0.16f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x66FFD166),
                        Color(0x3339D98A),
                        Color.Transparent
                    ),
                    center = Offset(size.width / 2f, sunY),
                    radius = size.minDimension * (0.35f + sunRise.value * 0.22f)
                ),
                radius = size.minDimension * 0.58f,
                center = Offset(size.width / 2f, sunY)
            )
        }

        Column(
            modifier = Modifier
                .scale(scale.value)
                .alpha(alpha.value)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xCC10241C),
                border = BorderStroke(2.dp, Color(0xFF39D98A)),
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MiniVoxelBlockIcon(block = BlockType.DIAMOND_ORE, size = 56.dp)
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "BLOCKFORGE",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEFFFF6)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "3D Voxel World • Oceans • Heroes • AI Combat",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF53FF9E)
            )
        }
    }
}

@Composable
private fun SessionNavHost(
    currentUserId: String,
    defaultDisplayName: String,
    isGuestSession: Boolean,
    onExitGuest: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val hubViewModel: HubViewModel = viewModel(
        key = "hub_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = if (isGuestSession) null else resolveFirestoreDatabaseId(app)
                val dataSource: WorldDataSource = if (isGuestSession || databaseId == null) {
                    GuestLocalDataSource.getInstance(app)
                } else {
                    WorldRepository(FirebaseFirestore.getInstance(databaseId))
                }
                HubViewModel(
                    repository = dataSource,
                    currentUserId = currentUserId,
                    defaultDisplayName = defaultDisplayName
                )
            }
        }
    )

    NavHost(
        navController = navController,
        startDestination = ROUTE_HUB
    ) {
        composable(ROUTE_HUB) {
            WorldsHubScreen(
                viewModel = hubViewModel,
                onOpenWorld = { worldId ->
                    navController.navigate("world/$worldId")
                },
                onSignOut = {
                    if (isGuestSession) {
                        onExitGuest()
                    } else {
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope
                        )
                        onExitGuest()
                    }
                }
            )
        }

        composable(
            route = ROUTE_WORLD,
            arguments = listOf(navArgument("worldId") { type = NavType.StringType })
        ) { backStackEntry ->
            val worldId = backStackEntry.arguments?.getString("worldId").orEmpty()
            val gameViewModel: GameViewModel = viewModel(
                key = "world_${currentUserId}_$worldId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = if (isGuestSession) null else resolveFirestoreDatabaseId(app)
                        val dataSource: WorldDataSource = if (isGuestSession || databaseId == null) {
                            GuestLocalDataSource.getInstance(app)
                        } else {
                            WorldRepository(FirebaseFirestore.getInstance(databaseId))
                        }
                        GameViewModel(
                            repository = dataSource,
                            worldId = worldId,
                            appContext = app.applicationContext
                        )
                    }
                }
            )

            GameWorldScreen(
                viewModel = gameViewModel,
                onExitToHub = {
                    navController.popBackStack()
                }
            )
        }
    }
}
