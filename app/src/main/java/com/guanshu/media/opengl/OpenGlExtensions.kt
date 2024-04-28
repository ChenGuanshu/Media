package com.guanshu.media.opengl

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log

private const val TAG = "OpenGL"

const val FLOAT_SIZE_BYTES = 4
const val INT_SIZE_BYTES = 4

private fun loadShader(shaderType: Int, source: String): Int {
    var shader = GLES20.glCreateShader(shaderType)
    checkGlError("glCreateShader type=$shaderType")
    GLES20.glShaderSource(shader, source)
    GLES20.glCompileShader(shader)
    val compiled = IntArray(1)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
    if (compiled[0] == 0) {
        Log.e(TAG, "Could not compile shader $shaderType:")
        Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader))
        GLES20.glDeleteShader(shader)
        shader = 0
    }
    return shader
}

fun createProgram(vertexSource: String, fragmentSource: String): Int {
    val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
    if (vertexShader == 0) {
        return 0
    }
    val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
    if (pixelShader == 0) {
        return 0
    }
    var program = GLES20.glCreateProgram()
    checkGlError("glCreateProgram")
    if (program == 0) {
        Log.e(TAG, "Could not create program")
    }
    GLES20.glAttachShader(program, vertexShader)
    checkGlError("glAttachShader")
    GLES20.glAttachShader(program, pixelShader)
    checkGlError("glAttachShader")
    GLES20.glLinkProgram(program)
    val linkStatus = IntArray(1)
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
    if (linkStatus[0] != GLES20.GL_TRUE) {
        Log.e(TAG, "Could not link program: ")
        Log.e(TAG, GLES20.glGetProgramInfoLog(program))
        GLES20.glDeleteProgram(program)
        program = 0
    }
    return program
}

// this = program id
fun Int.getAtrribLocation(name: String): Int {
    val ret = GLES20.glGetAttribLocation(this, name)
    checkGlError("glGetAttribLocation $name")
    if (ret == -1) {
        throw RuntimeException("Could not get attrib location for $name")
    }
    return ret
}

// this = program id
fun Int.getUniformLocation(name: String): Int {
    val ret = GLES20.glGetUniformLocation(this, name)
    checkGlError("glGetUniformLocation $name")
    if (ret == -1) {
        throw RuntimeException("Could not get uniform location for $name")
    }
    return ret
}

fun checkGlError(op: String) {
    var error: Int
    while (GLES20.glGetError().also { error = it } != GLES20.GL_NO_ERROR) {
        Log.e(TAG, "$op: glError $error")
        throw RuntimeException("$op: glError $error")
    }
}

fun newTexture(textures: IntArray, textureTarget: Int = GLES11Ext.GL_TEXTURE_EXTERNAL_OES) {
    GLES20.glGenTextures(textures.size, textures, 0)
    textures.forEach { textureId ->
        GLES20.glBindTexture(textureTarget, textureId)
        checkGlError("glBindTexture mTextureID")
        GLES20.glTexParameterf(
            textureTarget, GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST.toFloat()
        )
        GLES20.glTexParameterf(
            textureTarget, GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )
        GLES20.glTexParameteri(
            textureTarget, GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            textureTarget, GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        checkGlError("glTexParameter")
    }
}