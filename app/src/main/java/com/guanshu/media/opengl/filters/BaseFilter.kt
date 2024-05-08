package com.guanshu.media.opengl.filters

import android.opengl.GLES20
import android.util.Size
import com.guanshu.media.opengl.TextureData
import com.guanshu.media.opengl.createProgram
import com.guanshu.media.utils.Logger

private const val TAG = "BaseFilter"

interface Filter {
    fun init()
    fun render(
        textureDatas: List<TextureData>,
        viewResolution: Size,
    )
}

abstract class BaseFilter(
    private val vertexShader: String,
    private val fragmentShader: String,
) : Filter {

    private var init = false

    var program = 0

    override fun init() {
        if (init) return
        init = true

        Logger.d(TAG, "init")
        program = createProgram(vertexShader, fragmentShader)
        if (program == 0) {
            val str = GLES20.glGetProgramInfoLog(program)
            throw RuntimeException("failed creating program:$str")
        }
    }

    abstract override fun render(
        textureDatas: List<TextureData>,
        viewResolution: Size,
    )
}