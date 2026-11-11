package ru.example.lab07

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpsAddressTest {
    @Test
    fun officialDocumentationAndOtherHttpsSitesAreAccepted() {
        assertEquals(
            "https://developer.android.com/training/basics/intents/result",
            HttpsAddress.normalize("https://developer.android.com/training/basics/intents/result")
        )
        assertEquals("https://example.org", HttpsAddress.normalize("https://example.org"))
    }

    @Test
    fun pastedAddressTrimsSurroundingWhitespaceAndNormalizesScheme() {
        assertEquals(
            "https://developer.android.com/?hl=ru#start",
            HttpsAddress.normalize("  \tHTTPS://developer.android.com/?hl=ru#start\n")
        )
    }

    @Test
    fun nonHttpsSchemesAndAddressesWithoutSchemesAreRejected() {
        listOf(
            "http://developer.android.com",
            "file:///sdcard/note.txt",
            "javascript:alert(1)",
            "content://documents/note.txt",
            "developer.android.com",
            "//developer.android.com"
        ).forEach { assertNull("Must reject: $it", HttpsAddress.normalize(it)) }
    }

    @Test
    fun missingOrMalformedHostsAreRejected() {
        listOf(
            "", " \n\t ", "https://", "https:///note", "https:note",
            "https://?query=1", "https://exa mple.org", "https://-example.org",
            "https://example..org", "https://[invalid]", "https://example.org/%zz"
        ).forEach { assertNull("Must reject: $it", HttpsAddress.normalize(it)) }
    }

    @Test
    fun credentialsCannotDisguiseTheDestinationHost() {
        listOf(
            "https://developer.android.com@example.org",
            "https://user:password@example.org",
            "https://@example.org"
        ).forEach { assertNull("Must reject credentials: $it", HttpsAddress.normalize(it)) }
    }

    @Test
    fun explicitPortsMustBeUsableNetworkPorts() {
        assertEquals("https://example.org:1/", HttpsAddress.normalize("https://example.org:1/"))
        assertEquals(
            "https://example.org:65535/",
            HttpsAddress.normalize("https://example.org:65535/")
        )
        listOf("0", "65536", "-1", "abc", "").forEach { port ->
            assertNull("Must reject port '$port'", HttpsAddress.normalize("https://example.org:$port/"))
        }
    }

    @Test
    fun validEscapesArePreservedAndUnicodePathsBecomeAscii() {
        assertEquals(
            "https://example.org/a%20b?q=%2F",
            HttpsAddress.normalize("https://example.org/a%20b?q=%2F")
        )
        assertEquals(
            "https://example.org/%D1%82%D0%B5%D1%81%D1%82",
            HttpsAddress.normalize("https://example.org/тест")
        )
        assertEquals("https://[::1]:443/", HttpsAddress.normalize("https://[::1]:443/"))
    }

    @Test
    fun whitespaceInsideAnAddressIsRejected() {
        listOf(
            "https://example.org/a b", "https://example.org/\nnote",
            "https://exa\tmple.org", "https://example.org\\note"
        ).forEach { assertNull("Must reject: $it", HttpsAddress.normalize(it)) }
    }

    @Test
    fun oversizedPastedAddressesAreRejected() {
        val prefix = "https://example.org/"
        val maximumAddress = prefix + "a".repeat(2048 - prefix.length)
        assertEquals(maximumAddress, HttpsAddress.normalize(maximumAddress))
        assertNull(HttpsAddress.normalize(maximumAddress + "a"))
    }
}
