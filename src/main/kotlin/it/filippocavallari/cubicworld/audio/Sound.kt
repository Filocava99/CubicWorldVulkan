package it.filippocavallari.cubicworld.audio

import org.lwjgl.openal.AL10.*
import org.lwjgl.stb.STBVorbis
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer

/**
 * Represents a sound that can be played by the audio system.
 * Loads and manages audio data for playback.
 */
class Sound(filePath: String) {
    
    private var bufferId = 0
    
    init {
        try {
            // Load sound file
            val (data, channels, sampleRate) = loadAudioData(filePath)
            
            // Create OpenAL buffer
            bufferId = alGenBuffers()
            
            // Set buffer data
            val format = if (channels == 1) AL_FORMAT_MONO16 else AL_FORMAT_STEREO16
            alBufferData(bufferId, format, data, sampleRate)
            
            // Free audio data
            MemoryUtil.memFree(data)
        } catch (e: Exception) {
            throw IOException("Failed to load sound: $filePath", e)
        }
    }
    
    /**
     * Gets the OpenAL buffer ID for this sound.
     * 
     * @return The buffer ID
     */
    fun getBufferId(): Int {
        return bufferId
    }
    
    /**
     * Cleans up the sound resources.
     */
    fun cleanup() {
        if (bufferId != 0) {
            alDeleteBuffers(bufferId)
            bufferId = 0
        }
    }
    
    /**
     * Loads audio data from a file.
     * 
     * @param filePath The path to the audio file
     * @return A triple of (data, channels, sampleRate)
     */
    private fun loadAudioData(filePath: String): Triple<ShortBuffer, Int, Int> {
        val classLoader = javaClass.classLoader
        val url = classLoader.getResource(filePath)
            ?: throw IOException("Audio file not found: $filePath")
        
        MemoryStack.stackPush().use { stack ->
            val channelsBuffer = stack.mallocInt(1)
            val sampleRateBuffer = stack.mallocInt(1)
            
            // Load OGG file
            val audioBuffer = if (filePath.endsWith(".ogg", ignoreCase = true)) {
                loadOgg(url.openStream().readBytes(), channelsBuffer, sampleRateBuffer)
            } else {
                throw UnsupportedOperationException("Unsupported audio format: $filePath")
            }
            
            return Triple(audioBuffer, channelsBuffer.get(0), sampleRateBuffer.get(0))
        }
    }
    
    /**
     * Loads audio data from an OGG file.
     * 
     * @param data The OGG file data
     * @param channelsBuffer Buffer to store the number of channels
     * @param sampleRateBuffer Buffer to store the sample rate
     * @return The audio data as a ShortBuffer
     */
    private fun loadOgg(data: ByteArray, channelsBuffer: IntBuffer, sampleRateBuffer: IntBuffer): ShortBuffer {
        val byteBuffer = MemoryUtil.memAlloc(data.size)
            .put(data)
            .flip() as ByteBuffer
        
        val error = MemoryStack.stackPush().use { stack ->
            stack.mallocInt(1)
        }
        
        val audioBuffer = STBVorbis.stb_vorbis_decode_memory(byteBuffer, channelsBuffer, sampleRateBuffer)
            ?: throw IOException("Failed to load OGG file")
        
        // Free the byte buffer
        MemoryUtil.memFree(byteBuffer)
        
        return audioBuffer
    }
}