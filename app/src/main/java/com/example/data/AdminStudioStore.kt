package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.data.model.BiomeType
import com.example.engine.gl.Custom3DMeshPreset
import com.example.engine.gl.CustomModelParser
import com.example.engine.gl.Parsed3DModelMesh
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class CustomModelRole(val label: String) {
    SHOP_CHARACTER("Public 3D Character (Shop + Playable)"),
    ENEMY_DUSHMAN("Enemy / Dushman (AI Boss)")
}

data class AdminCustom3DModel(
    val id: String,
    val name: String,
    val role: CustomModelRole,
    val listedInShop: Boolean = true,
    val priceCoins: Int = 120,
    val maxHealth: Int = 120,
    val attackDamage: Int = 2,
    val moveSpeed: Float = 3.0f,
    val scale: Float = 1.05f,
    val primaryColorHex: Long = 0xFF29B6F6,
    val accentColorHex: Long = 0xFFFFD54F,
    val meshPreset: Custom3DMeshPreset = Custom3DMeshPreset.CYBER_KNIGHT,
    val uploadedObjFileName: String? = null,
    val uploadedTriangleCount: Int = 0,
    val hindiGreeting: String = "नमस्ते मित्र! मैं आपका नया 3D साथी हूँ, बोलिए क्या काम करना है?",
    val voicePitch: Float = 1.0f
)

data class LiveAdminMapConfig(
    val title: String = "Admin Live Royal World",
    val biome: BiomeType = BiomeType.EMERALD_VALLEY,
    val announcementBanner: String = "",
    val published: Boolean = false,
    val lastPublishedAtMs: Long = 0L,
    val liveEditsCount: Int = 0
)

data class AdminStudioSnapshot(
    val isAdminClaimed: Boolean = false,
    val adminEmail: String = "",
    val isUnlockedInSession: Boolean = false,
    val customModels: List<AdminCustom3DModel> = emptyList(),
    val liveMap: LiveAdminMapConfig = LiveAdminMapConfig(),
    val equippedPublicModelId: String? = null
)

/**
 * Persistent Admin Studio & Real-Time Live Content Store.
 *
 * Security Safeguards:
 * - Zero hardcoded passwords in source code: Admin claims their own Email & Password on first use.
 * - Password stored ONLY as PBKDF2WithHmacSHA256 (65,536 iterations) + random 16-byte salt in MODE_PRIVATE prefs.
 * - Constant-time verification (`MessageDigest.isEqual`).
 * - Uploaded 3D `.obj` files saved strictly inside app-private `filesDir/custom_models/<uuid>.obj` (no path traversal).
 */
class AdminStudioStore private constructor(private val appContext: Context) {

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val modelsDir: File = File(appContext.filesDir, "custom_models").apply { mkdirs() }
    private val parsedMeshCache = ConcurrentHashMap<String, Parsed3DModelMesh>()

    private val _snapshot = MutableStateFlow(loadInitialSnapshot())
    val snapshot: StateFlow<AdminStudioSnapshot> = _snapshot.asStateFlow()

    private fun loadInitialSnapshot(): AdminStudioSnapshot {
        val email = prefs.getString(KEY_ADMIN_EMAIL, "").orEmpty()
        val hash = prefs.getString(KEY_ADMIN_HASH, "").orEmpty()
        val salt = prefs.getString(KEY_ADMIN_SALT, "").orEmpty()
        val claimed = email.isNotBlank() && hash.isNotBlank() && salt.isNotBlank()
        val models = loadCustomModels()
        val liveMap = loadLiveMap()
        val equipped = prefs.getString(KEY_EQUIPPED_MODEL_ID, null)
        return AdminStudioSnapshot(
            isAdminClaimed = claimed,
            adminEmail = email,
            isUnlockedInSession = false,
            customModels = models,
            liveMap = liveMap,
            equippedPublicModelId = equipped
        )
    }

