package com.guanshu.media.opengl.filters

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Size
import com.guanshu.media.opengl.FLOAT_SIZE_BYTES
import com.guanshu.media.opengl.TextureData
import com.guanshu.media.opengl.abstraction.VertexBuffer
import com.guanshu.media.opengl.checkGlError
import com.guanshu.media.opengl.matrixReset
import com.guanshu.media.opengl.newMatrix
import com.guanshu.media.opengl.newTexture
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

    private lateinit var vbo: VertexBuffer
    private val mvpMatrix = newMatrix()

    private val myProgram = program as SmartTextureProgram

    private val fakeOesTexture by lazy {
        newTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
    }
    private val fake2DTexture by lazy {
        newTexture(GLES20.GL_TEXTURE_2D, 1, 1)
    }

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
        val slot = 0
        textureData.bind(slot)
        mvpMatrix.matrixReset()
        updateTransformMatrix(mvpMatrix, textureData.resolution, viewResolution)

        clear()
        program.use()
        vbo.bind()

        myProgram.aPosition.bindAtrribPointer(3, 5 * Float.SIZE_BYTES, 0)
        myProgram.aTextureCoord.bindAtrribPointer(2, 5 * Float.SIZE_BYTES, 3 * FLOAT_SIZE_BYTES)
        myProgram.mvpMatrix.bindUniform(1, mvpMatrix, 0)
        myProgram.stMatrix.bindUniform(1, textureData.matrix, 0)
        if (textureData.textureType == GLES20.GL_TEXTURE_2D) {
            myProgram.textureIndex.bindUniform(1)
            myProgram.texture2d.bindUniform(slot)

            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, fakeOesTexture)
            GLES20.glActiveTexture(slot + 1)
            myProgram.textureOes.bindUniform(slot + 1)
        } else {
            myProgram.textureIndex.bindUniform(0)
//            myProgram.texture2d.bindUniform(100)
            myProgram.textureOes.bindUniform(slot)
        }

        checkGlError("before glDrawArrays")
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")

        textureData.unbind()
        vbo.unbind()
    }
}