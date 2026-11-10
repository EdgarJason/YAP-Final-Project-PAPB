package com.example.yap_finalprojectpapb.data.repository

import android.content.Context
import com.example.yap_finalprojectpapb.data.AppUpdate
import com.example.yap_finalprojectpapb.data.AppUpdates
import com.example.yap_finalprojectpapb.domain.repository.AppUpdateRepository
import java.io.File

class AppUpdateRepositoryImpl(
    private val appUpdates: AppUpdates
) : AppUpdateRepository {
    constructor(context: Context) : this(AppUpdates(context))

    override suspend fun checkForUpdate(): AppUpdate? = appUpdates.check()

    override suspend fun downloadUpdate(update: AppUpdate, onProgress: (Int) -> Unit): File =
        appUpdates.download(update, onProgress)
}