    /**
     * One-time secure Admin setup: validates email + password strength, hashes with PBKDF2-HMAC-SHA256
     * + random salt, and unlocks the current session.
     */
    fun claimAdminCredentials(emailRaw: String, password: String): Result<Unit> {
        val email = emailRaw.trim().lowercase()
        if (!email.contains("@") || !email.contains(".") || email.length < 5) {
            return Result.failure(IllegalArgumentException("कृपया सही Admin Email दर्ज करें।"))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Admin Password कम से कम 6 अक्षरों का होना चाहिए।"))
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2Hash(password, salt)
        prefs.edit()
            .putString(KEY_ADMIN_EMAIL, email)
            .putString(KEY_ADMIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putString(KEY_ADMIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .apply()

        _snapshot.value = _snapshot.value.copy(
            isAdminClaimed = true,
            adminEmail = email,
            isUnlockedInSession = true
        )
        return Result.success(Unit)
    }

    /**
     * Verifies Admin Email & Password using constant-time PBKDF2 hash comparison.
     */
    fun loginAdmin(emailRaw: String, password: String): Result<Unit> {
        val storedEmail = prefs.getString(KEY_ADMIN_EMAIL, "").orEmpty()
        val storedHashB64 = prefs.getString(KEY_ADMIN_HASH, "").orEmpty()
        val storedSaltB64 = prefs.getString(KEY_ADMIN_SALT, "").orEmpty()
        if (storedEmail.isBlank() || storedHashB64.isBlank() || storedSaltB64.isBlank()) {
            return Result.failure(IllegalStateException("पहले Admin Email और Password सेट करें।"))
        }
        val enteredEmail = emailRaw.trim().lowercase()
        val salt = Base64.decode(storedSaltB64, Base64.NO_WRAP)
        val expectedHash = Base64.decode(storedHashB64, Base64.NO_WRAP)
        val actualHash = pbkdf2Hash(password, salt)

        val emailMatch = MessageDigest.isEqual(
            storedEmail.toByteArray(Charsets.UTF_8),
            enteredEmail.toByteArray(Charsets.UTF_8)
        )
        val passMatch = MessageDigest.isEqual(expectedHash, actualHash)
        if (!emailMatch || !passMatch) {
            return Result.failure(IllegalArgumentException("गलत Admin Email या Password! कृपया फिर से जांचें।"))
        }
        _snapshot.value = _snapshot.value.copy(isUnlockedInSession = true)
        return Result.success(Unit)
    }

    fun lockAdminSession() {
        _snapshot.value = _snapshot.value.copy(isUnlockedInSession = false)
    }

    fun updateAdminPassword(currentPassword: String, newPassword: String): Result<Unit> {
        val currentEmail = _snapshot.value.adminEmail
        val verified = loginAdmin(currentEmail, currentPassword)
        if (verified.isFailure) {
            return Result.failure(IllegalArgumentException("मौजूदा (Current) Password सही नहीं है।"))
        }
        return claimAdminCredentials(currentEmail, newPassword)
    }

    /**
     * Imports a user-selected 3D `.obj` file via Android Storage Access Framework (SAF) URI,
     * validates and parses its 3D triangles, and stores a safe private copy under `custom_models/`.
     */
    fun importObjFileFromUri(uri: Uri): Result<Pair<String, Int>> {
        return try {
            val contentResolver = appContext.contentResolver
            val bytes = contentResolver.openInputStream(uri)?.use { input ->
                val raw = input.readNBytes(1_048_576)
                if (raw.isEmpty()) return Result.failure(IllegalArgumentException("चयनित फ़ाइल खाली है।"))
                raw
            } ?: return Result.failure(IllegalArgumentException("फ़ाइल खोली नहीं जा सकी।"))

            val parsed = CustomModelParser.parseObjStream(bytes.inputStream()).getOrElse {
                return Result.failure(it)
            }

            val safeName = "model_${UUID.randomUUID().toString().replace("-", "").take(12)}.obj"
            val destFile = File(modelsDir, safeName).canonicalFile
            val baseCanonical = modelsDir.canonicalFile
            if (!destFile.path.startsWith(baseCanonical.path + File.separator)) {
                return Result.failure(SecurityException("Invalid storage path."))
            }
            destFile.writeBytes(bytes)
            parsedMeshCache[safeName] = parsed
            Result.success(safeName to parsed.triangleCount)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("3D फ़ाइल अपलोड नहीं हो सकी: ${e.localizedMessage ?: "अज्ञात त्रुटि"}"))
        }
    }

    fun loadParsedMesh(fileName: String?): Parsed3DModelMesh? {
        if (fileName.isNullOrBlank()) return null
        parsedMeshCache[fileName]?.let { return it }
        val safeOnly = File(fileName).name
        val file = File(modelsDir, safeOnly).canonicalFile
        val baseCanonical = modelsDir.canonicalFile
        if (!file.path.startsWith(baseCanonical.path + File.separator) || !file.exists()) {
            return null
        }
        return try {
            FileInputStream(file).use { fis ->
                CustomModelParser.parseObjStream(fis).getOrNull()?.also {
                    parsedMeshCache[safeOnly] = it
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun saveCustomModel(model: AdminCustom3DModel) {
        val current = _snapshot.value.customModels.toMutableList()
        val idx = current.indexOfFirst { it.id == model.id }
        if (idx >= 0) current[idx] = model else current.add(0, model)
        persistCustomModels(current)
        _snapshot.value = _snapshot.value.copy(customModels = current)
    }

    fun deleteCustomModel(modelId: String) {
        val removed = _snapshot.value.customModels.firstOrNull { it.id == modelId }
        val next = _snapshot.value.customModels.filterNot { it.id == modelId }
        persistCustomModels(next)
        removed?.uploadedObjFileName?.let { fileName ->
            parsedMeshCache.remove(fileName)
            val safeFile = File(modelsDir, File(fileName).name)
            runCatching { if (safeFile.exists()) safeFile.delete() }
        }
        val nextEquipped = if (_snapshot.value.equippedPublicModelId == modelId) null else _snapshot.value.equippedPublicModelId
        if (nextEquipped != _snapshot.value.equippedPublicModelId) {
            prefs.edit().remove(KEY_EQUIPPED_MODEL_ID).apply()
        }
        _snapshot.value = _snapshot.value.copy(
            customModels = next,
            equippedPublicModelId = nextEquipped
        )
    }

    fun equipPublicCharacterModel(modelId: String?) {
        if (modelId == null) {
            prefs.edit().remove(KEY_EQUIPPED_MODEL_ID).apply()
        } else {
            prefs.edit().putString(KEY_EQUIPPED_MODEL_ID, modelId).apply()
        }
        _snapshot.value = _snapshot.value.copy(equippedPublicModelId = modelId)
    }

    fun publishLiveMap(
        title: String,
        biome: BiomeType,
        announcementBanner: String
    ): LiveAdminMapConfig {
        val prev = _snapshot.value.liveMap
        val updated = LiveAdminMapConfig(
            title = title.trim().ifBlank { "Admin Live World" },
            biome = biome,
            announcementBanner = announcementBanner.trim(),
            published = true,
            lastPublishedAtMs = System.currentTimeMillis(),
            liveEditsCount = prev.liveEditsCount + 1
        )
        persistLiveMap(updated)
        _snapshot.value = _snapshot.value.copy(liveMap = updated)
        return updated
    }

    private fun pbkdf2Hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 65_536, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun loadCustomModels(): List<AdminCustom3DModel> {
        val raw = prefs.getString(KEY_CUSTOM_MODELS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = ArrayList<AdminCustom3DModel>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val role = runCatching { CustomModelRole.valueOf(obj.optString("role")) }
                    .getOrDefault(CustomModelRole.SHOP_CHARACTER)
                val preset = runCatching { Custom3DMeshPreset.valueOf(obj.optString("meshPreset")) }
                    .getOrDefault(Custom3DMeshPreset.CYBER_KNIGHT)
                result.add(
                    AdminCustom3DModel(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "Custom 3D Hero"),
                        role = role,
                        listedInShop = obj.optBoolean("listedInShop", true),
                        priceCoins = obj.optInt("priceCoins", 120),
                        maxHealth = obj.optInt("maxHealth", 120),
                        attackDamage = obj.optInt("attackDamage", 2),
                        moveSpeed = obj.optDouble("moveSpeed", 3.0).toFloat(),
                        scale = obj.optDouble("scale", 1.05).toFloat(),
                        primaryColorHex = obj.optLong("primaryColorHex", 0xFF29B6F6),
                        accentColorHex = obj.optLong("accentColorHex", 0xFFFFD54F),
                        meshPreset = preset,
                        uploadedObjFileName = obj.optString("uploadedObjFileName").takeIf { it.isNotBlank() },
                        uploadedTriangleCount = obj.optInt("uploadedTriangleCount", 0),
                        hindiGreeting = obj.optString("hindiGreeting", "नमस्ते मित्र! मैं तैयार हूँ।"),
                        voicePitch = obj.optDouble("voicePitch", 1.0).toFloat()
                    )
                )
            }
            result
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun persistCustomModels(list: List<AdminCustom3DModel>) {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(
                JSONObject().apply {
                    put("id", m.id)
                    put("name", m.name)
                    put("role", m.role.name)
                    put("listedInShop", m.listedInShop)
                    put("priceCoins", m.priceCoins)
                    put("maxHealth", m.maxHealth)
                    put("attackDamage", m.attackDamage)
                    put("moveSpeed", m.moveSpeed.toDouble())
                    put("scale", m.scale.toDouble())
                    put("primaryColorHex", m.primaryColorHex)
                    put("accentColorHex", m.accentColorHex)
                    put("meshPreset", m.meshPreset.name)
                    put("uploadedObjFileName", m.uploadedObjFileName ?: "")
                    put("uploadedTriangleCount", m.uploadedTriangleCount)
                    put("hindiGreeting", m.hindiGreeting)
                    put("voicePitch", m.voicePitch.toDouble())
                }
            )
        }
        prefs.edit().putString(KEY_CUSTOM_MODELS, arr.toString()).apply()
    }

    private fun loadLiveMap(): LiveAdminMapConfig {
        val raw = prefs.getString(KEY_LIVE_MAP, null) ?: return LiveAdminMapConfig()
        return try {
            val obj = JSONObject(raw)
            LiveAdminMapConfig(
                title = obj.optString("title", "Admin Live Royal World"),
                biome = BiomeType.safeFromName(obj.optString("biome", BiomeType.EMERALD_VALLEY.name)),
                announcementBanner = obj.optString("announcementBanner", ""),
                published = obj.optBoolean("published", false),
                lastPublishedAtMs = obj.optLong("lastPublishedAtMs", 0L),
                liveEditsCount = obj.optInt("liveEditsCount", 0)
            )
        } catch (_: Throwable) {
            LiveAdminMapConfig()
        }
    }

    private fun persistLiveMap(cfg: LiveAdminMapConfig) {
        val obj = JSONObject().apply {
            put("title", cfg.title)
            put("biome", cfg.biome.name)
            put("announcementBanner", cfg.announcementBanner)
            put("published", cfg.published)
            put("lastPublishedAtMs", cfg.lastPublishedAtMs)
            put("liveEditsCount", cfg.liveEditsCount)
        }
        prefs.edit().putString(KEY_LIVE_MAP, obj.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "blockforge_admin_studio_v1"
        private const val KEY_ADMIN_EMAIL = "admin_email"
        private const val KEY_ADMIN_HASH = "admin_pw_hash"
        private const val KEY_ADMIN_SALT = "admin_pw_salt"
        private const val KEY_CUSTOM_MODELS = "custom_models_json"
        private const val KEY_LIVE_MAP = "live_map_json"
        private const val KEY_EQUIPPED_MODEL_ID = "equipped_public_model_id"

        @Volatile
        private var instance: AdminStudioStore? = null

        fun getInstance(context: Context): AdminStudioStore {
            return instance ?: synchronized(this) {
                instance ?: AdminStudioStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
