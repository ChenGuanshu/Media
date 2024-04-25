package com.guanshu.media.opengl.filters

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Size
import com.guanshu.media.opengl.checkGlError
import com.guanshu.media.opengl.getAtrribLocation
import com.guanshu.media.opengl.getUniformLocation
import com.guanshu.media.utils.DefaultSize
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

// mat4: 4x4 矩阵
// vec4: 向量4
// aPosition 顶点坐标 -> uMVPMatrix 旋转拉伸
// aTextureCoord 纹理顶点 -> uSTMatrix 旋转拉伸
// varying vTextureCoord: 意味着vTextureCoord 会被光栅化插值
private const val VERTEX_SHADER = """
                uniform mat4 uMVPMatrix;
                uniform mat4 uSTMatrix;
                attribute vec4 aPosition;
                attribute vec4 aTextureCoord;
                varying vec2 vTextureCoord;
                void main() {
                  gl_Position = uMVPMatrix * aPosition;
                  vTextureCoord = (uSTMatrix * aTextureCoord).xy;
                }
                """

// vTextureCoord 是插值过的片元坐标, texture2D是一个vec4:rgba
// gl_FragColor可以看成是对应 gl_Position的颜色计算
private const val FRAGMENT_SHADER = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTextureCoord;
                uniform samplerExternalOES sTexture;
                void main() {
                    gl_FragColor = texture2D(sTexture, vTextureCoord);
                }
                """

private const val FLOAT_SIZE_BYTES = 4
private const val TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES
private const val TRIANGLE_VERTICES_DATA_POS_OFFSET = 0
private const val TRIANGLE_VERTICES_DATA_UV_OFFSET = 3

class SingleTextureFilter : BaseFilter(
    VERTEX_SHADER,
    FRAGMENT_SHADER,
) {

    private val vertices: FloatBuffer
    private val mvpMatrix = FloatArray(16)

    private var mvpMatrixHandle = 0
    private var stMatrixHandle = 0
    private var aPositionHandle = 0
    private var aTextureHandle = 0

    init {
        // 顶点坐标和纹理坐标
        val verticesData = floatArrayOf(
            // X, Y, Z, U, V
            -1.0f, -1.0f, 0f, 0f, 0f,
            1.0f, -1.0f, 0f, 1f, 0f,
            -1.0f, 1.0f, 0f, 0f, 1f,
            1.0f, 1.0f, 0f, 1f, 1f,
        )
        vertices = ByteBuffer.allocateDirect(verticesData.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        vertices.put(verticesData).position(0)
    }

    override fun init() {
        super.init()
        aPositionHandle = program.getAtrribLocation("aPosition")
        mvpMatrixHandle = program.getUniformLocation("uMVPMatrix")
        aTextureHandle = program.getAtrribLocation("aTextureCoord")
        stMatrixHandle = program.getUniformLocation("uSTMatrix")
    }

    override fun render(
        textureId: Int,
        textMatrix: FloatArray,
        mediaResolution: Size,
        screenResolution: Size,
    ) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        checkGlError("glUseProgram")
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        vertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET)
        GLES20.glVertexAttribPointer(
            aPositionHandle, 3, GLES20.GL_FLOAT, false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES, vertices
        )
        checkGlError("glVertexAttribPointer aPosition")
        GLES20.glEnableVertexAttribArray(aPositionHandle)

        checkGlError("glEnableVertexAttribArray maPositionHandle")
        vertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET)
        GLES20.glVertexAttribPointer(
            aTextureHandle, 2, GLES20.GL_FLOAT, false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES, vertices
        )
        checkGlError("glVertexAttribPointer maTextureHandle")
        GLES20.glEnableVertexAttribArray(aTextureHandle)

        checkGlError("glEnableVertexAttribArray maTextureHandle")
        Matrix.setIdentityM(mvpMatrix, 0)
        adjustTransformMatrix(mvpMatrix, mediaResolution, screenResolution)

        // 缩放纹理，会导致纹理坐标 >1 的使用 clamp_to_edge mode，出现像素重复
        // 建议缩放顶点坐标
//        Matrix.scaleM(stMatrix, 0, 2f, 2f, 1f)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(stMatrixHandle, 1, false, textMatrix, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")
        GLES20.glFinish()
    }

    /**
     * TODO 这个可以优化成只做一次
     */
    private fun adjustTransformMatrix(
        matrix: FloatArray,
        mediaResolution: Size,
        screenResolution: Size,
    ) {
        if (mediaResolution == DefaultSize || screenResolution == DefaultSize) {
            return
        }
        val mediaAspectRatio = mediaResolution.width.toFloat() / mediaResolution.height
        val viewAspectRatio = screenResolution.width.toFloat() / screenResolution.height

        var scaleX = 1f
        var scaleY = 1f
        if (mediaAspectRatio > viewAspectRatio) {
            // 视频比view更宽,x填满整个屏幕,y需要缩放，
            val expectedHeight =
                screenResolution.width.toFloat() / mediaResolution.width * mediaResolution.height
            // 视频高度被默认拉伸填充了view，需要缩放
            scaleY = expectedHeight / screenResolution.height
        } else {
            val expectedWidth =
                screenResolution.height.toFloat() / mediaResolution.height * mediaResolution.width
            scaleX = expectedWidth / screenResolution.width
        }

//        Matrix.scaleM(matrix, 0, scaleX, scaleY, 1f)
        Matrix.scaleM(matrix, 0, scaleX * 0.8f, scaleY * 0.8f, 1f)
    }
}