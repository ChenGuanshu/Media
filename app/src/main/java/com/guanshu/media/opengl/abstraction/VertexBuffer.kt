package com.guanshu.media.opengl.abstraction

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

// VBO
class VertexBuffer {

    private val id: Int

    init {
        val intArray = IntArray(1)
        GLES20.glGenBuffers(1, intArray, 0)
        id = intArray[0]
        bind()
    }

    fun bind() = GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, id)
    fun unbind() = GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

    fun addBuffer(bufferData: FloatArray, drawMode:Int = GLES20.GL_STATIC_DRAW) {
        val size = bufferData.size * Float.SIZE_BYTES
        val buffer = ByteBuffer.allocateDirect(size)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(bufferData).position(0)

        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            size,
            buffer,
            drawMode,
        )
    }
}