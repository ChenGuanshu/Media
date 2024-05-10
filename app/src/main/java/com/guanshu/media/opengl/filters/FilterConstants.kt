package com.guanshu.media.opengl.filters

object FilterConstants {

    const val SINGLE_TEXTURE = 1
    const val TEXTURE_WITH_IMAGE = 2
    const val FLATTEN = 3
    const val FLATTEN_WITH_IMAGE = 4
    const val TWO_OES_TEXTURE = 5
    const val TWO_OES_TEXTURE_2 = 6
    const val TWO_OES_TEXTURE_MIX = 7
    const val SINGLE_TEXTURE_FBO = 8

    val ID_TO_FILTER = mapOf(
        SINGLE_TEXTURE to SingleTextureFilter::class.java,
        TEXTURE_WITH_IMAGE to TextureWithImageFilter::class.java,
        FLATTEN to FlattenFilter::class.java,
        FLATTEN_WITH_IMAGE to FlattenWithImageFilter::class.java,
        TWO_OES_TEXTURE to TwoOesTextureFilter::class.java,
        TWO_OES_TEXTURE_2 to TwoOesTextureFilter2::class.java,
        TWO_OES_TEXTURE_MIX to TwoOesTextureMixFilter::class.java,
        SINGLE_TEXTURE_FBO to SingleTextureFboFilter::class.java,
    )

    fun Int.toFilter() = ID_TO_FILTER[this]!!.newInstance()
}