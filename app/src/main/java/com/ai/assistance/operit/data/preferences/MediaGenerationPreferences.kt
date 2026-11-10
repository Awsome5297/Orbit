package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.mediagen.provider.MediaGenerationProviderSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.mediaGenerationDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "media_generation_settings")

@Serializable
data class MediaGenerationSettings(
    val providers: List<MediaGenerationProviderSetting> = emptyList(),
    val defaultModelId: String? = null,
)

class MediaGenerationPreferences private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: MediaGenerationPreferences? = null

        fun getInstance(context: Context): MediaGenerationPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MediaGenerationPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }

        private val SETTINGS_JSON = stringPreferencesKey("media_generation_settings_json")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val settingsFlow: Flow<MediaGenerationSettings> = context.mediaGenerationDataStore.data
        .map { preferences ->
            preferences[SETTINGS_JSON]
                ?.let { json.decodeFromString<MediaGenerationSettings>(it) }
                ?: MediaGenerationSettings()
        }
        .distinctUntilChanged()

    suspend fun update(transform: (MediaGenerationSettings) -> MediaGenerationSettings) {
        context.mediaGenerationDataStore.edit { preferences ->
            val current = preferences[SETTINGS_JSON]
                ?.let { json.decodeFromString<MediaGenerationSettings>(it) }
                ?: MediaGenerationSettings()
            preferences[SETTINGS_JSON] = json.encodeToString(transform(current))
        }
    }

    suspend fun upsertProvider(provider: MediaGenerationProviderSetting) {
        update { settings ->
            val providers = settings.providers.toMutableList()
            val index = providers.indexOfFirst { it.id == provider.id }
            if (index >= 0) providers[index] = provider else providers.add(provider)
            settings.copy(providers = providers)
        }
    }

    suspend fun deleteProvider(providerId: String) {
        update { settings ->
            settings.copy(providers = settings.providers.filterNot { it.id == providerId })
        }
    }

    suspend fun setDefaultModel(modelId: String?) {
        update { it.copy(defaultModelId = modelId) }
    }
}
