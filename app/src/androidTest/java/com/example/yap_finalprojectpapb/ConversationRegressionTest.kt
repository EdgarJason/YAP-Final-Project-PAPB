package com.example.yap_finalprojectpapb

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.example.yap_finalprojectpapb.data.*
import com.example.yap_finalprojectpapb.ui.viewmodels.StudioViewModel
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject

class ConversationRegressionTest {
    private class IsolatedApplication(private val context: Context) : Application() {
        private val suffix = UUID.randomUUID().toString()
        private val names = mutableSetOf<String>()
        init { attachBaseContext(context) }
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolated = "$name-$suffix"
            names.add(isolated)
            return context.getSharedPreferences(isolated, mode)
        }
        fun cleanup() { names.forEach { context.deleteSharedPreferences(it) } }
    }

    @Test fun greetingsAppendImmediatelyAndConversationsSurviveSwitchingAndReload() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = IsolatedApplication(instrumentation.targetContext)
        try {
            instrumentation.runOnMainSync {
                val model = StudioViewModel(app)
                model.buildApk("hi")
                val original = model.state.value.conversationId
                assertFalse(model.state.value.loading)
                assertNotNull(model.state.value.reply)
                assertNull(model.state.value.apk)
                model.buildApk("Halo YAP!")
                assertEquals(listOf("hi", "Halo YAP!"), model.state.value.turns.map { it.prompt })
                model.reset()
                assertTrue(model.state.value.turns.isEmpty())
                model.buildApk("hello")
                val second = model.state.value.conversationId
                assertNotEquals(original, second)
                model.openConversation(original)
                assertEquals(2, model.state.value.turns.size)
                val restored = StudioViewModel(app)
                assertEquals(original, restored.state.value.conversationId)
                assertEquals(listOf("hi", "Halo YAP!"), restored.state.value.turns.map { it.prompt })
                restored.openConversation(second)
                assertEquals("hello", restored.state.value.turns.single().prompt)
            }
        } finally { app.cleanup() }
    }

    @Test fun legacySavedBuildMigratesWithoutLosingItsIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = IsolatedApplication(context)
        try {
            val previous = SavedApkBuild(UUID.randomUUID().toString(), "Buat kalkulator", "https://example.com")
            GatewaySettingsStore(app).saveApkBuild(previous)
            val store = StudioConversationStore(app)
            val migrated = store.load()
            val turn = migrated.conversations.single().turns.single()
            assertEquals(previous.id, turn.id)
            assertEquals(previous.prompt, turn.prompt)
            assertEquals(previous.gatewayUrl, turn.gatewayUrl)
            store.save(migrated)
            assertEquals(migrated, StudioConversationStore(app).load())
        } finally { app.cleanup() }
    }

    @Test fun revisionIncludesEarlierMessagesAndThePreviousResultId() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = IsolatedApplication(instrumentation.targetContext)
        val server = ServerSocket(0).apply { soTimeout = 10_000 }
        val executor = Executors.newSingleThreadExecutor()
        val requests = executor.submit<List<JSONObject>> {
            List(2) {
                server.accept().use { socket ->
                    socket.soTimeout = 10_000
                    val input = socket.getInputStream().bufferedReader()
                    input.readLine()
                    var line = input.readLine()
                    var length = 0
                    while (!line.isNullOrEmpty()) {
                        if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                        line = input.readLine()
                    }
                    val body = CharArray(length)
                    var read = 0
                    while (read < length) {
                        val count = input.read(body, read, length - read)
                        check(count > 0)
                        read += count
                    }
                    val request = JSONObject(String(body))
                    val response = JSONObject().put("id", request.getString("id"))
                        .put("status", "responded").put("message", "Baik.").toString().toByteArray()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(response)
                        flush()
                    }
                    request
                }
            }
        }
        try {
            lateinit var model: StudioViewModel
            instrumentation.runOnMainSync {
                GatewaySettingsStore(app).save(GatewaySettings("http://127.0.0.1:${server.localPort}", "fixture".repeat(8)))
                model = StudioViewModel(app)
                model.buildApk("hi")
                model.buildApk("Buat counter")
            }
            val deadline = System.currentTimeMillis() + 10_000
            while (model.state.value.loading && System.currentTimeMillis() < deadline) Thread.sleep(20)
            assertEquals("responded", model.state.value.apk?.status)
            instrumentation.runOnMainSync { model.buildApk("Ubah menjadi hijau") }
            val captured = requests.get(10, TimeUnit.SECONDS)
            assertTrue(captured[0].getString("context").contains("hi"))
            assertTrue(captured[1].getString("context").contains("Buat counter"))
            assertTrue(captured[1].getString("context").contains("Baik."))
            assertTrue(captured[1].getString("context").contains(captured[0].getString("id")))
            val completionDeadline = System.currentTimeMillis() + 10_000
            while (model.state.value.loading && System.currentTimeMillis() < completionDeadline) Thread.sleep(20)
            assertEquals("responded", model.state.value.apk?.status)
        } finally {
            server.close()
            executor.shutdownNow()
            app.cleanup()
        }
    }
}
