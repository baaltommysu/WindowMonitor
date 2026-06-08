package com.baaltommysu.windowmonitor.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BitmapDecodeBoundsInstrumentedTest {
    @Test
    fun decodeStreamWithJustDecodeBoundsReturnsNullButPopulatesDimensions() {
        val jpegBytes = ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply {
                compress(Bitmap.CompressFormat.JPEG, 90, output)
                recycle()
            }
            output.toByteArray()
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }

        val bitmap = BitmapFactory.decodeStream(ByteArrayInputStream(jpegBytes), null, options)

        assertNull(bitmap)
        assertEquals(64, options.outWidth)
        assertEquals(48, options.outHeight)
    }
}
