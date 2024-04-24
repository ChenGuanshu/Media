package com.guanshu.media.opengl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.print.PrintAttributes
import android.util.Log
import android.util.Size
import com.guanshu.media.utils.DefaultSize
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class TextureRender {

    private val vertices: FloatBuffer
    private val mvpMatrix = FloatArray(16)
    private val stMatrix = FloatArray(16)
    private var program = 0
    var textureId = -12345
        private set
    private var mvpMatrixHandle = 0
    private var stMatrixHandle = 0
    private var aPositionHandle = 0
    private var aTextureHandle = 0
    private var init: Boolean = false

    init {
        // 顶点坐标和纹理坐标
        val verticesData = floatArrayOf(
            // X, Y, Z, U, V
            -1.0f, -1.0f, 0f, 0f, 0f,
            1.0f, -1.0f, 0f, 1f, 0f,
            -1.0f, 1.0f, 0f, 0f, 1f,
            1.0f, 1.0f, 0f, 1f, 1f,
        )
        vertices = ByteBuffer.allocateDirect(
            verticesData.size * FLOAT_SIZE_BYTES
        ).order(ByteOrder.nativeOrder()).asFloatBuffer()

        vertices.put(verticesData).position(0)
        Matrix.setIdentityM(stMatrix, 0)
    }

    private fun getAtrribLocation(name: String): Int {
        val ret = GLES20.glGetAttribLocation(program, name)
        checkGlError("glGetAttribLocation $name")
        if (ret == -1) {
            throw RuntimeException("Could not get attrib location for $name")
        }
        return ret
    }

    private fun getUniformLocation(name: String): Int {
        val ret = GLES20.glGetUniformLocation(program, name)
        checkGlError("glGetUniformLocation $name")
        if (ret == -1) {
            throw RuntimeException("Could not get uniform location for $name")
        }
        return ret
    }


    /**
     * Initializes GL state.  Call this after the EGL surface has been created and made current.
     */
    fun init() {
        if (init) {
            return
        }
        init = true

//        program = createProgram(VERTEX_SHADER, FOUR_TIMES_FRAGMENT_SHADER)
//        program = createProgram(VERTEX_SHADER, TWO_TIMES_FRAGMENT_SHADER)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
//        program = createProgram(GAUSSIAN_VERTEX_SHADER, GAUSSIAN_FRAGMENT_SHADER)
        if (program == 0) {
            throw RuntimeException("failed creating program")
        }

        aPositionHandle = getAtrribLocation("aPosition")
        mvpMatrixHandle = getUniformLocation("uMVPMatrix")

        aTextureHandle = getAtrribLocation("aTextureCoord")
        stMatrixHandle = getUniformLocation("uSTMatrix")

        // 设置 texture
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        checkGlError("glBindTexture mTextureID")
        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST.toFloat()
        )
        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        checkGlError("glTexParameter")
    }

    fun drawFrame(
        surfaceTexture: SurfaceTexture,
        mediaResolution: Size,
        screenResolution: Size,
    ) {
        checkGlError("onDrawFrame start")
        surfaceTexture.getTransformMatrix(stMatrix)
        /**
         * 0.0, -1.0, 0.0, 0.0,
         * 1.0,  0.0, 0.0, 0.0,
         * 0.0,  0.0, 1.0, 0.0,
         * 0.0,  1.0, 0.0, 1.0
         * 这是一个st matrix的例子，表示了顺时针调整了90度，用来将纹理的内容正向
         */

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
        GLES20.glUniformMatrix4fv(stMatrixHandle, 1, false, stMatrix, 0)

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

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
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

    fun checkGlError(op: String) {
        var error: Int
        while (GLES20.glGetError().also { error = it } != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "$op: glError $error")
            throw RuntimeException("$op: glError $error")
        }
    }

    companion object {
        private const val TAG = "TextureRender"

        private const val FLOAT_SIZE_BYTES = 4
        private const val TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES
        private const val TRIANGLE_VERTICES_DATA_POS_OFFSET = 0
        private const val TRIANGLE_VERTICES_DATA_UV_OFFSET = 3

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

        // 将画面平铺成上下
        // TODO 一个猜想，如果 sTexture本身是90 rotated， 那么vTextureCoord里的值x和y就是互换的
        // 甚至需要互倒的（1-x）
        private const val TWO_TIMES_FRAGMENT_SHADER = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTextureCoord;
                uniform samplerExternalOES sTexture;
                void main() {
                    vec2 adjustedTextureCoord = vTextureCoord; 
                    if (vTextureCoord.x < 0.5) {  
                        adjustedTextureCoord.x += 0.25;  
                    } else {  
                        adjustedTextureCoord.x -= 0.25;  
                    }  
                    gl_FragColor = texture2D(sTexture, adjustedTextureCoord); 
                }
                """

        // 将画面平铺成2x2
        private const val FOUR_TIMES_FRAGMENT_SHADER = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTextureCoord;
                uniform samplerExternalOES sTexture;
                void main() {
                    vec2 adjustedTextureCoord = vTextureCoord;  
                    if (vTextureCoord.x < 0.5) {  
                        adjustedTextureCoord.x = adjustedTextureCoord.x*2.0;
                    } else {  
                        adjustedTextureCoord.x = (adjustedTextureCoord.x-0.5)*2.0;
                    }  
                    if (vTextureCoord.y < 0.5) {  
                        adjustedTextureCoord.y = adjustedTextureCoord.y*2.0;
                    } else {  
                        adjustedTextureCoord.y = (adjustedTextureCoord.y-0.5)*2.0;
                    }  
                    gl_FragColor = texture2D(sTexture, adjustedTextureCoord); 
                }
                """

        // 反色
        private const val INVERSE_FRAGMENT_SHADER = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTextureCoord;
                uniform samplerExternalOES sTexture;
                void main() {
                    vec4 src = texture2D(sTexture, vTextureCoord);
                    gl_FragColor = vec4(1.0 - src.r, 1.0 - src.g, 1.0 - src.b, 1.0);
                }
                """

        // 灰色
        private const val GRAY_FRAGMENT_SHADER = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTextureCoord;
                uniform samplerExternalOES sTexture;
                void main() {
                    vec4 src = texture2D(sTexture, vTextureCoord);
                    float gray = (src.r + src.g + src.b) / 3.0;
                    gl_FragColor =vec4(gray, gray, gray, 1.0);
                }
                """

        private const val GAUSSIAN_VERTEX_SHADER = """
                uniform mat4 uMVPMatrix;
                uniform mat4 uSTMatrix;
                attribute vec4 aPosition;
                attribute vec4 aTextureCoord;
                varying vec2 vTextureCoord;
                varying vec2 blurCoordinates[9];
                void main() {
                  gl_Position = uMVPMatrix * aPosition;
                  vTextureCoord = (uSTMatrix * aTextureCoord).xy;
                  
                  //横向和纵向的步长
                  vec2 widthStep = vec2(10.0/2448.0, 0.0);
                  vec2 heightStep = vec2(0.0, 10.0/3264.0);
                  //计算出当前片段相邻像素的纹理坐标
                  blurCoordinates[0] = vTextureCoord.xy - heightStep - widthStep; // 左上
                  blurCoordinates[1] = vTextureCoord.xy - heightStep; // 上
                  blurCoordinates[2] = vTextureCoord.xy - heightStep + widthStep; // 右上
                  blurCoordinates[3] = vTextureCoord.xy - widthStep; // 左中
                  blurCoordinates[4] = vTextureCoord.xy; // 中
                  blurCoordinates[5] = vTextureCoord.xy + widthStep; // 右中
                  blurCoordinates[6] = vTextureCoord.xy + heightStep - widthStep; // 左下
                  blurCoordinates[7] = vTextureCoord.xy + heightStep; // 下
                  blurCoordinates[8] = vTextureCoord.xy + heightStep + widthStep; // 右下
                }
                """

        private const val GAUSSIAN_FRAGMENT_SHADER = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTextureCoord;
                varying vec2 blurCoordinates[9];
                uniform samplerExternalOES sTexture;
                mat3 kernelMatrix = mat3(
                    0.0947416f, 0.118318f, 0.0947416f,
                    0.118318f,  0.147761f, 0.118318f,
                    0.0947416f, 0.118318f, 0.0947416f
                );
                void main() {
                    vec4 sum = texture2D(sTexture, blurCoordinates[0]) * kernelMatrix[0][0];
                    sum += texture2D(sTexture, blurCoordinates[1]) * kernelMatrix[0][1];
                    sum += texture2D(sTexture, blurCoordinates[2]) * kernelMatrix[0][2];
                    sum += texture2D(sTexture, blurCoordinates[3]) * kernelMatrix[1][0];
                    sum += texture2D(sTexture, blurCoordinates[4]) * kernelMatrix[1][1];
                    sum += texture2D(sTexture, blurCoordinates[5]) * kernelMatrix[1][2];
                    sum += texture2D(sTexture, blurCoordinates[6]) * kernelMatrix[2][0];
                    sum += texture2D(sTexture, blurCoordinates[7]) * kernelMatrix[2][1];
                    sum += texture2D(sTexture, blurCoordinates[8]) * kernelMatrix[2][2];
                    gl_FragColor = sum;
//                    gl_FragColor = texture2D(sTexture, blurCoordinates[4]);
                } 
        """
    }
}