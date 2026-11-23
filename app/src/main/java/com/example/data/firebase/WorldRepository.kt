package com.example.data.firebase

import android.content.Context
import com.example.data.WorldDataSource
import com.example.data.model.BiomeType
import com.example.data.model.BuilderProfile
import com.example.data.model.GameMode
import com.example.data.model.VoxelWorldDoc
import com.example.data.model.defaultHotbar
import com.example.data.model.defaultStarterInventory
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.max

/**
 * Safely resolves `firestore_database_id` at runtime so builds without
 * `firebase_applet_config.xml` (e.g. public GitHub / offline builds) compile cleanly.
 */
fun resolveFirestoreDatabaseId(context: Context): String? {
    val resId = context.resources.getIdentifier(
        "firestore_database_id",
        "string",
        context.packageName
    )
    return if (resId != 0) context.getString(resId).takeIf { it.isNotBlank() } else null
}

class WorldRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : WorldDataSource {
    override val isCloudBacked: Boolean = true

    constructor(context: Context) : this(
        resolveFirestoreDatabaseId(context.applicationContext)?.let { dbId ->
            FirebaseFirestore.getInstance(dbId)
        } ?: FirebaseFirestore.getInstance()
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    override fun observeProfile(userId: String): Flow<BuilderProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Authenticated user mismatch." }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            BuilderProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    override fun observeWorlds(userId: String): Flow<List<VoxelWorldDoc>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Authenticated user mismatch." }
        val path = "users/$uid/worlds"
        emitAll(
            db.collection("users").document(uid).collection("worlds")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VoxelWorldDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override suspend fun ensureProfileExists(defaultDisplayName: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid)
            docPath = ref.path
            val snapshot = ref.get().await()
            if (!snapshot.exists()) {
                val safeName = defaultDisplayName.trim().take(60).ifBlank { "Voxel Builder" }
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "totalBlocksMined" to 0L,
                    "totalBlocksPlaced" to 0L,
                    "totalItemsCrafted" to 0L,
                    "builderLevel" to 1L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun updateDisplayName(newName: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val cleanName = newName.trim().take(60)
            require(cleanName.isNotEmpty()) { "Display name cannot be blank." }
            val ref = db.collection("users").document(uid)
            docPath = ref.path
            ref.update(
                mapOf(
                    "displayName" to cleanName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun incrementProfileStats(
        minedDelta: Long,
        placedDelta: Long,
        craftedDelta: Long
    ): Result<Unit> {
        if (minedDelta <= 0L && placedDelta <= 0L && craftedDelta <= 0L) {
            return Result.success(Unit)
        }
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid)
            docPath = ref.path
            val snap = ref.get().await()
            val prevMined = snap.getLong("totalBlocksMined") ?: 0L
            val prevPlaced = snap.getLong("totalBlocksPlaced") ?: 0L
            val prevCrafted = snap.getLong("totalItemsCrafted") ?: 0L

            val newMined = (prevMined + max(0L, minedDelta)).coerceAtMost(10_000_000L)
            val newPlaced = (prevPlaced + max(0L, placedDelta)).coerceAtMost(10_000_000L)
            val newCrafted = (prevCrafted + max(0L, craftedDelta)).coerceAtMost(10_000_000L)
            val xp = newMined * 2L + newPlaced * 3L + newCrafted * 10L
            val newLevel = (1L + xp / 60L).coerceIn(1L, 999L)

            if (snap.exists()) {
                ref.update(
                    mapOf(
                        "totalBlocksMined" to newMined,
                        "totalBlocksPlaced" to newPlaced,
                        "totalItemsCrafted" to newCrafted,
                        "builderLevel" to newLevel,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val fallbackName = (auth.currentUser?.displayName ?: "Voxel Builder").trim().take(60).ifBlank { "Voxel Builder" }
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to fallbackName,
                        "totalBlocksMined" to newMined,
                        "totalBlocksPlaced" to newPlaced,
                        "totalItemsCrafted" to newCrafted,
                        "builderLevel" to newLevel,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun createWorld(
        name: String,
        biome: BiomeType,
        gameMode: GameMode,
        seed: Long
    ): Result<VoxelWorldDoc> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/worlds"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim().take(50)
            require(cleanName.isNotEmpty()) { "World name cannot be empty." }
            val safeSeed = seed.coerceIn(0L, 999_999_999L)

            val docRef = db.collection("users").document(uid).collection("worlds").document()
            docPath = docRef.path

            val starterInv = defaultStarterInventory(gameMode)
            val starterHotbar = defaultHotbar()
            val payload = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "biome" to biome.name,
                "gameMode" to gameMode.name,
                "seed" to safeSeed,
                "timeOfDay" to 0.25,
                "pickaxeTier" to 1L,
                "blocksMined" to 0L,
                "blocksPlaced" to 0L,
                "playerX" to 8.5,
                "playerY" to 18.0,
                "playerZ" to 8.5,
                "blockDeltas" to emptyMap<String, Long>(),
                "inventory" to starterInv,
                "hotbar" to starterHotbar,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(
                VoxelWorldDoc(
                    id = docRef.id,
                    userId = uid,
                    name = cleanName,
                    biome = biome.name,
                    gameMode = gameMode.name,
                    seed = safeSeed,
                    timeOfDay = 0.25,
                    pickaxeTier = 1L,
                    blocksMined = 0L,
                    blocksPlaced = 0L,
                    playerX = 8.5,
                    playerY = 18.0,
                    playerZ = 8.5,
                    blockDeltas = emptyMap(),
                    inventory = starterInv,
                    hotbar = starterHotbar
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun getWorld(worldId: String): Result<VoxelWorldDoc?> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/worlds/$worldId"
        return try {
            val uid = requireUserId()
            require(worldId.isNotBlank()) { "World ID must not be blank." }
            val ref = db.collection("users").document(uid).collection("worlds").document(worldId)
            docPath = ref.path
            val snap = ref.get().await()
            val world = if (snap.exists()) {
                snap.toObject(
                    VoxelWorldDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else {
                null
            }
            Result.success(world)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    override suspend fun saveWorldState(
        worldId: String,
        gameMode: GameMode,
        timeOfDay: Double,
        pickaxeTier: Long,
        blocksMined: Long,
        blocksPlaced: Long,
        playerX: Double,
        playerY: Double,
        playerZ: Double,
        blockDeltas: Map<String, Long>,
        inventory: Map<String, Long>,
        hotbar: List<String>
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/worlds/$worldId"
        return try {
            val uid = requireUserId()
            require(worldId.isNotBlank()) { "World ID must not be blank." }
            val ref = db.collection("users").document(uid).collection("worlds").document(worldId)
            docPath = ref.path

            val boundedDeltas = if (blockDeltas.size > 4000) {
                blockDeltas.entries.take(4000).associate { it.key to it.value }
            } else {
                blockDeltas
            }
            val boundedInventory = inventory.entries.take(64).associate { it.key to it.value.coerceAtLeast(0L) }
            val boundedHotbar = hotbar.take(9)

            ref.update(
                mapOf(
                    "gameMode" to gameMode.name,
                    "timeOfDay" to timeOfDay.coerceIn(0.0, 1.0),
                    "pickaxeTier" to pickaxeTier.coerceIn(1L, 3L),
                    "blocksMined" to blocksMined.coerceIn(0L, 10_000_000L),
                    "blocksPlaced" to blocksPlaced.coerceIn(0L, 10_000_000L),
                    "playerX" to playerX.coerceIn(-10_000.0, 10_000.0),
                    "playerY" to playerY.coerceIn(-10_000.0, 10_000.0),
                    "playerZ" to playerZ.coerceIn(-10_000.0, 10_000.0),
                    "blockDeltas" to boundedDeltas,
                    "inventory" to boundedInventory,
                    "hotbar" to boundedHotbar,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun renameWorld(worldId: String, newName: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/worlds/$worldId"
        return try {
            val uid = requireUserId()
            require(worldId.isNotBlank()) { "World ID must not be blank." }
            val cleanName = newName.trim().take(50)
            require(cleanName.isNotEmpty()) { "World name cannot be empty." }
            val ref = db.collection("users").document(uid).collection("worlds").document(worldId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "name" to cleanName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun deleteWorld(worldId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/worlds/$worldId"
        return try {
            val uid = requireUserId()
            require(worldId.isNotBlank()) { "World ID must not be blank." }
            val ref = db.collection("users").document(uid).collection("worlds").document(worldId)
            docPath = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
