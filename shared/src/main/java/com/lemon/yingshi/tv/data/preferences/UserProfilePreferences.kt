package com.lemon.yingshi.tv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userProfileDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_profile"
)

@Singleton
class UserProfilePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.userProfileDataStore

    val displayName: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_DISPLAY_NAME].orEmpty()
    }

    val avatarRevision: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_AVATAR_REVISION] ?: 0L
    }

    suspend fun setDisplayName(name: String) {
        dataStore.edit { prefs ->
            prefs[KEY_DISPLAY_NAME] = name.trim()
        }
    }

    suspend fun bumpAvatarRevision() {
        dataStore.edit { prefs ->
            prefs[KEY_AVATAR_REVISION] = (prefs[KEY_AVATAR_REVISION] ?: 0L) + 1L
        }
    }

    companion object {
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_AVATAR_REVISION = longPreferencesKey("avatar_revision")
    }
}
