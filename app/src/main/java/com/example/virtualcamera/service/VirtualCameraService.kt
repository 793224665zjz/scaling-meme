package com.example.virtualcamera.service

import android.app.Service
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.nio.ByteBuffer

class VirtualCameraService : Service() {
    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    
    private var videoExtractor: MediaExtractor? = null
    private var videoDecoder: MediaCodec? = null
    private var isRunning = false
    private var currentCameraType = CameraType.BACK
    private var videoFilePath: String? = null

    enum class CameraType {
        FRONT, BACK
    }

    inner class LocalBinder : Binder() {
        fun getService(): VirtualCameraService = this@VirtualCameraService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "VirtualCameraService started")
        return START_STICKY
    }

    fun setVideoFile(filePath: String) {
        this.videoFilePath = filePath
        Log.d(TAG, "Video file set: $filePath")
    }

    fun switchCamera(cameraType: CameraType) {
        currentCameraType = cameraType
        Log.d(TAG, "Camera switched to: $cameraType")
    }

    fun startVirtualCamera() {
        if (isRunning) {
            Log.w(TAG, "Virtual camera is already running")
            return
        }

        videoFilePath?.let { filePath ->
            scope.launch {
                try {
                    isRunning = true
                    initializeDecoder(filePath)
                    startVideoPlayback()
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting virtual camera", e)
                    isRunning = false
                }
            }
        }
    }

    fun stopVirtualCamera() {
        isRunning = false
        releaseResources()
        Log.d(TAG, "Virtual camera stopped")
    }

    private suspend fun initializeDecoder(filePath: String) = withContext(Dispatchers.Default) {
        try {
            videoExtractor = MediaExtractor().apply {
                setDataSource(filePath)
            }

            val videoTrackIndex = findVideoTrack()
            if (videoTrackIndex < 0) {
                throw IllegalArgumentException("No video track found in file")
            }

            val format = videoExtractor?.getTrackFormat(videoTrackIndex)
                ?: throw IllegalStateException("Cannot get track format")

            videoExtractor?.selectTrack(videoTrackIndex)

            val mimeType = format.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("Cannot get MIME type")

            videoDecoder = MediaCodec.createDecoderByType(mimeType).apply {
                configure(format, null, null, 0)
                start()
            }

            Log.d(TAG, "Decoder initialized for MIME type: $mimeType")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize decoder", e)
            throw e
        }
    }

    private fun findVideoTrack(): Int {
        val extractor = videoExtractor ?: return -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mimeType = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mimeType.startsWith("video/")) {
                return i
            }
        }
        return -1
    }

    private suspend fun startVideoPlayback() = withContext(Dispatchers.Default) {
        val extractor = videoExtractor ?: return@withContext
        val decoder = videoDecoder ?: return@withContext

        val inputBuffers = decoder.inputBuffers
        val outputBuffers = decoder.outputBuffers
        val bufferInfo = MediaCodec.BufferInfo()

        var frameCount = 0

        while (isRunning && !Thread.currentThread().isInterrupted) {
            val inputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferIndex >= 0) {
                val inputBuffer = inputBuffers[inputBufferIndex]
                val sampleSize = extractor.readSampleData(inputBuffer, 0)

                if (sampleSize > 0) {
                    val presentationTime = extractor.sampleTime
                    decoder.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        sampleSize,
                        presentationTime,
                        0
                    )
                    extractor.advance()
                } else {
                    decoder.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        0,
                        0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }
            }

            val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputBufferIndex >= 0 -> {
                    if (bufferInfo.size > 0) {
                        frameCount++
                    }
                    decoder.releaseOutputBuffer(outputBufferIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isRunning = false
                        Log.d(TAG, "End of stream reached. Total frames: $frameCount")
                    }
                }
            }

            delay(1)
        }
    }

    private fun releaseResources() {
        try {
            videoDecoder?.stop()
            videoDecoder?.release()
            videoExtractor?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources", e)
        } finally {
            videoDecoder = null
            videoExtractor = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        stopVirtualCamera()
        Log.d(TAG, "VirtualCameraService destroyed")
    }

    companion object {
        private const val TAG = "VirtualCameraService"
        private const val TIMEOUT_US = 10000L
    }
}