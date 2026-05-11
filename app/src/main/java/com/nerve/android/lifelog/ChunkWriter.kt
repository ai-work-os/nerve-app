package com.nerve.android.lifelog

import android.content.Context
import java.io.File
import java.security.MessageDigest

data class Chunk(
  val chunkId: String,
  val deviceId: String,
  val recordedAtMs: Long,
  val durationMs: Long,
  val path: String,
)

class ChunkWriter(context: Context, private val deviceId: String) {
  private val dir: File = File(context.cacheDir, "lifelog/chunks").apply { mkdirs() }

  fun write(opusBytes: ByteArray, recordedAtMs: Long, durationMs: Long): Chunk {
    val chunkId = makeId(deviceId, recordedAtMs)
    val file = File(dir, "$chunkId.opus")
    file.writeBytes(opusBytes)
    return Chunk(chunkId, deviceId, recordedAtMs, durationMs, file.absolutePath)
  }

  fun delete(chunkId: String) {
    File(dir, "$chunkId.opus").delete()
  }

  companion object {
    fun makeId(deviceId: String, recordedAtMs: Long): String {
      val md = MessageDigest.getInstance("SHA-256")
      val bytes = md.digest("$deviceId:$recordedAtMs".toByteArray())
      return bytes.joinToString("") { "%02x".format(it) }.substring(0, 16)
    }
  }
}
