package com.cineenglish.app.domain.audio

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class AudioPlayer(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var segmentEndMs: Long = -1L
    private var segmentStartMs: Long = 0L
    private var isLoopingSegment: Boolean = false

    var onPlaybackEnded: (() -> Unit)? = null
    var onPositionChanged: ((Long) -> Unit)? = null
    var onPlaybackError: ((Exception) -> Unit)? = null

    private val progressRunnable = object : Runnable {
        override fun run() {
            exoPlayer?.let { player ->
                if (player.isPlaying) {
                    val currentPos = player.currentPosition
                    onPositionChanged?.invoke(currentPos)

                    // Check segment boundary for sentence pause or loop
                    if (segmentEndMs > 0 && currentPos >= segmentEndMs) {
                        if (isLoopingSegment) {
                            player.seekTo(segmentStartMs)
                        } else {
                            player.pause()
                            onPlaybackEnded?.invoke()
                            return
                        }
                    }
                }
            }
            handler.postDelayed(this, 100)
        }
    }

    private fun ensurePlayer(): ExoPlayer {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            if (isLoopingSegment && segmentEndMs > 0) {
                                seekTo(segmentStartMs)
                                play()
                            } else {
                                onPlaybackEnded?.invoke()
                            }
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        onPlaybackError?.invoke(error)
                    }
                })
            }
            handler.post(progressRunnable)
        }
        return exoPlayer!!
    }

    fun playAudio(
        uri: Uri,
        speed: Float = 1.0f,
        startMs: Long = 0L,
        endMs: Long = -1L,
        loop: Boolean = false
    ) {
        val player = ensurePlayer()
        player.stop()

        segmentStartMs = startMs
        segmentEndMs = endMs
        isLoopingSegment = loop

        // Set pitch-preserving playback speed
        player.playbackParameters = PlaybackParameters(speed, 1.0f)

        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        if (startMs > 0) {
            player.seekTo(startMs)
        }
        player.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        exoPlayer?.play()
    }

    fun stop() {
        exoPlayer?.stop()
        segmentEndMs = -1L
        isLoopingSegment = false
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying == true

    fun setSpeed(speed: Float) {
        exoPlayer?.playbackParameters = PlaybackParameters(speed, 1.0f)
    }

    fun release() {
        handler.removeCallbacks(progressRunnable)
        exoPlayer?.release()
        exoPlayer = null
    }
}
