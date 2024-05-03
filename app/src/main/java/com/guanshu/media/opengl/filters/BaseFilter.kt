package com.guanshu.media.opengl.filters

import android.util.Log
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

        Logger.i(TAG, "init")
        program = createProgram(vertexShader, fragmentShader)
        if (program == 0) {
            throw RuntimeException("failed creating program")
        }
    }

    abstract fun render(
        textureDatas: List<TextureData>,
        viewResolution: Size,
    )
}