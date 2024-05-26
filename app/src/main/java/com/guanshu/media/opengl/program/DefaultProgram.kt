package com.guanshu.media.opengl.program

import com.guanshu.media.opengl.abstraction.Program

private const val VERTEX_SHADER = """
#version 300 es
layout (location = 0) in vec4 vPosition;
layout (location = 1) in vec4 aColor;
uniform mat4 mvpMatrix;
out vec4 vColor;
void main() {
     gl_Position  = mvpMatrix * vPosition;
     vColor = aColor;
}
"""

private const val FRAGMENT_SHADER = """
#version 300 es
precision mediump float; //声明float型变量的精度为mediump
in vec4 vColor;
out vec4 fragColor;
void main() {
     fragColor = vColor;
}
"""

class DefaultProgram : Program(
    VERTEX_SHADER,
    FRAGMENT_SHADER,
) {

    val positionHandle by lazy { getAttrib("vPosition") }
    val colorHandle by lazy { getAttrib("aColor") }
    val matrixHandle by lazy { getUniform("mvpMatrix") }

}