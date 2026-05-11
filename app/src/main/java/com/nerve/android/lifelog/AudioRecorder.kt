// nerve-app/app/src/main/java/com/nerve/android/lifelog/AudioRecorder.kt
package com.nerve.android.lifelog

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

class AudioRecorder(
  val sampleRate: Int = 16000,
  val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
  val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
) {
  private var record: AudioRecord? = null
  private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

  @SuppressLint("MissingPermission") // RECORD_AUDIO checked at service start
  fun start(onPcm: (ByteArray, Int) -> Unit) {
    val r = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize)
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
    try { record?.stop() } catch (e: Exception) { Log.w("AudioRecorder", "stop", e) }
    record?.release()
    record = null
  }
}
