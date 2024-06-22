package com.guanshu.media.opengl

object OesTextureProgram {
    // mat4: 4x4 矩阵
    // vec4: 向量4
    // aPosition 顶点坐标 -> uMVPMatrix 旋转拉伸
    // aTextureCoord 纹理顶点 -> uSTMatrix 旋转拉伸
    // varying vTextureCoord: 意味着vTextureCoord 会被光栅化插值
    const val VERTEX_SHADER = """
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

    // 1. vTextureCoord 是插值过的片元坐标, texture2D是一个vec4:rgba
    // 2. gl_FragColor可以看成是对应 gl_Position的颜色计算
    // 3. 如果当前的渲染只需要一个纹理单元的情况下，OpenGL会默认我们使用的是第一个纹理单元. 正常来说 sTexture
    // 需要与纹理单元绑定
    const val FRAGMENT_SHADER = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTextureCoord;
                uniform samplerExternalOES sTexture;
                void main() {
                    gl_FragColor = texture2D(sTexture, vTextureCoord);
                }
                """
}

// TODO 加了 alpha之后，break了很多filter，以后再说把
object ImageTextureProgram {
    const val VERTEX_SHADER = OesTextureProgram.VERTEX_SHADER
    const val FRAGMENT_SHADER = """
                precision mediump float;
                varying vec2 vTextureCoord;
                uniform sampler2D sTexture;
                uniform float alpha;
                void main() {
                    vec4 color = texture2D(sTexture, vTextureCoord);
                    color.a = alpha;
                    gl_FragColor = color;
                }
                """
}

object TwoOesTexture2Program {
    const val VERTEX_SHADER = """
                uniform mat4 uSTMatrix1;
                uniform mat4 uSTMatrix2;
                attribute vec4 aPosition;
                attribute vec4 aTextureCoord;
                varying vec2 vTextureCoord1;
                varying vec2 vTextureCoord2;
                varying float left;
                void main() {
                  gl_Position = aPosition;
                  vTextureCoord1 = (uSTMatrix1 * aTextureCoord).xy;
                  vTextureCoord2 = (uSTMatrix2 * aTextureCoord).xy;
                  
                  if(aPosition.x <= 0.0){
                    left = 1.0;
                  } else {
                    left = 0.0;
                  }
                }
                """

    const val FRAGMENT_SHADER = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTextureCoord1;
                varying vec2 vTextureCoord2;
                varying float left;
                uniform samplerExternalOES sTexture1;
                uniform samplerExternalOES sTexture2;
                void main() {
                    vec4 color1 = texture2D(sTexture1, vTextureCoord1);
                    vec4 color2 = texture2D(sTexture2, vTextureCoord2);
                    // 不理解为什么是 1.0 而不是 0.5 TODO investigate
                    if(left >= 1.0){
                        gl_FragColor = color1;
                    }else{
                        gl_FragColor = color2;
                    }
                }
                """
}

object TwoOesTextureMixProgram {
    const val VERTEX_SHADER = """
                uniform mat4 uSTMatrix1;
                uniform mat4 uSTMatrix2;
                attribute vec4 aPosition;
                attribute vec4 aTextureCoord;
                varying vec2 vTextureCoord1;
                varying vec2 vTextureCoord2;
                void main() {
                  gl_Position = aPosition;
                  vTextureCoord1 = (uSTMatrix1 * aTextureCoord).xy;
                  vTextureCoord2 = (uSTMatrix2 * aTextureCoord).xy;
                }
                """

    const val FRAGMENT_SHADER = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTextureCoord1;
                varying vec2 vTextureCoord2;
                uniform samplerExternalOES sTexture1;
                uniform samplerExternalOES sTexture2;
                void main() {
                    vec4 color1 = texture2D(sTexture1, vTextureCoord1);
                    vec4 color2 = texture2D(sTexture2, vTextureCoord2);
                    gl_FragColor = mix(color1, color2, 0.5);
                }
                """
}

object GaussianTextureProgram {

    private const val VERTEX_SHADER = """
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

    private const val FRAGMENT_SHADER = """
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
                } 
        """
}