package com.example.yap_finalprojectpapb.domain.repository

import com.example.yap_finalprojectpapb.data.GatewaySettings
import com.example.yap_finalprojectpapb.data.SavedApkBuild

/**
 * Kontrak abstraksi penyimpanan preferensi server gateway dan status build aktif.
 */
interface SettingsRepository {
    fun loadSettings(): GatewaySettings
    fun saveSettings(settings: GatewaySettings)
    fun loadActiveBuild(): SavedApkBuild?
    fun saveActiveBuild(build: SavedApkBuild)
    fun clearActiveBuild()
}
