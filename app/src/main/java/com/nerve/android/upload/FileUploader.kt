package com.nerve.android.upload

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class PendingFileUpload(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
)

@Serializable
data class UploadedFile(
    val path: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
)

interface FileUploadClient {
    fun upload(baseUrl: String, file: PendingFileUpload): UploadedFile
}

class FileUploadException(message: String, cause: Throwable? = null) : IOException(message, cause)

class FileUploader(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : FileUploadClient {
    override fun upload(baseUrl: String, file: PendingFileUpload): UploadedFile {
        val body = file.bytes.toRequestBody(file.mimeType.toMediaType())
        val req = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/files/upload")
            .header("X-File-Name-Encoded", encodeHeaderFileName(file.name))
            .post(body)
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw FileUploadException(parseError(raw) ?: "upload failed: HTTP ${resp.code}")
                }
                return json.decodeFromString(UploadedFile.serializer(), raw)
            }
        } catch (e: FileUploadException) {
            throw e
        } catch (e: IOException) {
            throw FileUploadException(e.message ?: "upload failed", e)
        }
    }

    private fun parseError(raw: String): String? =
        runCatching {
            json.parseToJsonElement(raw).jsonObject["error"]?.jsonPrimitive?.content
        }.getOrNull()

    private fun encodeHeaderFileName(name: String): String =
        URLEncoder.encode(name, StandardCharsets.UTF_8.toString()).replace("+", "%20")
}
