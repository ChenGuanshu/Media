package com.guanshu.media.opengl.abstraction

import android.opengl.GLES20
import com.guanshu.media.opengl.createProgram
import com.guanshu.media.opengl.getAtrribLocation
import com.guanshu.media.opengl.getUniformLocation

open class Program(
    private val vertexShader: String,
    private val fragmentShader: String,
) {

    private var id: Int = -1

    fun init() {
        id = createProgram(vertexShader, fragmentShader)
        if (id == 0) {
            val str = GLES20.glGetProgramInfoLog(id)
            throw RuntimeException("failed creating program:$str")
        }
    }

    fun getAtrribLocation(name: String) = id.getAtrribLocation(name)
    fun getUniformLocation(name: String) = id.getUniformLocation(name)

    fun use() = GLES20.glUseProgram(id)

}