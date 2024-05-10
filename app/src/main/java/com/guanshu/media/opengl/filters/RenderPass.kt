package com.guanshu.media.opengl.filters

import android.util.Size
import com.guanshu.media.opengl.TextureData

// TODO
interface RenderPass {
    fun init()
    fun render(
        textureDatas: List<TextureData>,
        viewResolution: Size,
    )
}