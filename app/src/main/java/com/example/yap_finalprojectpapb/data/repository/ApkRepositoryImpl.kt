package com.example.yap_finalprojectpapb.data.repository

import android.content.Context
import android.net.Uri
import com.example.yap_finalprojectpapb.data.ApkBuild
import com.example.yap_finalprojectpapb.data.ApkBuildGateway
import com.example.yap_finalprojectpapb.data.GatewaySettings
import com.example.yap_finalprojectpapb.data.GatewaySettingsStore
import com.example.yap_finalprojectpapb.domain.repository.ApkRepository

class ApkRepositoryImpl(
    private val gateway: ApkBuildGateway
) : ApkRepository {
    constructor(context: Context) : this(ApkBuildGateway(GatewaySettingsStore(context)))

    override fun getSession(): GatewaySettings = gateway.session()

    override suspend fun submitBuild(settings: GatewaySettings, id: String, prompt: String): ApkBuild =
        gateway.submit(settings, id, prompt)

    override suspend fun getBuildStatus(settings: GatewaySettings, id: String): ApkBuild =
        gateway.status(settings, id)

    override suspend fun answerClarification(
        settings: GatewaySettings,
        id: String,
        questionId: String,
        answer: String
    ): ApkBuild = gateway.answer(settings, id, questionId, answer)

    override suspend fun downloadApk(
        context: Context,
        settings: GatewaySettings,
        job: ApkBuild,
        destination: Uri
    ) {
        gateway.download(context, settings, job, destination)
    }
}
