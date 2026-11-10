package com.example.yap_finalprojectpapb.domain.repository

import com.example.yap_finalprojectpapb.data.AppUpdate
import java.io.File

/**
 * Kontrak abstraksi pengecekan dan pengunduhan pembaruan in-app.
 */
interface AppUpdateRepository {
    suspend fun checkForUpdate(): AppUpdate?
    suspend fun downloadUpdate(update: AppUpdate, onProgress: (Int) -> Unit): File
}
