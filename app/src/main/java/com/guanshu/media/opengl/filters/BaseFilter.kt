package com.guanshu.media.opengl.filters

import android.util.Log
import android.util.Size
import com.guanshu.media.opengl.createProgram

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

        Log.i(TAG, "init")
        program = createProgram(vertexShader, fragmentShader)
        if (program == 0) {
            throw RuntimeException("failed creating program")
        }
    }

    abstract fun render(
        textureId: Int,
        textMatrix: FloatArray,
        mediaResolution: Size,
        screenResolution: Size,
    )
}