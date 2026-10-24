package com.local.interviewassistant.data.provisioning

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "hf_settings")

/**
 * Manages secure storage of the Hugging Face API token.
 */
class HuggingFaceApiClient(private val context: Context) {
    
    companion object {
        private val HF_TOKEN_KEY = stringPreferencesKey("hf_token")
    }

    val tokenFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[HF_TOKEN_KEY]
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[HF_TOKEN_KEY] = token
        }
    }

    suspend fun clearToken() {
        context.dataStore.edit { preferences ->
            preferences.remove(HF_TOKEN_KEY)
        }
    }
}
