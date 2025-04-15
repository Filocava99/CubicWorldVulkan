package it.filippocavallari.cubicworld.audio

import org.lwjgl.openal.AL10.*

/**
 * Handles music playback for the game.
 * Manages a single background music track.
 */
class MusicPlayer {
    
    // Music source ID
    private var sourceId = 0
    
    // Current music
    private var currentMusic: Sound? = null
    private var currentFilePath: String? = null
    
    // Playback state
    private var volume = 1.0f
    private var loop = true
    private var playing = false
    private var paused = false
    
    init {
        // Create a source for music
        sourceId = alGenSources()
        
        // Set source properties
        alSourcef(sourceId, AL_GAIN, volume)
        alSourcei(sourceId, AL_LOOPING, if (loop) AL_TRUE else AL_FALSE)
    }
    
    /**
     * Plays a music track.
     * 
     * @param filePath The path to the music file
     * @param volume The volume of the music (0.0-1.0)
     * @param loop Whether to loop the music
     */
    fun play(filePath: String, volume: Float = 1.0f, loop: Boolean = true) {
        // Stop current music if playing
        if (playing) {
            stop()
        }
        
        try {
            // Load new music if it's different from the current one
            if (currentFilePath != filePath) {
                // Clean up old music
                currentMusic?.cleanup()
                
                // Load new music
                currentMusic = Sound(filePath)
                currentFilePath = filePath
            }
            
            // Set source properties
            alSourcei(sourceId, AL_BUFFER, currentMusic?.getBufferId() ?: 0)
            
            this.volume = volume.coerceIn(0f, 1f)
            this.loop = loop
            
            alSourcef(sourceId, AL_GAIN, this.volume)
            alSourcei(sourceId, AL_LOOPING, if (this.loop) AL_TRUE else AL_FALSE)
            
            // Play music
            alSourcePlay(sourceId)
            
            playing = true
            paused = false
        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed to play music: $filePath")
        }
    }
    
    /**
     * Stops the current music.
     */
    fun stop() {
        if (!playing) return
        
        alSourceStop(sourceId)
        playing = false
        paused = false
    }
    
    /**
     * Pauses the current music.
     */
    fun pause() {
        if (!playing || paused) return
        
        alSourcePause(sourceId)
        paused = true
    }
    
    /**
     * Resumes the paused music.
     */
    fun resume() {
        if (!playing || !paused) return
        
        alSourcePlay(sourceId)
        paused = false
    }
    
    /**
     * Sets the volume of the music.
     * 
     * @param volume The volume (0.0-1.0)
     */
    fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        alSourcef(sourceId, AL_GAIN, this.volume)
    }
    
    /**
     * Updates the music player.
     * Should be called once per frame.
     */
    fun update() {
        // Check if music has stopped playing (and not paused)
        if (playing && !paused) {
            val state = alGetSourcei(sourceId, AL_SOURCE_STATE)
            
            if (state != AL_PLAYING) {
                // Music has stopped playing
                playing = false
                
                // Restart if looping
                if (loop) {
                    alSourcePlay(sourceId)
                    playing = true
                }
            }
        }
    }
    
    /**
     * Cleans up the music player resources.
     */
    fun cleanup() {
        stop()
        
        if (sourceId != 0) {
            alDeleteSources(sourceId)
            sourceId = 0
        }
        
        currentMusic?.cleanup()
        currentMusic = null
        currentFilePath = null
    }
}