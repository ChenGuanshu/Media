package com.guanshu.media.opengl.filters

import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Size
import com.guanshu.media.opengl.FLOAT_SIZE_BYTES
import com.guanshu.media.opengl.TextureData
import com.guanshu.media.opengl.abstraction.VertexArrayObject
import com.guanshu.media.opengl.abstraction.VertexBuffer
import com.guanshu.media.opengl.checkGlError
import com.guanshu.media.opengl.matrixReset
import com.guanshu.media.opengl.newMatrix
import com.guanshu.media.opengl.program.ExternalTextureProgram
import com.guanshu.media.opengl.program.SmartTextureProgram
import com.guanshu.media.opengl.updateTransformMatrix
import com.guanshu.media.utils.Logger

// 顶点坐标和纹理坐标
private val verticesData = floatArrayOf(
    // X, Y, Z, U, V
    -1.0f, -1.0f, 0f, 0f, 0f,
    1.0f, -1.0f, 0f, 1f, 0f,
    -1.0f, 1.0f, 0f, 0f, 1f,
    1.0f, 1.0f, 0f, 1f, 1f,
)

private const val TAG = "SmartTextureFilter"

/**
 * 支持 oes/2d
 */
class SmartTextureFilter : BaseFilter(SmartTextureProgram()) {

//    private lateinit var vao: VertexArrayObject
    private lateinit var vbo: VertexBuffer
    private val mvpMatrix = newMatrix()

    private val myProgram = program as SmartTextureProgram

    override fun init() {
        super.init()
        Logger.i(TAG, "call init")
        checkGlError("init program")
        vbo = VertexBuffer()
        vbo.addBuffer(verticesData)
        vbo.unbind()
        checkGlError("unbind attribute")
    }

    override fun render(
        textureDatas: List<TextureData>,
        viewResolution: Size,
    ) {
        val textureData = textureDatas.first()
        textureData.bind(0)
        mvpMatrix.matrixReset()
        updateTransformMatrix(mvpMatrix, textureData.resolution, viewResolution)

        clear()
        program.use()
        textureData.bind()
        vbo.bind()

        myProgram.aPosition.bindAtrribPointer(3, 5 * Float.SIZE_BYTES, 0)
        myProgram.aTextureCoord.bindAtrribPointer(2, 5 * Float.SIZE_BYTES, 3 * FLOAT_SIZE_BYTES)
        myProgram.mvpMatrix.bindUniform(1, mvpMatrix, 0)
        myProgram.stMatrix.bindUniform(1, textureData.matrix, 0)
        myProgram.texture2d.bindUniform(0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")

        textureData.unbind()
        vbo.unbind()
    }
}