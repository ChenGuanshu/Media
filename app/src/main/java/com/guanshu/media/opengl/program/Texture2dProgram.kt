package com.guanshu.media.opengl.program

import com.guanshu.media.opengl.ImageTextureProgram
import com.guanshu.media.opengl.abstraction.Program

class Texture2dProgram : Program(
    ImageTextureProgram.VERTEX_SHADER,
    ImageTextureProgram.FRAGMENT_SHADER,
) {

    val aPositionHandle by lazy { getAttrib("aPosition") }
    val mvpMatrixHandle by lazy { getUniform("uMVPMatrix") }
    val aTextureHandle by lazy { getAttrib("aTextureCoord") }
    val stMatrixHandle by lazy { getUniform("uSTMatrix") }
    val sTextureHandle by lazy { getUniform("sTexture") }
}