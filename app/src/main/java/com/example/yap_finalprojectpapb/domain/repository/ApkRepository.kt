package com.example.yap_finalprojectpapb.domain.repository

import android.content.Context
import android.net.Uri
import com.example.yap_finalprojectpapb.data.ApkBuild
import com.example.yap_finalprojectpapb.data.GatewaySettings

/**
 * Kontrak abstraksi untuk interaksi pembangunan dan unduhan APK via Gateway AI.
 * Memenuhi Interface Segregation Principle (ISP) dan Dependency Inversion Principle (DIP).
 */
interface ApkRepository {
    fun getSession(): GatewaySettings
    suspend fun submitBuild(settings: GatewaySettings, id: String, prompt: String): ApkBuild
    suspend fun getBuildStatus(settings: GatewaySettings, id: String): ApkBuild
    suspend fun answerClarification(settings: GatewaySettings, id: String, questionId: String, answer: String): ApkBuild
    suspend fun downloadApk(context: Context, settings: GatewaySettings, job: ApkBuild, destination: Uri)
}
