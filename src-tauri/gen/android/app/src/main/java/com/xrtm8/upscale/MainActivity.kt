package com.xrtm8.upscale

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : TauriActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    val background = Color.rgb(12, 14, 17)
    enableEdgeToEdge(SystemBarStyle.dark(background), SystemBarStyle.dark(background))
    super.onCreate(savedInstanceState)
    val content = findViewById<View>(android.R.id.content)
    content.setBackgroundColor(background)
    ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
      val safeArea = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
      view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom)
      insets
    }
    ViewCompat.requestApplyInsets(content)
  }
}
