package com.example.yap_finalprojectpapb.data

import java.net.URI

/** A Storage request never carries the gateway bearer token or follows redirects. */
internal fun validatedArtifactUrl(value: String): URI {
    val uri = URI(value)
    require(uri.scheme == "https" && uri.host == "ipgkbdzdtmonrythiqta.supabase.co" &&
        uri.port in listOf(-1, 443) && uri.userInfo == null && uri.fragment == null &&
        uri.rawPath.startsWith("/storage/v1/object/sign/yap-artifacts/") &&
        uri.rawPath == uri.normalize().rawPath && !uri.rawPath.contains('%') &&
        !uri.rawQuery.isNullOrBlank()) { "Alamat unduhan APK tidak valid." }
    return uri
}
