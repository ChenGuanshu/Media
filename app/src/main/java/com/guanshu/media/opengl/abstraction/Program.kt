package com.guanshu.media.opengl.abstraction

import android.opengl.GLES20
import com.guanshu.media.opengl.checkGlError
import com.guanshu.media.opengl.createProgram
import com.guanshu.media.opengl.getAttribLocation
import com.guanshu.media.opengl.getUniformLocation

open class Program(
    private val vertexShader: String,
    private val fragmentShader: String,
) {

    private var id: Int = -1

    fun init() {
        id = createProgram(vertexShader, fragmentShader)
        checkGlError("glCreateProgram")
        if (id == 0) {
            val str = GLES20.glGetProgramInfoLog(id)
            throw RuntimeException("failed creating program:$str")
        }
    }

    fun getAttrib(name: String) = Attribute(getAttribLocation(name))

    @Deprecated("soon")
    fun getAttribLocation(name: String) = id.getAttribLocation(name)
    fun getUniform(name: String) = Uniform(getUniformLocation(name))


    @Deprecated("soon")
    fun getUniformLocation(name: String) = id.getUniformLocation(name)

    fun use() = GLES20.glUseProgram(id)

    class Attribute(private val id: Int) {
        fun bindAtrribPointer(
            size: Int,
            stride: Int,
            offset: Int,
            type: Int = GLES20.GL_FLOAT,
        ) {
            GLES20.glVertexAttribPointer(
                id,
                size,
                type,
                false,
                stride,
                offset,
            )
            GLES20.glEnableVertexAttribArray(id)
        }
    }

    class Uniform(private val id: Int) {
        fun bindUniform(
            count: Int,
            matrix: FloatArray,
            offset: Int
        ) {
            GLES20.glUniformMatrix4fv(id, count, false, matrix, offset)
        }
    }
}