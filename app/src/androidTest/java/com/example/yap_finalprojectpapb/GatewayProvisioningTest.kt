package com.example.yap_finalprojectpapb

import androidx.test.platform.app.InstrumentationRegistry
import com.example.yap_finalprojectpapb.data.GatewaySettings
import com.example.yap_finalprojectpapb.data.GatewaySettingsStore
import com.example.yap_finalprojectpapb.data.defaultGatewayUrl
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class GatewayProvisioningTest {
    @Test fun provisionConnectedDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tokenFile = File(context.filesDir, "gateway-provision-token")
        assumeTrue(tokenFile.isFile)
        val token = try {
            tokenFile.readText().trim()
        } finally {
            tokenFile.delete()
        }
        check(token.length >= 32 && !token.startsWith("LLM|"))
        val store = GatewaySettingsStore(context)
        val settings = GatewaySettings(defaultGatewayUrl, token)
        store.save(settings)
        assertTrue("Gateway settings could not be read back", store.load() == settings)
    }
}
