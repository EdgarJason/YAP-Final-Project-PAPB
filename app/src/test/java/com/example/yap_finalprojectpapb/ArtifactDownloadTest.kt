package com.example.yap_finalprojectpapb

import com.example.yap_finalprojectpapb.data.validatedArtifactUrl
import org.junit.Assert.*
import org.junit.Test

class ArtifactDownloadTest {
    private val origin = "https://ipgkbdzdtmonrythiqta.supabase.co"
    @Test fun acceptsOnlyOurSignedArtifactStorage() {
        val url = "$origin/storage/v1/object/sign/yap-artifacts/job/result.apk?token=example"
        assertEquals(url, validatedArtifactUrl(url).toString())
    }
    @Test fun rejectsForeignOriginsCredentialsTraversalAndUnsignedPaths() {
        listOf(
            "https://evil.example/storage/v1/object/sign/yap-artifacts/a?token=x",
            "$origin.evil.example/storage/v1/object/sign/yap-artifacts/a?token=x",
            origin.replace("https:", "http:") + "/storage/v1/object/sign/yap-artifacts/a?token=x",
            origin.replace("https://", "https://secret@") + "/storage/v1/object/sign/yap-artifacts/a?token=x",
            "$origin/storage/v1/object/sign/other/a?token=x",
            "$origin/storage/v1/object/sign/yap-artifacts/../other/a?token=x",
            "$origin/storage/v1/object/sign/yap-artifacts/%2e%2e/a?token=x",
            "$origin/storage/v1/object/sign/yap-artifacts/a",
            "$origin/storage/v1/object/sign/yap-artifacts/a?token=x#fragment"
        ).forEach { value -> assertTrue(value, runCatching { validatedArtifactUrl(value) }.isFailure) }
    }
}
