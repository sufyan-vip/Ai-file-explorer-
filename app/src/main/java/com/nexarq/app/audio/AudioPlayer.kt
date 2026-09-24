package com.nexarq.app.audio

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/** Observable playback state for the built-in audio player. */
data class AudioPlayerState(
    val prepared: Boolean = false,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
)

/**
 * Lightweight audio player backed by android.media.MediaPlayer.
 *
 * Supports common formats the platform can decode (MP3, AAC/M4A, OGG/Vorbis,
 * FLAC, WAV, OPUS, MIDI). Position is polled on a background dispatcher so the
 * UI slider stays smooth without blocking the main thread.
 */
class AudioPlayer(private val scope: CoroutineScope) {

    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow(AudioPlayerState())
    val state: StateFlow<AudioPlayerState> = _state

    fun load(path: String) {
        releasePlayer()
        val mp = MediaPlayer()
        player = mp
        _state.value = AudioPlayerState()
        try {
            mp.setDataSource(path)
            mp.setOnPreparedListener {
                _state.value = AudioPlayerState(prepared = true, durationMs = it.duration.toLong())
                play()
            }
            mp.setOnCompletionListener {
                stopTicker()
                _state.value = _state.value.copy(playing = false, positionMs = _state.value.durationMs)
            }
            mp.setOnErrorListener { _, what, extra ->
                stopTicker()
                _state.value = _state.value.copy(
                    playing = false,
                    error = "Playback error (code $what/$extra)",
                )
                true
            }
            mp.prepareAsync()
        } catch (e: IOException) {
            _state.value = AudioPlayerState(error = e.message ?: "Cannot play this file")
        } catch (e: IllegalArgumentException) {
            _state.value = AudioPlayerState(error = e.message ?: "Cannot play this file")
        } catch (e: RuntimeException) {
            _state.value = AudioPlayerState(error = e.message ?: "Cannot play this file")
        }
    }

    fun toggle() {
        if (!_state.value.prepared) return
        if (_state.value.playing) pause() else play()
    }

    fun play() {
        val mp = player ?: return
        if (_state.value.prepared && !mp.isPlaying) {
            mp.start()
            _state.value = _state.value.copy(playing = true, error = null)
            startTicker()
        }
    }

    fun pause() {
        val mp = player ?: return
        if (mp.isPlaying) {
            mp.pause()
            _state.value = _state.value.copy(playing = false, positionMs = mp.currentPosition.toLong())
            stopTicker()
        }
    }

    fun seekTo(ms: Long) {
        val mp = player ?: return
        if (!_state.value.prepared) return
        val target = ms.coerceIn(0L, _state.value.durationMs)
        mp.seekTo(target.toInt())
        _state.value = _state.value.copy(positionMs = target)
    }

    fun seekBy(deltaMs: Long) = seekTo(_state.value.positionMs + deltaMs)

    private fun startTicker() {
        stopTicker()
        ticker = scope.launch(Dispatchers.Default) {
            while (true) {
                delay(250)
                val mp = player ?: break
                if (!mp.isPlaying) break
                _state.value = _state.value.copy(positionMs = mp.currentPosition.toLong())
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun releasePlayer() {
        stopTicker()
        runCatching { player?.release() }
        player = null
    }

    fun release() {
        releasePlayer()
    }
}
