package com.guanshu.media.opengl

import android.util.Log
import android.util.Size
import com.guanshu.media.opengl.filters.FlattenFilter
import com.guanshu.media.opengl.filters.FlattenWithImageFilter
import com.guanshu.media.opengl.filters.SingleTextureFilter
import com.guanshu.media.opengl.filters.TextureWithImageFilter

private const val TAG = "TextureRender"
private const val FILTER_ID = 3

class TextureData(
    val textureId: Int,
    val matrix: FloatArray,
    var resolution: Size,
)

class TextureRender {

    // TODO support changing
    private val filter = when (FILTER_ID) {
        1 -> SingleTextureFilter()
        2 -> TextureWithImageFilter()
        3 -> FlattenFilter()
        4 -> FlattenWithImageFilter()
        else -> SingleTextureFilter()
    }

    private var init = false

    var textureId = -12345
        private set

    /**
     * Initializes GL state.  Call this after the EGL surface has been created and made current.
     */
    fun init() {
        if (init) return
        init = true

        Log.i(TAG, "init")
        // 设置 texture
        val textures = IntArray(1)
        newTexture(textures)
        textureId = textures[0]

        filter.init()
    }

    fun drawFrame(
        textureData: TextureData,
        viewResolution: Size,
    ) {
        checkGlError("onDrawFrame start")

        /**
         * 0.0, -1.0, 0.0, 0.0,
         * 1.0,  0.0, 0.0, 0.0,
         * 0.0,  0.0, 1.0, 0.0,
         * 0.0,  1.0, 0.0, 1.0
         * 这是一个st matrix的例子，表示了顺时针调整了90度，用来将纹理的内容正向
         */
        filter.render(textureData, viewResolution)
    }

    // Deprecated: move to other filter
    companion object {

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