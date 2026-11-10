package com.example.yap_finalprojectpapb.data.repository

import android.content.Context
import com.example.yap_finalprojectpapb.data.GatewaySettings
import com.example.yap_finalprojectpapb.data.GatewaySettingsStore
import com.example.yap_finalprojectpapb.data.SavedApkBuild
import com.example.yap_finalprojectpapb.domain.repository.SettingsRepository

class SettingsRepositoryImpl(
    private val store: GatewaySettingsStore
) : SettingsRepository {
    constructor(context: Context) : this(GatewaySettingsStore(context))

    override fun loadSettings(): GatewaySettings = store.load()
    override fun saveSettings(settings: GatewaySettings) = store.save(settings)
    override fun loadActiveBuild(): SavedApkBuild? = store.loadApkBuild()
    override fun saveActiveBuild(build: SavedApkBuild) = store.saveApkBuild(build)
    override fun clearActiveBuild() = store.clearApkBuild()
}
