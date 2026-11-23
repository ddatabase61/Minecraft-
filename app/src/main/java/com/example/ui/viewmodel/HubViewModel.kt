package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.WorldDataSource
import com.example.data.model.BiomeType
import com.example.data.model.BuilderProfile
import com.example.data.model.GameMode
import com.example.data.model.VoxelWorldDoc
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class HubViewModel(
    private val repository: WorldDataSource,
    private val currentUserId: String,
    defaultDisplayName: String
) : ViewModel() {

    val isCloudBacked: Boolean = repository.isCloudBacked

    val worldsState: StateFlow<UiState<List<VoxelWorldDoc>>> = repository.observeWorlds(currentUserId)
        .map<List<VoxelWorldDoc>, UiState<List<VoxelWorldDoc>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing worlds", rootError)
            emit(UiState.Error(rootError.message ?: "Unable to sync worlds."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val profileState: StateFlow<UiState<BuilderProfile?>> = repository.observeProfile(currentUserId)
        .map<BuilderProfile?, UiState<BuilderProfile?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing builder profile", rootError)
            emit(UiState.Error(rootError.message ?: "Unable to load builder profile."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _busyMessage = MutableStateFlow<String?>(null)
    val busyMessage: StateFlow<String?> = _busyMessage.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureProfileExists(defaultDisplayName)
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun createWorld(
        name: String,
        biome: BiomeType,
        gameMode: GameMode,
        seed: Long,
        onCreated: (String) -> Unit
    ) {
        if (_busyMessage.value != null) return
        viewModelScope.launch {
            _busyMessage.value = "Forging ${biome.displayName}..."
            val result = try {
                repository.createWorld(name, biome, gameMode, seed)
            } finally {
                _busyMessage.value = null
            }
            result.fold(
                onSuccess = { world ->
                    onCreated(world.id)
                },
                onFailure = { error ->
                    _feedbackMessage.value = error.localizedMessage ?: "Failed to create world."
                }
            )
        }
    }

    fun renameWorld(worldId: String, newName: String) {
        viewModelScope.launch {
            val result = repository.renameWorld(worldId, newName)
            result.fold(
                onSuccess = { _feedbackMessage.value = "World renamed." },
                onFailure = { _feedbackMessage.value = it.localizedMessage ?: "Rename failed." }
            )
        }
    }

    fun deleteWorld(worldId: String) {
        viewModelScope.launch {
            val result = repository.deleteWorld(worldId)
            result.fold(
                onSuccess = { _feedbackMessage.value = "World deleted." },
                onFailure = { _feedbackMessage.value = it.localizedMessage ?: "Delete failed." }
            )
        }
    }

    fun updateDisplayName(newName: String) {
        viewModelScope.launch {
            val result = repository.updateDisplayName(newName)
            result.fold(
                onSuccess = { _feedbackMessage.value = "Builder name updated!" },
                onFailure = { _feedbackMessage.value = it.localizedMessage ?: "Failed to update name." }
            )
        }
    }

    private companion object {
        const val TAG = "HubVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
