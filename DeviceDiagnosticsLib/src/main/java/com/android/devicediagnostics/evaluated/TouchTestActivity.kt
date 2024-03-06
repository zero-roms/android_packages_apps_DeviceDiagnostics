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
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.android.devicediagnostics.R
import com.android.devicediagnostics.TestStatus
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
class TouchTestView(context: Context, attrs: AttributeSet) :
    View(context, attrs, 0) {

    private val whitePaint = Paint().apply {
        this.color = getContext().getColor(R.color.white)
    }
    private val redPaint = Paint().apply {
        this.color = getContext().getColor(R.color.red)
    }

    private val rows = 20
    private val columns = 10
    private val xSize get() = width.toFloat() / columns
    private val ySize get() = height.toFloat() / rows

    private var array = Array(columns) { BooleanArray(rows) }

    private val timeSource = TimeSource.Monotonic
    private var timer = timeSource.markNow()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (x in 0 until columns)
            for (y in 0 until rows)
                canvas.drawRect(xSize * x, ySize * y,
                    xSize * (x + 1), ySize * (y + 1),
                    if (array[x][y]) whitePaint else redPaint
                )
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null) try {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> timer = timeSource.markNow()
                MotionEvent.ACTION_UP ->
                    if (array.all { it.all { it } })
                        (context as TouchTestActivity).reportResult(true)
                    else if (timeSource.markNow() - timer < 0.2.seconds)
                        (context as TouchTestActivity).reportResult(false)
            }

            array[(event.x / xSize).toInt()][(event.y / ySize).toInt()] = true
            invalidate()
        } catch (e: Exception) {
        }
        return true
    }
}

class TouchTestActivity : Activity() {
    private lateinit var testStatus: TestStatus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.touch_test)
        testStatus = TestStatus(intent!!.extras!!)
    }

    fun reportResult(result: Boolean) {
        testStatus.touchTest = result
        Intent(this, TouchTestFinalizeActivity::class.java).also {
            testStatus.write(it)
            startActivity(it)
        }
    }
}