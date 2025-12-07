package com.darkempire78.opencalculator

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.ArrayDeque

/**
 * TaigiSoundPlayer - Manages Taigi sound playback for calculator keys
 * 
 * This class provides a clean, independent way to play Taigi sounds when
 * number and operator keys are pressed. It uses SoundPool for efficient
 * playback of short audio clips.
 * 
 * Usage:
 *   val soundPlayer = TaigiSoundPlayer(context)
 *   soundPlayer.playNumber(5)  // Play sound for number 5
 *   soundPlayer.playOperator("+")  // Play sound for addition
 *   soundPlayer.release()  // Clean up when done
 */
class TaigiSoundPlayer(private val context: Context) {
    
    companion object {
        private const val TAG = "TaigiSoundPlayer"
        private const val MAX_STREAMS = 10  // Allow multiple streams for smooth transitions
        private const val PLAYBACK_RATE = 1.25f // Speed up audio a little
    }
    
    // SoundPool for efficient audio playback
    private var soundPool: SoundPool? = null
    
    // Maps to store loaded sound IDs and their durations
    private val numberSounds = mutableMapOf<Int, Int>()  // digit -> soundId
    private val operatorSounds = mutableMapOf<String, Int>()  // operator -> soundId
    private var dotSoundId: Int? = null
    private var equalsSoundId: Int? = null
    private val soundDurations = mutableMapOf<Int, Long>() // soundId -> duration in ms
    
    // Queue for sequential playback
    private val soundQueue = ArrayDeque<Int>()
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())
    
    init {
        initializeSoundPool()
        loadSounds()
    }
    
    /**
     * Initialize the SoundPool with appropriate audio attributes
     */
    private fun initializeSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        
        soundPool = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(audioAttributes)
            .build()
            
        // Warm up sounds by playing them at 0 volume when loaded
        soundPool?.setOnLoadCompleteListener { pool, soundId, status ->
            if (status == 0) {
                pool.play(soundId, 0.0f, 0.0f, 0, 0, 1.0f)
            }
        }
    }
    
    /**
     * Load all sound resources from res/raw directory
     * Gracefully handles missing audio files
     */
    private fun loadSounds() {
        try {
            // Load number sounds (0-9)
            for (digit in 0..9) {
                val resourceName = "num_$digit"
                val resourceId = getResourceId(resourceName)
                if (resourceId != 0) {
                    soundPool?.load(context, resourceId, 1)?.let { soundId ->
                        numberSounds[digit] = soundId
                        soundDurations[soundId] = getDuration(resourceId)
                    }
                }
            }
            
            // Load dot sound
            val dotResourceId = getResourceId("num_dot")
            if (dotResourceId != 0) {
                soundPool?.load(context, dotResourceId, 1)?.let { soundId ->
                    dotSoundId = soundId
                    soundDurations[soundId] = getDuration(dotResourceId)
                }
            }
            
            // Load equals sound
            val equalsResourceId = getResourceId("op_equals")
            if (equalsResourceId != 0) {
                soundPool?.load(context, equalsResourceId, 1)?.let { soundId ->
                    equalsSoundId = soundId
                    soundDurations[soundId] = getDuration(equalsResourceId)
                }
            }
            
            // Load operator sounds
            val operators = mapOf(
                "+" to "op_add",
                "-" to "op_subtract",
                "×" to "op_multiply",
                "÷" to "op_divide"
            )
            
            for ((operator, resourceName) in operators) {
                val resourceId = getResourceId(resourceName)
                if (resourceId != 0) {
                    soundPool?.load(context, resourceId, 1)?.let { soundId ->
                        operatorSounds[operator] = soundId
                        soundDurations[soundId] = getDuration(resourceId)
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading sounds: ${e.message}", e)
        }
    }

    private fun getDuration(resourceId: Int): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            val afd = context.resources.openRawResourceFd(resourceId)
            retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            time?.toLong() ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting duration", e)
            0L
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }
    }
    
    /**
     * Get resource ID by name from res/raw directory
     * Returns 0 if resource not found
     */
    private fun getResourceId(resourceName: String): Int {
        return try {
            // Try multiple package names to find the resource
            val packagesToTry = listOf(
                context.packageName, // Current runtime package (e.g. app.taigi.kesngki.debug)
                context.applicationContext.packageName,
                "com.darkempire78.opencalculator", // Original namespace
                "app.taigi.kesngki" // Base application ID
            ).distinct()

            for (pkg in packagesToTry) {
                val resourceId = context.resources.getIdentifier(resourceName, "raw", pkg)
                if (resourceId != 0) {
                    return resourceId
                }
            }
            
            0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting resource ID for $resourceName: ${e.message}")
            0
        }
    }
    
    /**
     * Play sound for a number digit (0-9)
     * @param digit The digit to play sound for
     */
    fun playNumber(digit: Int) {
        if (digit !in 0..9) return
        
        numberSounds[digit]?.let { soundId ->
            queueSound(soundId)
        }
    }
    
    /**
     * Play sound for an operator (+, -, ×, ÷)
     * @param operator The operator symbol
     */
    fun playOperator(operator: String) {
        operatorSounds[operator]?.let { soundId ->
            queueSound(soundId)
        }
    }

    /**
     * Play sound for the dot/point
     */
    fun playDot() {
        dotSoundId?.let { soundId ->
            queueSound(soundId)
        }
    }

    /**
     * Play sound for equals
     */
    fun playEquals() {
        equalsSoundId?.let { soundId ->
            queueSound(soundId)
        }
    }

    private fun queueSound(soundId: Int) {
        soundQueue.add(soundId)
        if (!isPlaying) {
            playNext()
        }
    }

    private fun playNext() {
        if (soundQueue.isEmpty()) {
            isPlaying = false
            return
        }
        
        isPlaying = true
        val soundId = soundQueue.removeFirst()
        
        // Play the sound
        playSoundId(soundId)
        
        // Schedule next sound after this one finishes
        // We use the duration of the sound to determine the delay
        val duration = soundDurations[soundId] ?: 300L // Default to 300ms if unknown
        
        // Adjust duration for playback rate
        val delay = (duration / PLAYBACK_RATE).toLong()
        
        handler.postDelayed({
            playNext()
        }, delay)
    }
    
    /**
     * Play a sound by its sound ID
     */
    private fun playSoundId(soundId: Int) {
        try {
            soundPool?.play(
                soundId,
                1.0f,  // left volume
                1.0f,  // right volume
                1,     // priority
                0,     // loop (0 = no loop)
                PLAYBACK_RATE   // playback rate
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error playing sound: ${e.message}")
        }
    }
    
    /**
     * Release all resources
     * Should be called when the sound player is no longer needed
     */
    fun release() {
        try {
            handler.removeCallbacksAndMessages(null)
            soundQueue.clear()
            isPlaying = false
            soundPool?.release()
            soundPool = null
            numberSounds.clear()
            operatorSounds.clear()
            soundDurations.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing sound pool: ${e.message}")
        }
    }
}
