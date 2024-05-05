package com.guanshu.media.opengl

import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.guanshu.media.utils.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
        Logger.e(TAG, "Could not compile shader $shaderType:")
        Logger.e(TAG, " " + GLES20.glGetShaderInfoLog(shader))
        GLES20.glDeleteShader(shader)
        shader = 0
    }
    return shader
}

fun createProgram(vertexSource: String, fragmentSource: String): Int {
    val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
    if (vertexShader == 0) {
        Logger.w(TAG, "load vertexShader:${GLES20.glGetShaderInfoLog(vertexShader)}")
        Logger.w(TAG, "failed to load vertex shader $vertexSource")
        return 0
    }

    val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
    if (pixelShader == 0) {
        Logger.w(TAG, "load pixelShader:${GLES20.glGetShaderInfoLog(pixelShader)}")
        Logger.w(TAG, "failed to load fragment shader $fragmentSource")
        return 0
    }

    var program = GLES20.glCreateProgram()
    checkGlError("glCreateProgram")
    if (program == 0) {
        Logger.e(TAG, "Could not create program")
    }
    GLES20.glAttachShader(program, vertexShader)
    checkGlError("glAttachShader")
    GLES20.glAttachShader(program, pixelShader)
    checkGlError("glAttachShader")
    GLES20.glLinkProgram(program)
    checkGlError("glLinkProgram")

    val linkStatus = IntArray(1)
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
    if (linkStatus[0] != GLES20.GL_TRUE) {
        Logger.e(TAG, "Could not link program: ")
        Logger.e(TAG, GLES20.glGetProgramInfoLog(program))
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
        Logger.e(TAG, "$op: glError $error")
        throw RuntimeException("$op: glError $error")
    }
}

fun newTexture(
    textures: IntArray,
    textureTarget: Int = GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
    width: Int = -1,
    height: Int = -1,
) {
    GLES20.glGenTextures(textures.size, textures, 0)
    textures.forEach { textureId ->
        GLES20.glBindTexture(textureTarget, textureId)
        checkGlError("glBindTexture mTextureID")

        if (textureTarget == GLES20.GL_TEXTURE_2D && width != -1) {
            Logger.d(TAG, "newTexture,glTexImage2D from $width")
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height,
                0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
            )
        }

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

fun readToBitmap(width: Int, height: Int): Bitmap {
    val buf = ByteBuffer.allocateDirect(width * height * 4)
    buf.order(ByteOrder.LITTLE_ENDIAN)
    GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
    buf.rewind()

    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bmp.copyPixelsFromBuffer(buf)
    return bmp
}