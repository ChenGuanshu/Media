package com.guanshu.media.opengl

object OesTextureProgram {
    // mat4: 4x4 矩阵
    // vec4: 向量4
    // aPosition 顶点坐标 -> uMVPMatrix 旋转拉伸
    // aTextureCoord 纹理顶点 -> uSTMatrix 旋转拉伸
    // varying vTextureCoord: 意味着vTextureCoord 会被光栅化插值
    const val VERTEX_SHADER = """
                uniform mat4 uMVPMatrix;
                uniform mat4 uSTMatrix;
                attribute vec4 aPosition;
                attribute vec4 aTextureCoord;
                varying vec2 vTextureCoord;
                void main() {
                  gl_Position = uMVPMatrix * aPosition;
                  vTextureCoord = (uSTMatrix * aTextureCoord).xy;
                }
                """

    // 1. vTextureCoord 是插值过的片元坐标, texture2D是一个vec4:rgba
    // 2. gl_FragColor可以看成是对应 gl_Position的颜色计算
    // 3. 如果当前的渲染只需要一个纹理单元的情况下，OpenGL会默认我们使用的是第一个纹理单元. 正常来说 sTexture
    // 需要与纹理单元绑定
    const val FRAGMENT_SHADER = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTextureCoord;
                uniform samplerExternalOES sTexture;
                void main() {
                    gl_FragColor = texture2D(sTexture, vTextureCoord);
                }
                """
}

object ImageTextureProgram {
    const val VERTEX_SHADER = OesTextureProgram.VERTEX_SHADER
    const val FRAGMENT_SHADER = """
                precision mediump float;
                varying vec2 vTextureCoord;
                uniform sampler2D sTexture;
                void main() {
                    gl_FragColor = texture2D(sTexture, vTextureCoord);
                }
                """
}