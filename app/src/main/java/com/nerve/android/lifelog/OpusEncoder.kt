// nerve-app/app/src/main/java/com/nerve/android/lifelog/OpusEncoder.kt
package com.nerve.android.lifelog

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Encodes 16-bit LE 16kHz mono PCM into a complete Ogg/Opus file at outFile.
 * Call `feed(pcm, len)` repeatedly during recording, then `finishToFile()` once
 * to flush + close. Each output file is a complete chunk.
 */
class OpusEncoder(private val outFile: File, sampleRate: Int = 16000) {
  private val codec: MediaCodec = MediaCodec.createEncoderByType("audio/opus")
  private val muxer: MediaMuxer
  private var trackIdx = -1
  private var muxerStarted = false

  init {
    val fmt = MediaFormat.createAudioFormat("audio/opus", sampleRate, 1).apply {
      setInteger(MediaFormat.KEY_BIT_RATE, 16_000) // 16 kbps VBR
    }
    codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    codec.start()
    muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
  }

  fun feed(pcm: ByteArray, len: Int) {
    val idx = codec.dequeueInputBuffer(10_000)
    if (idx >= 0) {
      val buf = codec.getInputBuffer(idx)!!
      buf.clear(); buf.put(pcm, 0, len)
      codec.queueInputBuffer(idx, 0, len, System.nanoTime() / 1000, 0)
    }
    drainOnce()
  }

  fun finishToFile() {
    val idx = codec.dequeueInputBuffer(10_000)
    if (idx >= 0) codec.queueInputBuffer(idx, 0, 0, System.nanoTime() / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
    while (true) {
      val info = MediaCodec.BufferInfo()
      val out = codec.dequeueOutputBuffer(info, 50_000)
      if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        trackIdx = muxer.addTrack(codec.outputFormat); muxer.start(); muxerStarted = true
      } else if (out >= 0) {
        if (info.size > 0 && muxerStarted) {
          val outBuf: ByteBuffer = codec.getOutputBuffer(out)!!
          muxer.writeSampleData(trackIdx, outBuf, info)
        }
        codec.releaseOutputBuffer(out, false)
        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
      } else if (out == MediaCodec.INFO_TRY_AGAIN_LATER) continue
    }
    if (muxerStarted) { muxer.stop() }
    muxer.release(); codec.stop(); codec.release()
  }

  private fun drainOnce() {
    while (true) {
      val info = MediaCodec.BufferInfo()
      val out = codec.dequeueOutputBuffer(info, 0)
      if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        trackIdx = muxer.addTrack(codec.outputFormat); muxer.start(); muxerStarted = true
      } else if (out >= 0) {
        if (info.size > 0 && muxerStarted) {
          muxer.writeSampleData(trackIdx, codec.getOutputBuffer(out)!!, info)
        }
        codec.releaseOutputBuffer(out, false)
      } else break
    }
  }
}
