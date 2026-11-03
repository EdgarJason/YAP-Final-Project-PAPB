package com.example.yap_finalprojectpapb

import androidx.test.platform.app.InstrumentationRegistry
import com.example.yap_finalprojectpapb.data.GatewaySettings
import com.example.yap_finalprojectpapb.data.GatewaySettingsStore
import com.example.yap_finalprojectpapb.data.StudioGateway
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GatewayIntegrationTest {
    @Test fun androidClientSendsPromptAndRevisionAndReceivesRealHtml() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = GatewaySettingsStore(context)
        val previousSettings = store.load()
        val executor = Executors.newSingleThreadExecutor()
        val server = ServerSocket(0)
        val html = "<!DOCTYPE html><html><head></head><body>Hasil aplikasi</body></html>"
        val token = "test-only-yap-access-token-32-characters"
        try {
            store.save(GatewaySettings("http://127.0.0.1:${server.localPort}", token))
            assertEquals(token, store.load().token)
            val stored = context.getSharedPreferences("yap_gateway", 0).getString("token", "")
            assertFalse(stored.orEmpty().contains(token))
            val request = executor.submit<String> {
                server.accept().use { socket ->
                    socket.soTimeout = 10_000
                    val reader = socket.getInputStream().bufferedReader()
                    val headers = mutableListOf<String>()
                    var line = reader.readLine()
                    while (!line.isNullOrEmpty()) {
                        headers.add(line)
                        line = reader.readLine()
                    }
                    assertTrue(headers.any { it == "POST /api/generate HTTP/1.1" })
                    assertTrue(headers.any { it == "Authorization: Bearer $token" })
                    val length = headers.first { it.startsWith("Content-Length:", true) }.substringAfter(':').trim().toInt()
                    val body = CharArray(length)
                    var offset = 0
                    while (offset < length) {
                        val read = reader.read(body, offset, length - offset)
                        check(read > 0)
                        offset += read
                    }
                    val response = buildJsonObject { put("html", html); put("model", "muse-spark-1.3") }
                        .toString().toByteArray(Charsets.UTF_8)
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(response)
                        flush()
                    }
                    String(body)
                }
            }
            assertEquals(html, StudioGateway(store).generate("Buat penghitung", "<html>old</html>"))
            val body = Json.parseToJsonElement(request.get(10, TimeUnit.SECONDS)).jsonObject
            assertEquals("Buat penghitung", body["prompt"]?.jsonPrimitive?.content)
            assertEquals("<html>old</html>", body["previousHtml"]?.jsonPrimitive?.content)
        } finally {
            store.save(previousSettings)
            server.close()
            executor.shutdownNow()
        }
    }
}
