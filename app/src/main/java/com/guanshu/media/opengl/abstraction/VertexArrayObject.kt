package com.guanshu.media.opengl.abstraction

import android.opengl.GLES30

class VertexArrayObject {

    private val id: Int

    init {
        val intArray = IntArray(1)
        GLES30.glGenVertexArrays(1, intArray, 0)
        id = intArray[0]
        bind()
    }

    fun bind() = GLES30.glBindVertexArray(id)
    fun unbind() = GLES30.glBindVertexArray(0)
}