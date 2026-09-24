package com.warrantyvault.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Request building and response parsing for the Drive REST calls. These are the parts most likely
 * to break silently (a bad query string returns an empty list, which looks exactly like "no backup
 * yet"), so they are pinned down here rather than only being exercised against the live API.
 */
class DriveRestTest {

    private val properties = mapOf(
        DriveRest.PROP_PRODUCT_COUNT to "4",
        DriveRest.PROP_FORMAT to BackupEnvelope.FORMAT
    )

    @Test
    fun `list url targets the app data folder with an encoded query`() {
        val url = DriveRest.listUrl()

        assertTrue(url.startsWith("https://www.googleapis.com/drive/v3/files?"))
        assertTrue(url.contains("spaces=appDataFolder"))
        // The q= value must be percent-encoded; raw spaces would break the request.
        assertTrue(url.contains("q=name%20%3D%20%27${BackupEnvelope.FILE_NAME}%27%20and%20trashed%20%3D%20false"))
        // The product-count property must be requested, or the overwrite guard loses its input.
        assertTrue(url.contains("fields=files%28id%2Cname%2CmodifiedTime%2Csize%2CappProperties%29"))
        assertTrue("no raw spaces allowed in the URL", !url.contains(" "))
    }

    @Test
    fun `upload and download urls point at the expected endpoints`() {
        assertTrue(DriveRest.createUrl().startsWith("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"))
        // Updates are multipart too, so the app-private properties stay in step with the content.
        assertTrue(DriveRest.updateUrl("abc123").contains("/abc123?uploadType=multipart"))
        assertTrue(DriveRest.downloadUrl("abc123").endsWith("/abc123?alt=media"))
        assertTrue(DriveRest.aboutUrl().startsWith("https://www.googleapis.com/drive/v3/about"))
        // Metadata-only create is how the visible folder gets made.
        assertTrue(DriveRest.metadataCreateUrl().startsWith("https://www.googleapis.com/drive/v3/files?fields="))
    }

    @Test
    fun `multipart body contains metadata, properties, content and a closing boundary`() {
        val content = """{"products":[{"productName":"boAt Airdopes 141 — ₹1,299"}]}""".toByteArray(Charsets.UTF_8)
        val body = String(
            DriveRest.multipartBody(
                BackupEnvelope.FILE_NAME,
                content,
                properties,
                parents = listOf(DriveRest.APPDATA_FOLDER)
            ),
            Charsets.UTF_8
        )

        assertTrue(body.startsWith("--warrantyvault_backup_boundary_7f3a\r\n"))
        assertTrue(body.contains("\"name\":\"${BackupEnvelope.FILE_NAME}\""))
        assertTrue(body.contains("\"parents\":[\"appDataFolder\"]"))
        assertTrue(body.contains("\"appProperties\""))
        assertTrue(body.contains("\"${DriveRest.PROP_PRODUCT_COUNT}\":\"4\""))
        // Two parts: metadata, then the file content.
        assertEquals(2, Regex("--warrantyvault_backup_boundary_7f3a\r\n").findAll(body).count())
        assertTrue(body.endsWith("\r\n--warrantyvault_backup_boundary_7f3a--\r\n"))
        assertTrue(body.contains("₹1,299"))
        assertTrue(DriveRest.MULTIPART_CONTENT_TYPE.contains("multipart/related"))
    }

    @Test
    fun `an update body omits parents because a file cannot change folder`() {
        val body = String(
            DriveRest.multipartBody(BackupEnvelope.FILE_NAME, "{}".toByteArray(Charsets.UTF_8), properties),
            Charsets.UTF_8
        )

        assertTrue(!body.contains("\"parents\""))
        assertTrue(body.contains("\"appProperties\""))
    }

    @Test
    fun `a create body can target the private or the visible folder`() {
        val empty = "{}".toByteArray(Charsets.UTF_8)

        val privateBody = String(
            DriveRest.multipartBody("x.json", empty, properties, parents = listOf(DriveRest.APPDATA_FOLDER)),
            Charsets.UTF_8
        )
        assertTrue(privateBody.contains("\"parents\":[\"appDataFolder\"]"))

        val visibleBody = String(
            DriveRest.multipartBody("x.json", empty, properties, parents = listOf("folder123")),
            Charsets.UTF_8
        )
        assertTrue(visibleBody.contains("\"parents\":[\"folder123\"]"))
    }

    @Test
    fun `visible folder lookup is scoped to My Drive root and the folder mime type`() {
        val url = DriveRest.visibleFolderQueryUrl()

        assertTrue(url.contains("spaces=drive"))
        assertTrue(url.contains("%27${DriveRest.ROOT_FOLDER}%27%20in%20parents"))
        assertTrue(url.contains("mimeType%20%3D%20%27application%2Fvnd.google-apps.folder%27"))
        assertTrue(url.contains(DriveRest.VISIBLE_FOLDER_NAME))
        assertTrue(!url.contains(" "))
    }

    @Test
    fun `children lookup is scoped to the resolved folder`() {
        val url = DriveRest.childrenQueryUrl("folder123")

        assertTrue(url.contains("%27folder123%27%20in%20parents"))
        assertTrue(url.contains("spaces=drive"))
        assertTrue(url.contains("appProperties"))
        assertTrue(!url.contains(" "))
    }

    @Test
    fun `folder creation body names the folder and puts it in My Drive root`() {
        val body = String(DriveRest.folderCreateBody(), Charsets.UTF_8)

        assertTrue(body.contains("\"name\":\"${DriveRest.VISIBLE_FOLDER_NAME}\""))
        assertTrue(body.contains("\"mimeType\":\"application/vnd.google-apps.folder\""))
        assertTrue(body.contains("\"parents\":[\"root\"]"))
    }

    @Test
    fun `multipart content bytes are preserved exactly`() {
        val content = "😀 unicode ✓ and \"quotes\"".toByteArray(Charsets.UTF_8)
        val body = DriveRest.multipartBody("x.json", content)

        val index = body.indexOfSubArray("😀 unicode".toByteArray(Charsets.UTF_8))
        assertTrue("content bytes missing from multipart body", index > 0)
    }

    @Test
    fun `file list response is parsed and an empty list is not an error`() {
        assertEquals(emptyList<DriveRest.BackupFileInfo>(), DriveRest.parseFileList("""{"files":[]}"""))
        assertEquals(emptyList<DriveRest.BackupFileInfo>(), DriveRest.parseFileList("{}"))

        val listed = DriveRest.parseFileList(
            """
            {"files":[
              {"id":"1AbC","name":"${BackupEnvelope.FILE_NAME}","modifiedTime":"2026-09-24T09:31:00.000Z",
               "size":"2048",
               "appProperties":{"${DriveRest.PROP_PRODUCT_COUNT}":"7","${DriveRest.PROP_FORMAT}":"${BackupEnvelope.FORMAT}"}}
            ]}
            """.trimIndent()
        )

        assertEquals(1, listed.size)
        assertEquals("1AbC", listed[0].id)
        assertEquals(BackupEnvelope.FILE_NAME, listed[0].name)
        assertEquals("2026-09-24T09:31:00.000Z", listed[0].modifiedTime)
        assertEquals(2048L, listed[0].sizeBytes)
        // This is what the overwrite guard compares against the local product count.
        assertEquals(7, listed[0].productCount)
    }

    @Test
    fun `a backup predating the property has an unknown product count`() {
        val info = DriveRest.parseFile("""{"id":"abc","name":"warrantyvault-backup.json"}""")

        assertEquals("abc", info.id)
        assertNull(info.modifiedTime)
        assertNull(info.sizeBytes)
        assertNull(info.productCount)
        assertEquals(emptyMap<String, String>(), info.appProperties)
    }

    @Test
    fun `revoke url encodes the access token`() {
        val url = DriveRest.revokeUrl("ya29.a0/Ab+C=")

        assertTrue(url.startsWith("https://oauth2.googleapis.com/revoke?token="))
        assertTrue(url.contains("ya29.a0%2FAb%2BC%3D"))
        assertTrue(!url.contains(" "))
    }

    @Test
    fun `about response yields the linked account email`() {
        val user = DriveRest.parseUser(
            """{"user":{"displayName":"Aakash S","emailAddress":"aakash@example.com","me":true}}"""
        )
        assertEquals("aakash@example.com", user.email)
        assertEquals("Aakash S", user.displayName)

        assertNull(DriveRest.parseUser("""{"user":{}}""").email)
        assertNull(DriveRest.parseUser("{}").email)
    }

    private fun ByteArray.indexOfSubArray(needle: ByteArray): Int {
        if (needle.isEmpty() || needle.size > size) return -1
        outer@ for (i in 0..size - needle.size) {
            for (j in needle.indices) {
                if (this[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
