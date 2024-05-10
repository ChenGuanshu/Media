package com.guanshu.media.opengl.filters

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

    abstract override fun render(
        textureDatas: List<TextureData>,
        viewResolution: Size,
    )
}