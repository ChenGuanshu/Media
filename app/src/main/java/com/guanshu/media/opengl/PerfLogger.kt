package com.guanshu.media.opengl

import android.os.SystemClock
import com.guanshu.media.utils.Logger

class PerfLogger {

    private val TAG = "PerfLogger"
    private val tagToTime = hashMapOf<String, ArrayList<Long>>()

    fun logPerf(tag: String, runnable: () -> Unit) {
        val start = SystemClock.elapsedRealtimeNanos()
        runnable()
        val cost = SystemClock.elapsedRealtimeNanos() - start
        tagToTime.getOrPut(tag) { arrayListOf() }.add(cost)
    }

    fun print() {
        tagToTime.entries.forEach {
            val tag = it.key
            val times = it.value
            val avg = times.average()
            Logger.d(TAG, "$tag, run ${times.size} times, avg cost:$avg ns, ${avg / 1000_000} ms")
        }
    }
}

