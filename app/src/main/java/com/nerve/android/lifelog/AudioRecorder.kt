// nerve-app/app/src/main/java/com/nerve/android/lifelog/AudioRecorder.kt
package com.nerve.android.lifelog

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.nerve.android.util.Logger

class AudioRecorder(
  val sampleRate: Int = 16000,
  val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
  val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
) {
  private var record: AudioRecord? = null
  private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

  @SuppressLint("MissingPermission") // RECORD_AUDIO checked at service start
  fun start(onPcm: (ByteArray, Int) -> Unit) {
    val r = try {
      AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize)
    } catch (err: Exception) {
      Logger.error("AudioRecorder", "mic_unavailable", mapOf("reason" to err.message), err)
      throw err
    }
    record = r
    r.startRecording()
    Thread {
      val buf = ByteArray(2048)
      while (r.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
        val n = r.read(buf, 0, buf.size)
        if (n > 0) onPcm(buf, n)
      }
    }.apply { name = "lifelog-audio-thread"; start() }
  }

  fun stop() {
    try { record?.stop() } catch (e: Exception) {
      Logger.warn("AudioRecorder", "recorder_stop_fail", mapOf("reason" to e.message), e)
    }
    record?.release()
    record = null
  }
}
