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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
class TouchTestView(context : Context, attrs: AttributeSet) :
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
  private var timer  = timeSource.markNow()
  private var count: Int = 0
    set(value) {
      if (value == rows * columns) {
        (getContext() as TouchTestActivity).reportResult(true)
      }
      field = value
    }

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
    if (event != null)  try{
      when (event.action) {
        MotionEvent.ACTION_DOWN -> timer = timeSource.markNow()
        MotionEvent.ACTION_UP -> if(timeSource.markNow() - timer < 0.2.seconds)
          (getContext() as TouchTestActivity).reportResult(false)
      }

      val x = (event.x / xSize).toInt()
      val y = (event.y / ySize).toInt()
      if (!array[x][y]) {
        count++
        array[x][y] = true
      }

      invalidate()
    } catch (e : Exception) {}
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