package com.example.yap_finalprojectpapb

import com.example.yap_finalprojectpapb.data.gatewayUrlError
import org.junit.Assert.*
import org.junit.Test

class GatewayUrlTest {
    @Test fun acceptsHttpsOrigins() {
        assertNull(gatewayUrlError("https://yap.example"))
        assertNull(gatewayUrlError("https://yap.example/"))
    }

    @Test fun rejectsInsecureRemoteAndNonWebSchemes() {
        listOf("http://yap.example", "file:///secrets", "javascript:alert(1)", "", "not a URL").forEach {
            assertNotNull(it, gatewayUrlError(it))
        }
    }

    @Test fun rejectsCredentialsPathsAndQueries() {
        listOf("https://token@yap.example", "https://yap.example/api/generate", "https://yap.example?key=secret", "https://yap.example#token").forEach {
            assertNotNull(it, gatewayUrlError(it))
        }
    }

    @Test fun allowsLoopbackOnlyForDebugBuilds() {
        listOf("http://10.0.2.2:8787", "http://127.0.0.1:8787", "http://localhost:8787").forEach {
            assertEquals(!BuildConfig.DEBUG, gatewayUrlError(it) != null)
        }
    }
}
