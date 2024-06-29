package com.guanshu.media.opengl.filters

import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Size
import com.guanshu.media.opengl.FLOAT_SIZE_BYTES
import com.guanshu.media.opengl.TextureData
import com.guanshu.media.opengl.abstraction.VertexBuffer
import com.guanshu.media.opengl.checkGlError
import com.guanshu.media.opengl.matrixReset
import com.guanshu.media.opengl.newMatrix
import com.guanshu.media.opengl.program.ExternalTextureProgram
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

private const val TAG = "SingleTextureFilter"

/**
 * 渲染texture
 */
class SingleTextureFilter : BaseFilter(ExternalTextureProgram()) {

    private lateinit var vertexBuffer: VertexBuffer
    private val mvpMatrix = newMatrix()
    private val exProgram = program as ExternalTextureProgram

    override fun init() {
        super.init()
        Logger.i(TAG, "call init")
        vertexBuffer = VertexBuffer()
        vertexBuffer.addBuffer(verticesData)
        vertexBuffer.unbind()
        checkGlError("vertexBuffer")
    }

    override fun render(
        textureDatas: List<TextureData>,
        viewResolution: Size,
    ) {
        val textureData = textureDatas.first()
        mvpMatrix.matrixReset()
        updateTransformMatrix(mvpMatrix, textureData.resolution, viewResolution)

        clear()
        program.use()
        textureData.bind()
        vertexBuffer.bind()
        exProgram.aPositionHandle.bindAtrribPointer(3, 5 * Float.SIZE_BYTES, 0)
        exProgram.aTextureHandle.bindAtrribPointer(2, 5 * Float.SIZE_BYTES, 3 * FLOAT_SIZE_BYTES)
        exProgram.mvpMatrixHandle.bindUniform(1, mvpMatrix, 0)
        exProgram.stMatrixHandle.bindUniform(1, textureData.matrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")
        textureData.unbind()
        vertexBuffer.unbind()
    }
}