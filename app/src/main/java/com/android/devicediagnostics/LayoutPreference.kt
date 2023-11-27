/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.devicediagnostics

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.res.TypedArrayUtils
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

/**
 * A preference can be simply customized a view by adding layout attribute in xml.
 * User also can decide whether or not LayoutPreference allows above divider or below divider.
 *
 * For instances,
 *
 * <com.android.settingslib.widget.LayoutPreference ... android:layout="@layout/settings_entity_header" xxxxxxx:allowDividerAbove="true" xxxxxxx:allowDividerBelow="true"></com.android.settingslib.widget.LayoutPreference>
 *
 */
class LayoutPreference : Preference {
  private val mClickListener = View.OnClickListener { v: View? ->
    performClick(
      v!!
    )
  }
  private var mAllowDividerAbove = false
  /**
   * Return a value whether or not LayoutPreference allows to set a below divider.
   */
  /**
   * LayoutPreference whether or not allows to set a below divider.
   */
  var isAllowDividerBelow = false
  private var mRootView: View? = null

  /**
   * Constructs a new LayoutPreference with the given context's theme and the supplied
   * attribute set.
   *
   * @param context The Context the view is running in, through which it can
   * access the current theme, resources, etc.
   * @param attrs The attributes of the XML tag that is inflating the view.
   */
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
    init(context, attrs, 0 /* defStyleAttr */)
  }

  /**
   * Constructs a new LayoutPreference with the given context's theme, the supplied
   * attribute set, and default style attribute.
   *
   * @param context The Context the view is running in, through which it can
   * access the current theme, resources, etc.
   * @param attrs The attributes of the XML tag that is inflating the view.
   * @param defStyleAttr An attribute in the current theme that contains a
   * reference to a style resource that supplies default
   * values for the view. Can be 0 to not look for
   * defaults.
   */
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
    context,
    attrs,
    defStyleAttr
  ) {
    init(context, attrs, defStyleAttr)
  }

  /**
   * Constructs a new LayoutPreference with the given context's theme and a customized view id.
   *
   * @param context The Context the view is running in, through which it can
   * access the current theme, resources, etc.
   * @param resource The view id which you expected to be inflated and show in preference.
   */
  constructor(context: Context?, resource: Int) : this(
    context,
    LayoutInflater.from(context).inflate(resource, null, false)
  )

  /**
   * Constructs a new LayoutPreference with the given context's theme and a customized view.
   *
   * @param context The Context the view is running in, through which it can
   * access the current theme, resources, etc.
   * @param view The view which you expected show in preference.
   */
  constructor(context: Context?, view: View) : super(context!!) {
    setView(view)
  }

  private fun init(context: Context, attrs: AttributeSet?, defStyleAttr: Int) {
    var a = context.obtainStyledAttributes(attrs, androidx.preference.R.styleable.Preference)
    mAllowDividerAbove = TypedArrayUtils.getBoolean(
      a, androidx.preference.R.styleable.Preference_allowDividerAbove,
      androidx.preference.R.styleable.Preference_allowDividerAbove, false
    )
    isAllowDividerBelow = TypedArrayUtils.getBoolean(
      a, androidx.preference.R.styleable.Preference_allowDividerBelow,
      androidx.preference.R.styleable.Preference_allowDividerBelow, false
    )
    a.recycle()
    a = context.obtainStyledAttributes(
      attrs, androidx.preference.R.styleable.Preference, defStyleAttr, 0
    )
    val layoutResource =
      a.getResourceId(androidx.preference.R.styleable.Preference_android_layout, 0)
    require(layoutResource != 0) { "LayoutPreference requires a layout to be defined" }
    a.recycle()

    // Need to create view now so that findViewById can be called immediately.
    val view = LayoutInflater.from(getContext())
      .inflate(layoutResource, null, false)
    setView(view)
  }

  private fun setView(view: View) {
    layoutResource = R.layout.layout_preference_frame
    mRootView = view
    shouldDisableView = false
  }

  override fun onBindViewHolder(holder: PreferenceViewHolder) {
    holder.itemView.setOnClickListener(mClickListener)
    val selectable = isSelectable
    holder.itemView.isFocusable = selectable
    holder.itemView.isClickable = selectable
    holder.isDividerAllowedAbove = mAllowDividerAbove
    holder.isDividerAllowedBelow = isAllowDividerBelow
    val layout = holder.itemView as FrameLayout
    layout.removeAllViews()
    val parent = mRootView!!.parent as ViewGroup?
    parent?.removeView(mRootView)
    layout.addView(mRootView)
  }

  /**
   * Finds the view with the given ID.
   *
   * @param id the ID to search for
   * @return a view with given ID if found, or `null` otherwise
   */
  fun <T : View?> findViewById(id: Int): T? {
    return mRootView!!.findViewById(id)
  }
}