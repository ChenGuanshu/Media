package com.guanshu.media.opengl.filters

import android.opengl.GLES20
import android.util.Size
import com.guanshu.media.opengl.TextureData
import com.guanshu.media.opengl.createProgram
import com.guanshu.media.utils.Logger

private const val TAG = "BaseFilter"

abstract class BaseFilter(
    private val vertexShader: String,
    private val fragmentShader: String,
) {

    private var init = false

    var program = 0

    open fun init() {
        if (init) return
        init = true

        Logger.d(TAG, "init")
        program = createProgram(vertexShader, fragmentShader)
        if (program == 0) {
            val str = GLES20.glGetProgramInfoLog(program)
            throw RuntimeException("failed creating program:$str")
        }
    }

    abstract fun render(
        textureDatas: List<TextureData>,
        viewResolution: Size,
    )
}