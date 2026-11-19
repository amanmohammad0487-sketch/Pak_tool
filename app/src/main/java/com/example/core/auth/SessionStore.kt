package com.example.core.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.GlassThemePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.majlishDataStore by preferencesDataStore(name = "majlish_prefs")

data class StoredSession(
    val lastKey: String = "",
    val themePreset: GlassThemePreset = GlassThemePreset.FROST_LIGHT,
    val customLuaXorHex: String = ""
)

class SessionStore(private val context: Context) {

    private object Keys {
        val THEME_ID = stringPreferencesKey("theme_id")
        val LAST_KEY = stringPreferencesKey("last_license_key")
        val LUA_XOR_HEX = stringPreferencesKey("lua_xor_hex")
    }

    val sessionFlow: Flow<StoredSession> = context.majlishDataStore.data.map { prefs ->
        StoredSession(
            lastKey = prefs[Keys.LAST_KEY].orEmpty(),
            themePreset = GlassThemePreset.fromId(prefs[Keys.THEME_ID]),
            customLuaXorHex = prefs[Keys.LUA_XOR_HEX].orEmpty()
        )
    }

    suspend fun saveTheme(preset: GlassThemePreset) {
        context.majlishDataStore.edit { it[Keys.THEME_ID] = preset.id }
    }

    suspend fun saveLastKey(key: String) {
        context.majlishDataStore.edit { it[Keys.LAST_KEY] = key }
    }

    suspend fun clearLastKey() {
        context.majlishDataStore.edit { it.remove(Keys.LAST_KEY) }
    }

    suspend fun saveCustomLuaXorHex(hex: String) {
        context.majlishDataStore.edit { it[Keys.LUA_XOR_HEX] = hex.trim() }
    }
}
