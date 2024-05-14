package com.guanshu.media.opengl.program

import com.guanshu.media.opengl.abstraction.Program

private const val VERTEX_SHADER = """
                #version 300 es  
    
                layout(location = 0) uniform mat4 uMVPMatrix;  
                layout(location = 1) uniform mat4 uSTMatrix;  
                layout(location = 2) in vec4 aPosition;  
                layout(location = 3) in vec4 aTextureCoord;  
//                layout(location = 4) in int aTextureIndex;
                
                out vec2 vTextureCoord;
//                out int vTextureIndex;
  
                void main() {  
                    gl_Position = uMVPMatrix * aPosition;  
                    vTextureCoord = (uSTMatrix * aTextureCoord).xy;  
                }
                """

private const val FRAGMENT_SHADER = """
                #version 300 es  
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                
//                layout(location = 5) uniform samplerExternalOES uTextureOES;
                layout(location = 4) uniform sampler2D uTexture2D;  
                
                in vec2 vTextureCoord;  
//                in int vTextureIndex;  
                out vec4 vFragColor;  
  
                void main() {  
                    vFragColor = texture(uTexture2D, vTextureCoord);  
                }
                """

class SmartTextureProgram : Program(
    VERTEX_SHADER,
    FRAGMENT_SHADER,
) {

    val mvpMatrix by lazy { getUniform(0) }
    val stMatrix by lazy { getUniform(1) }
    val aPosition by lazy { getAttrib(2) }
    val aTextureCoord by lazy { getAttrib(3) }
//    val aTextureIndex by lazy { getAttrib(4) }
//
//    val textureOes by lazy { getUniform(5) }
    val texture2d by lazy { getUniform(4) }
}