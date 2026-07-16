package ru.xvmblitz.android.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.xvmblitz.android.data.ApiDefaults
import ru.xvmblitz.android.overlay.OverlayBaseFontSizeSp
import ru.xvmblitz.android.overlay.coerceOverlayScaleX
import ru.xvmblitz.android.overlay.coerceOverlayScaleY

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "xvm_settings")

data class AppSettings(
    val alliesX: Int = 24,
    val alliesY: Int = 120,
    val enemiesX: Int = 900,
    val enemiesY: Int = 120,
    val captureButtonX: Int = 48,
    val captureButtonY: Int = 420,
    val panelScaleX: Float = 1f,
    val panelScaleY: Float = 1f,
    val configMode: Boolean = false,
    val overlayVisible: Boolean = true,
    val floatingButtonEnabled: Boolean = true,
    val apiBaseUrl: String = ApiDefaults.BASE_URL,
    val guideCompleted: Boolean = false,
)

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        val legacyScale =
            (preferences[Keys.FONT_SIZE] ?: OverlayBaseFontSizeSp) / OverlayBaseFontSizeSp
        AppSettings(
            alliesX = preferences[Keys.ALLIES_X] ?: 24,
            alliesY = preferences[Keys.ALLIES_Y] ?: 120,
            enemiesX = preferences[Keys.ENEMIES_X] ?: 900,
            enemiesY = preferences[Keys.ENEMIES_Y] ?: 120,
            captureButtonX = preferences[Keys.CAPTURE_BUTTON_X] ?: 48,
            captureButtonY = preferences[Keys.CAPTURE_BUTTON_Y] ?: 420,
            panelScaleX = coerceOverlayScaleX(preferences[Keys.PANEL_SCALE_X] ?: legacyScale),
            panelScaleY = coerceOverlayScaleY(preferences[Keys.PANEL_SCALE_Y] ?: legacyScale),
            configMode = preferences[Keys.CONFIG_MODE] ?: false,
            overlayVisible = preferences[Keys.OVERLAY_VISIBLE] ?: true,
            floatingButtonEnabled = preferences[Keys.FLOATING_BUTTON_ENABLED] ?: true,
            apiBaseUrl = preferences[Keys.API_BASE_URL] ?: ApiDefaults.BASE_URL,
            guideCompleted = preferences[Keys.GUIDE_COMPLETED] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun updateAlliesPosition(x: Int, y: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.ALLIES_X] = x
            preferences[Keys.ALLIES_Y] = y
        }
    }

    suspend fun updateEnemiesPosition(x: Int, y: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.ENEMIES_X] = x
            preferences[Keys.ENEMIES_Y] = y
        }
    }

    suspend fun updatePanelScale(scaleX: Float, scaleY: Float) {
        val coercedX = coerceOverlayScaleX(scaleX)
        val coercedY = coerceOverlayScaleY(scaleY)
        dataStore.edit { preferences ->
            preferences[Keys.PANEL_SCALE_X] = coercedX
            preferences[Keys.PANEL_SCALE_Y] = coercedY
            preferences[Keys.FONT_SIZE] = OverlayBaseFontSizeSp * coercedY
        }
    }

    suspend fun updateCaptureButtonPosition(x: Int, y: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.CAPTURE_BUTTON_X] = x
            preferences[Keys.CAPTURE_BUTTON_Y] = y
        }
    }

    suspend fun setConfigMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.CONFIG_MODE] = enabled
        }
    }

    suspend fun setOverlayVisible(visible: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.OVERLAY_VISIBLE] = visible
        }
    }

    suspend fun setFloatingButtonEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.FLOATING_BUTTON_ENABLED] = enabled
        }
    }

    suspend fun setApiBaseUrl(baseUrl: String) {
        dataStore.edit { preferences ->
            preferences[Keys.API_BASE_URL] = ApiDefaults.normalizeBaseUrl(baseUrl)
        }
    }

    suspend fun setGuideCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.GUIDE_COMPLETED] = completed
        }
    }

    private object Keys {
        val ALLIES_X = intPreferencesKey("allies_x")
        val ALLIES_Y = intPreferencesKey("allies_y")
        val ENEMIES_X = intPreferencesKey("enemies_x")
        val ENEMIES_Y = intPreferencesKey("enemies_y")
        val CAPTURE_BUTTON_X = intPreferencesKey("capture_button_x")
        val CAPTURE_BUTTON_Y = intPreferencesKey("capture_button_y")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val PANEL_SCALE_X = floatPreferencesKey("panel_scale_x")
        val PANEL_SCALE_Y = floatPreferencesKey("panel_scale_y")
        val CONFIG_MODE = booleanPreferencesKey("config_mode")
        val OVERLAY_VISIBLE = booleanPreferencesKey("overlay_visible")
        val FLOATING_BUTTON_ENABLED = booleanPreferencesKey("floating_button_enabled")
        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val GUIDE_COMPLETED = booleanPreferencesKey("guide_completed")
    }
}
