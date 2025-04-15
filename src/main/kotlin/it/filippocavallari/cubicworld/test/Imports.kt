package it.filippocavallari.cubicworld.test

import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import org.lwjgl.system.MemoryUtil

class TestImports {
    fun test() {
        val buffer: Buffer? = null
        val longBuf = MemoryUtil.memAllocLong(10)
    }
}