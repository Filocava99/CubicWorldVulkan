package it.filippocavallari.cubicworld.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder

object BufferUtils {
    fun floatArrayToByteArray(floatArray: FloatArray): ByteArray {
        val byteBuffer = ByteBuffer.allocate(floatArray.size * 4) // 4 bytes per float
        byteBuffer.order(ByteOrder.nativeOrder()) // Use native order, assuming engine expects this
        val floatBuffer = byteBuffer.asFloatBuffer()
        floatBuffer.put(floatArray)
        return byteBuffer.array()
    }
}
