package com.warrantyvault.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup format has to be stable enough that a file uploaded today still restores after the
 * app changes, and strict enough that a random JSON file in the user's Drive is rejected with a
 * clear reason instead of reaching the database importer.
 */
class BackupEnvelopeTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** Mirrors the real export document, with the name escaped the way JSON requires. */
    private fun exportDoc(productName: String = "ASUS TUF Gaming F15"): String {
        val name = productName
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        return """
        {
          "exportedAt": "2026-09-24T09:30:00Z",
          "count": 1,
          "products": [
            {
              "id": 1,
              "productName": "$name",
              "brand": "ASUS",
              "serialNumber": "N8NRCV012345678",
              "warrantyPeriodMonths": 24,
              "serviceHistory": []
            }
          ]
        }
    """.trimIndent()
    }

    private fun wrap(exportJson: String = exportDoc(), count: Int = 1): String =
        BackupEnvelope.wrap(exportJson, appVersion = "1.0.0", exportedAt = "2026-09-24T09:31:00Z", count = count)

    @Test
    fun `wrap then unwrap returns the original payload`() {
        val unwrapped = BackupEnvelope.unwrap(wrap()).getOrThrow()

        assertEquals(BackupEnvelope.CURRENT_VERSION, unwrapped.version)
        assertEquals("2026-09-24T09:31:00Z", unwrapped.exportedAt)
        assertEquals("1.0.0", unwrapped.appVersion)
        assertEquals(1, unwrapped.count)

        val products = json.parseToJsonElement(unwrapped.dataJson).jsonObject["products"]!!.jsonArray
        assertEquals(1, products.size)
        assertEquals("ASUS TUF Gaming F15", products[0].jsonObject["productName"]!!.jsonPrimitive.content)
        assertEquals("N8NRCV012345678", products[0].jsonObject["serialNumber"]!!.jsonPrimitive.content)
    }

    @Test
    fun `wrapped document carries an identifiable format marker and version`() {
        val obj = json.parseToJsonElement(wrap()).jsonObject
        assertEquals(BackupEnvelope.FORMAT, obj["format"]!!.jsonPrimitive.content)
        assertEquals("1", obj["version"]!!.jsonPrimitive.content)
        assertTrue(BackupEnvelope.looksLikeBackup(wrap()))
    }

    @Test
    fun `product names with quotes unicode and newlines survive the round trip`() {
        val tricky = "Noise ColorFit \"Pulse\" 3 — ₹2,499\nsecond line"
        val unwrapped = BackupEnvelope.unwrap(wrap(exportDoc(productName = tricky))).getOrThrow()
        val name = json.parseToJsonElement(unwrapped.dataJson)
            .jsonObject["products"]!!.jsonArray[0].jsonObject["productName"]!!.jsonPrimitive.content
        assertEquals(tricky, name)
    }

    @Test
    fun `a plain JSON export without the envelope is not mistaken for a backup`() {
        val result = BackupEnvelope.unwrap(exportDoc())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("format marker"))
        assertFalse(BackupEnvelope.looksLikeBackup(exportDoc()))
    }

    @Test
    fun `a file from a newer app version is refused with an explanatory message`() {
        val future = """
            {"format":"${BackupEnvelope.FORMAT}","version":${BackupEnvelope.CURRENT_VERSION + 1},
             "exportedAt":"2030-01-01T00:00:00Z","appVersion":"9.9.9","count":0,
             "data":{"products":[]}}
        """.trimIndent()

        val error = BackupEnvelope.unwrap(future).exceptionOrNull()!!
        assertTrue(error.message!!.contains("newer version"))
    }

    @Test
    fun `malformed input fails instead of throwing out of unwrap`() {
        listOf(
            "not json at all",
            "[]",
            """{"format":"something-else","version":1,"data":{"products":[]}}""",
            """{"format":"${BackupEnvelope.FORMAT}","version":1}""",
            """{"format":"${BackupEnvelope.FORMAT}","version":1,"data":{}}""",
            """{"format":"${BackupEnvelope.FORMAT}","exportedAt":"x","data":{"products":[]}}"""
        ).forEach { bad ->
            assertTrue("expected failure for: $bad", BackupEnvelope.unwrap(bad).isFailure)
        }
    }

    @Test
    fun `missing count falls back to the number of products`() {
        val noCount = """
            {"format":"${BackupEnvelope.FORMAT}","version":1,"exportedAt":"2026-01-01T00:00:00Z",
             "appVersion":"1.0.0","data":{"products":[{"productName":"A"},{"productName":"B"}]}}
        """.trimIndent()

        assertEquals(2, BackupEnvelope.unwrap(noCount).getOrThrow().count)
    }

    @Test
    fun `an empty vault still produces a valid restorable backup`() {
        val empty = """{"exportedAt":"2026-09-24T09:30:00Z","count":0,"products":[]}"""
        val unwrapped = BackupEnvelope.unwrap(wrap(empty, count = 0)).getOrThrow()
        assertEquals(0, unwrapped.count)
        assertTrue(json.parseToJsonElement(unwrapped.dataJson).jsonObject.containsKey("products"))
    }
}
