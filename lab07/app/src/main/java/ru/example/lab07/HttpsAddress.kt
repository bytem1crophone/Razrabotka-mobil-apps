package ru.example.lab07

import java.net.URI
import java.net.URISyntaxException

/** Validates web addresses before they are passed to an external application. */
object HttpsAddress {
    const val DEFAULT_ADDRESS = "https://developer.android.com"
    private const val MAX_LENGTH = 2048

    fun normalize(input: String): String? {
        val address = input.trim()
        if (address.isEmpty() || address.length > MAX_LENGTH) return null

        return try {
            val uri = URI(address).parseServerAuthority()
            if (!"https".equals(uri.scheme, ignoreCase = true) ||
                uri.host.isNullOrBlank() ||
                uri.rawUserInfo != null ||
                (uri.port != -1 && uri.port !in 1..65535) ||
                uri.rawAuthority.endsWith(":")
            ) {
                null
            } else {
                // Replace only the scheme: rebuilding URI components would escape '%' twice.
                URI("https" + address.substring(address.indexOf(':'))).toASCIIString()
            }
        } catch (_: URISyntaxException) {
            null
        }
    }
}
