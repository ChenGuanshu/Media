package com.guanshu.media.opengl.filters

import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Size
import com.guanshu.media.opengl.FLOAT_SIZE_BYTES
import com.guanshu.media.opengl.ImageTextureProgram
import com.guanshu.media.opengl.OesTextureProgram
import com.guanshu.media.opengl.TextureData
import com.guanshu.media.opengl.abstraction.Program
import com.guanshu.media.opengl.bindFbo
import com.guanshu.media.opengl.checkGlError
import com.guanshu.media.opengl.newFbo
import com.guanshu.media.opengl.newTexture
import com.guanshu.media.opengl.readToBitmap
import com.guanshu.media.opengl.unbindFbo
import com.guanshu.media.opengl.updateTransformMatrix
import com.guanshu.media.utils.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder

// 顶点坐标和纹理坐标
private val verticesData = floatArrayOf(
    // X, Y, Z, U, V
    -1.0f, -1.0f, 0f, 0f, 0f,
    1.0f, -1.0f, 0f, 1f, 0f,
    -1.0f, 1.0f, 0f, 0f, 1f,
    1.0f, 1.0f, 0f, 1f, 1f,
)

private const val TAG = "SingleTextureFboFilter"

/**
 * 渲染texture
 */
class SingleTextureFboFilter : BaseFilter(
    OesTextureProgram.VERTEX_SHADER,
    OesTextureProgram.FRAGMENT_SHADER,
) {

    private lateinit var imageProgram: Program
    private val vertexVbos = IntArray(1)
    private val mvpMatrix = FloatArray(16)
    private val textMatrix = FloatArray(16)

    private var fbo = -1
    private var fboTexture = -1

    private var testBitmap: Bitmap? = null

    override fun init() {
        super.init()
        Logger.i(TAG, "call init")

        imageProgram =
            Program(ImageTextureProgram.VERTEX_SHADER, ImageTextureProgram.FRAGMENT_SHADER)

        val vertices = ByteBuffer.allocateDirect(verticesData.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        vertices.put(verticesData).position(0)

        GLES20.glGenBuffers(1, vertexVbos, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexVbos[0])
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            verticesData.size * FLOAT_SIZE_BYTES,
            vertices,
            GLES20.GL_STATIC_DRAW
        )

//        数据传输完就解绑
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    override fun render(
        textureDatas: List<TextureData>,
        viewResolution: Size,
    ) {
        val textureData = textureDatas.first()

        // render from oes texture to fbo texture
        GLES20.glViewport(0, 0, textureData.resolution.width, textureData.resolution.height)
        maybeSetupFbo(textureData.resolution)
        bindFbo(fbo, fboTexture)
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureData.textureId)
        Matrix.setIdentityM(mvpMatrix, 0)
        render(program, mvpMatrix, textureData.matrix)
        if (false && testBitmap == null) {
            // test code to validate the fbo
            testBitmap = readToBitmap(textureData.resolution.width, textureData.resolution.height)
        }
        unbindFbo()
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)

        checkGlError("render from oes to fbo")

        // render from fbo texture to screen
        GLES20.glViewport(0, 0, viewResolution.width, viewResolution.height)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTexture)
        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.setIdentityM(textMatrix, 0)
        updateTransformMatrix(mvpMatrix, textureData.resolution, viewResolution)
        render(imageProgram, mvpMatrix, textMatrix)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        checkGlError("render from fbo to screen")
    }

    private fun render(
        program: Program,
        mvpTransform: FloatArray,
        textureTransform: FloatArray,
    ) {
        program.use()

        val aPositionHandle = program.getAtrribLocation("aPosition")
        val mvpMatrixHandle = program.getUniformLocation("uMVPMatrix")
        val aTextureHandle = program.getAtrribLocation("aTextureCoord")
        val stMatrixHandle = program.getUniformLocation("uSTMatrix")

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexVbos[0])
        GLES20.glVertexAttribPointer(
            aPositionHandle,
            3,
            GLES20.GL_FLOAT,
            false,
            5 * FLOAT_SIZE_BYTES,
            0,
        )
        GLES20.glVertexAttribPointer(
            aTextureHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            5 * FLOAT_SIZE_BYTES,
            3 * FLOAT_SIZE_BYTES
        )
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glEnableVertexAttribArray(aTextureHandle)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpTransform, 0)
        GLES20.glUniformMatrix4fv(stMatrixHandle, 1, false, textureTransform, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun maybeSetupFbo(resolution: Size) {
        if (fbo != -1) {
            return
        }
        fboTexture = newTexture(GLES20.GL_TEXTURE_2D, resolution.width, resolution.height)
        fbo = newFbo()
        Logger.d(TAG, "maybeSetupFbo fboTexture=$fboTexture, fbo=$fbo, res=$resolution")
    }
}