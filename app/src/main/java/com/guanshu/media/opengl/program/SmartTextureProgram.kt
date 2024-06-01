package com.guanshu.media.opengl.program

import com.guanshu.media.opengl.abstraction.Program

private const val VERTEX_SHADER = """
                #version 300 es  
    
                uniform mat4 uMVPMatrix;  
                uniform mat4 uSTMatrix;  
                in vec4 aPosition;  
                in vec4 aTextureCoord;  
                out vec2 vTextureCoord;
  
                void main() {  
                    gl_Position = uMVPMatrix * aPosition;  
                    vTextureCoord = (uSTMatrix * aTextureCoord).xy;  
                }
                """

private const val FRAGMENT_SHADER = """
                #version 300 es  
                #extension GL_OES_EGL_image_external_essl3 : require
                precision mediump float;
                
                uniform samplerExternalOES uTextureOES;
                uniform sampler2D uTexture2D;  
                uniform int uTextureIndex;  
                in vec2 vTextureCoord;
                out vec4 vFragColor;  
  
                void main() {  
                    vec4 color;
                    if (uTextureIndex == 0){
                       color = texture(uTextureOES, vTextureCoord);  
                    } else if (uTextureIndex == 1){
                       color = texture(uTexture2D, vTextureCoord);  
                    } else {
                       color = vec4(1.0, 0.0, 0.0, 0.0);
                    }
                    vFragColor = color;
                }
                """

/**
 * TODO this is not working
 */
class SmartTextureProgram : Program(
    VERTEX_SHADER,
    FRAGMENT_SHADER,
) {

    val mvpMatrix by lazy { getUniform("uMVPMatrix") }
    val stMatrix by lazy { getUniform("uSTMatrix") }
    val aPosition by lazy { getAttrib("aPosition") }
    val aTextureCoord by lazy { getAttrib("aTextureCoord") }
    val textureOes by lazy { getUniform("uTextureOES") }
    val texture2d by lazy { getUniform("uTexture2D") }
    val textureIndex by lazy { getUniform("uTextureIndex") }
}