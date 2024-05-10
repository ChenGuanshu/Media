package com.guanshu.media.opengl.filters

import android.opengl.GLES20
import android.util.Size
import com.guanshu.media.opengl.TextureData
import com.guanshu.media.opengl.abstraction.Program
import com.guanshu.media.opengl.checkGlError

private const val TAG = "BaseFilter"

abstract class BaseFilter(
    val program: Program
) : RenderPass {

    constructor(
        vertexShader: String,
        fragmentShader: String
    ) : this(Program(vertexShader, fragmentShader))

    override fun init() {
        program.init()
        checkGlError("BaseFilter:init")
    }

    fun clear() {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)
    }

    abstract override fun render(
        textureDatas: List<TextureData>,
        viewResolution: Size,
    )
}