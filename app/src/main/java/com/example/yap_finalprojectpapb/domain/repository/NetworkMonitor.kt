package com.example.yap_finalprojectpapb.domain.repository

import kotlinx.coroutines.flow.Flow

interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}
