package com.example.yap_finalprojectpapb

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.example.yap_finalprojectpapb.data.GatewaySettingsStore
import com.example.yap_finalprojectpapb.data.defaultGatewayUrl
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class GuestSettingsTest {
    @Test fun freshInstallationConnectsWithoutProvisioningAndRetainsItsIdentity() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "guest-settings-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(app) {
            override fun getSharedPreferences(ignored: String, mode: Int): SharedPreferences =
                app.getSharedPreferences(name, Context.MODE_PRIVATE)
        }
        try {
            val first = GatewaySettingsStore(context).load()
            assertEquals(defaultGatewayUrl, first.url)
            assertTrue(first.token.matches(Regex("guest_[a-f0-9]{64}")))
            assertEquals(first, GatewaySettingsStore(context).load())
            val stored = context.getSharedPreferences("", 0).getString("token", "")
            assertFalse(stored.orEmpty().contains(first.token))
        } finally {
            app.deleteSharedPreferences(name)
        }
    }
}
