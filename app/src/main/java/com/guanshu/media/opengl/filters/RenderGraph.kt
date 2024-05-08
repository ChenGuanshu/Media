package com.guanshu.media.opengl.filters

import com.guanshu.media.opengl.filters.FilterConstants.toFilter

class RenderGraph() {

    constructor(filterId: Int) : this() {
        addFilter(filterId)
    }

    val filtersMap = hashMapOf<Int, ArrayList<Filter>>()
    var outputFilter: Filter? = null

    fun addFilter(filterId: Int, index: Int = 0) {
        val filters = filtersMap.getOrPut(index) { arrayListOf() }
        filters.add(filterId.toFilter())
    }

    fun addOutputFilter(filterId: Int) {
        outputFilter = filterId.toFilter()
    }
}