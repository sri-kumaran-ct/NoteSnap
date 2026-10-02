package com.example

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("NoteSnap", appName)
  }

  @Test
  fun `load app logo drawable successfully`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val drawable = context.getDrawable(R.drawable.ic_app_logo)
    assertNotNull("Drawable should not be null", drawable)
    assertTrue("Drawable should be BitmapDrawable", drawable is BitmapDrawable)
  }
}
