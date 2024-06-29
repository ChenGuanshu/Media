package com.guanshu.media.opengl

import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Size
import com.guanshu.media.utils.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

private const val TAG = "OpenGL"

const val FLOAT_SIZE_BYTES = 4
const val INT_SIZE_BYTES = 4

fun fence() = GLES30.glFenceSync(GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
fun Long.waitFence() {
    GLES30.glWaitSync(this, 0, GLES30.GL_TIMEOUT_IGNORED)
    GLES30.glDeleteSync(this)
}

fun newMatrix(): FloatArray {
    val ret = FloatArray(16)
    Matrix.setIdentityM(ret, 0)
    return ret
}

fun FloatArray.matrixReset() = Matrix.setIdentityM(this, 0)

fun FloatArray.flipVertical() {
    this[5] = -this[5]
    this[13] += 1f //基于y=0.5进行翻转
}

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
fun Int.getAttribLocation(name: String): Int {
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
    textureTarget: Int = GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
    width: Int = -1,
    height: Int = -1,
): Int {
    val textures = IntArray(1)
    newTexture(textures, textureTarget, width, height)
    return textures[0]
}

fun newTexture(
    textures: IntArray,
    textureTarget: Int = GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
    width: Int = -1,
    height: Int = -1,
) {
    checkGlError("before glGenTextures")
    GLES20.glGenTextures(textures.size, textures, 0)
    checkGlError("glGenTextures ${textures.contentToString()}")

    Logger.d(
        TAG,
        "newTexture, textures=${textures.contentToString()}, type=$textureTarget, size=$width*$height"
    )

    textures.forEach { textureId ->
        GLES20.glBindTexture(textureTarget, textureId)
        checkGlError("glBindTexture $textureId")

        if (textureTarget == GLES20.GL_TEXTURE_2D && width != -1) {
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

fun readToBitmap(resolution: Size) = readToBitmap(resolution.width, resolution.height)

fun readToBitmap(width: Int, height: Int): Bitmap {
    val buf = ByteBuffer.allocateDirect(width * height * 4)
    buf.order(ByteOrder.LITTLE_ENDIAN)
    GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
    buf.rewind()

    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bmp.copyPixelsFromBuffer(buf)
    return bmp
}

fun newFbo(): Int {
    val fbo = IntArray(1)
    GLES20.glGenFramebuffers(1, fbo, 0)
    return fbo[0]
}

fun bindFbo(fbo: Int, texture: Int) {
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
    GLES20.glFramebufferTexture2D(
        GLES20.GL_FRAMEBUFFER,
        GLES20.GL_COLOR_ATTACHMENT0,
        GLES20.GL_TEXTURE_2D,
        texture,
        0,
    )
}

fun unbindFbo() {
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_NONE)
}

fun FloatArray.toFloatBuffer(): FloatBuffer {
    val vertexBuffer = ByteBuffer.allocateDirect(this.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    vertexBuffer.put(this).position(0)
    return vertexBuffer
}

fun IntArray.toIntBuffer(): IntBuffer {
    val vertexBuffer = ByteBuffer.allocateDirect(this.size * Int.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asIntBuffer()
    vertexBuffer.put(this).position(0)
    return vertexBuffer
}