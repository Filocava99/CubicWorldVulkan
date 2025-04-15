package it.filippocavallari.cubicworld.audio

import org.lwjgl.openal.AL
import org.lwjgl.openal.AL10.*
import org.lwjgl.openal.ALC
import org.lwjgl.openal.ALC10.*
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.IntBuffer

/**
 * Audio system for the game engine.
 * Manages sound effects and music playback using OpenAL.
 */
object AudioSystem {
    
    // OpenAL device and context
    private var device: Long = 0
    private var context: Long = 0
    
    // Loaded sounds
    private val sounds = mutableMapOf<String, Sound>()
    
    // Music player
    private var musicPlayer: MusicPlayer? = null
    
    // System settings
    private var masterVolume = 1.0f
    private var soundVolume = 1.0f
    private var musicVolume = 1.0f
    private var enabled = true
    
    /**
     * Initializes the audio system.
     */
    fun init() {
        if (!enabled) return
        
        try {
            // Open default audio device
            device = alcOpenDevice(null as ByteBuffer?)
            if (device == 0L) {
                println("Failed to open audio device. Audio will be disabled.")
                enabled = false
                return
            }
            
            // Create OpenAL context
            context = alcCreateContext(device, null as IntBuffer?)
            if (context == 0L) {
                println("Failed to create OpenAL context. Audio will be disabled.")
                alcCloseDevice(device)
                enabled = false
                return
            }
            
            // Make context current
            alcMakeContextCurrent(context)
            
            // Initialize OpenAL
            AL.createCapabilities(ALC.createCapabilities(device))
            
            // Set listener properties
            alListener3f(AL_POSITION, 0f, 0f, 0f)
            alListener3f(AL_VELOCITY, 0f, 0f, 0f)
            
            // Initialize music player
            musicPlayer = MusicPlayer()
            
            println("Audio system initialized")
        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed to initialize audio system. Audio will be disabled.")
            enabled = false
            cleanup()
        }
    }
    
    /**
     * Loads a sound from a file.
     * 
     * @param name The name to identify the sound
     * @param filePath The path to the sound file
     * @return True if the sound was loaded successfully
     */
    fun loadSound(name: String, filePath: String): Boolean {
        if (!enabled) return false
        
        // Don't reload if already loaded
        if (sounds.containsKey(name)) {
            return true
        }
        
        try {
            val sound = Sound(filePath)
            sounds[name] = sound
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed to load sound: $filePath")
            return false
        }
    }
    
    /**
     * Plays a sound by name.
     * 
     * @param name The name of the sound to play
     * @param volume The volume of the sound (0.0-1.0)
     * @param pitch The pitch of the sound (0.5-2.0)
     * @param loop Whether to loop the sound
     * @return The source ID of the sound, or -1 if failed
     */
    fun playSound(name: String, volume: Float = 1.0f, pitch: Float = 1.0f, loop: Boolean = false): Int {
        if (!enabled) return -1
        
        val sound = sounds[name] ?: return -1
        
        try {
            // Create a source
            val sourceId = alGenSources()
            
            // Link buffer to source
            alSourcei(sourceId, AL_BUFFER, sound.getBufferId())
            
            // Set source properties
            alSourcef(sourceId, AL_GAIN, volume * soundVolume * masterVolume)
            alSourcef(sourceId, AL_PITCH, pitch)
            alSourcei(sourceId, AL_LOOPING, if (loop) AL_TRUE else AL_FALSE)
            
            // Play the source
            alSourcePlay(sourceId)
            
            return sourceId
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
    }
    
    /**
     * Stops a sound source.
     * 
     * @param sourceId The ID of the source to stop
     */
    fun stopSound(sourceId: Int) {
        if (!enabled || sourceId <= 0) return
        
        try {
            alSourceStop(sourceId)
            alDeleteSources(sourceId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Plays music from a file.
     * 
     * @param filePath The path to the music file
     * @param volume The volume of the music (0.0-1.0)
     * @param loop Whether to loop the music
     */
    fun playMusic(filePath: String, volume: Float = 1.0f, loop: Boolean = true) {
        if (!enabled) return
        
        musicPlayer?.play(filePath, volume * musicVolume * masterVolume, loop)
    }
    
    /**
     * Stops the currently playing music.
     */
    fun stopMusic() {
        if (!enabled) return
        
        musicPlayer?.stop()
    }
    
    /**
     * Pauses the currently playing music.
     */
    fun pauseMusic() {
        if (!enabled) return
        
        musicPlayer?.pause()
    }
    
    /**
     * Resumes the paused music.
     */
    fun resumeMusic() {
        if (!enabled) return
        
        musicPlayer?.resume()
    }
    
    /**
     * Sets the master volume.
     * 
     * @param volume The master volume (0.0-1.0)
     */
    fun setMasterVolume(volume: Float) {
        masterVolume = volume.coerceIn(0f, 1f)
        
        // Update music volume
        if (enabled) {
            musicPlayer?.setVolume(musicVolume * masterVolume)
        }
    }
    
    /**
     * Sets the sound effect volume.
     * 
     * @param volume The sound effect volume (0.0-1.0)
     */
    fun setSoundVolume(volume: Float) {
        soundVolume = volume.coerceIn(0f, 1f)
    }
    
    /**
     * Sets the music volume.
     * 
     * @param volume The music volume (0.0-1.0)
     */
    fun setMusicVolume(volume: Float) {
        musicVolume = volume.coerceIn(0f, 1f)
        
        // Update music volume
        if (enabled) {
            musicPlayer?.setVolume(musicVolume * masterVolume)
        }
    }
    
    /**
     * Updates the audio system.
     * Should be called once per frame.
     */
    fun update() {
        if (!enabled) return
        
        // Update music player
        musicPlayer?.update()
    }
    
    /**
     * Cleans up the audio system resources.
     */
    fun cleanup() {
        if (!enabled) return
        
        // Cleanup music player
        musicPlayer?.cleanup()
        
        // Cleanup sounds
        for (sound in sounds.values) {
            sound.cleanup()
        }
        sounds.clear()
        
        // Cleanup OpenAL
        if (context != 0L) {
            alcDestroyContext(context)
            context = 0
        }
        
        if (device != 0L) {
            alcCloseDevice(device)
            device = 0
        }
    }
}