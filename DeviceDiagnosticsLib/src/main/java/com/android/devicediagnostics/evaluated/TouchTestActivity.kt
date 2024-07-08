/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.devicediagnostics.evaluated

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.android.devicediagnostics.R
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import java.util.*

const val TOUCH_TEST_RESULT_KEY = "touch_test_result"

@OptIn(ExperimentalTime::class)
class TouchTestView(context: Context, attrs: AttributeSet) : View(context, attrs, 0) {

    private val whitePaint = Paint().apply { this.color = getContext().getColor(R.color.white) }
    private val redPaint = Paint().apply { this.color = getContext().getColor(R.color.red) }

    private val squareSize = 200 // px
    private val columns = context.getResources().getDisplayMetrics().widthPixels / squareSize
    private val rows = context.getResources().getDisplayMetrics().heightPixels / squareSize

    private val xSize
        get() = width.toFloat() / columns
    private val ySize
        get() = height.toFloat() / rows

    private var array = Array(columns) {Array(rows) {false} }

    private val timeSource = TimeSource.Monotonic
    private var timer = timeSource.markNow()


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (x in 0 until columns) for (y in 0 until rows) canvas.drawRect(
            xSize * x,
            ySize * y,
            xSize * (x + 1),
            ySize * (y + 1),
            if (array[x][y]) whitePaint else redPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    timer = timeSource.markNow()
                }
                MotionEvent.ACTION_UP ->
                    if (array.all { it.all { it } })
                        (context as TouchTestActivity).reportResult(true)
                    else if (timeSource.markNow() - timer < 0.2.seconds)
                        (context as TouchTestActivity).reportResult(false)
            }
            val pointerCount = event.pointerCount
            for(i in 0 until pointerCount) {
                val x = (event.getX(i) / xSize).toInt()
                val y = (event.getY(i) / ySize).toInt()
                if(!array[x][y]) {
                    array[x][y] = true
                    invalidate()
                }
            }
        }
        return true
    }
}

class TouchTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.touch_test)
    }

    fun reportResult(result: Boolean) {
        nextTestActivity(this, TouchTestFinalizeActivity::class.java.name) {
            it.putExtra(TOUCH_TEST_RESULT_KEY, result)
        }
    }
}
