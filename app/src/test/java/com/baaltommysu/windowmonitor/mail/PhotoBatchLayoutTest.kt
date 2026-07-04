package com.baaltommysu.windowmonitor.mail

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoBatchLayoutTest {
    @Test
    fun usesSingleCellForOnePhoto() {
        val grid = PhotoBatchLayout.gridFor(1)

        assertEquals(1, grid.columns)
        assertEquals(1, grid.rows)
    }

    @Test
    fun usesTwoByTwoGridForFourPhotos() {
        val grid = PhotoBatchLayout.gridFor(4)

        assertEquals(2, grid.columns)
        assertEquals(2, grid.rows)
    }

    @Test
    fun usesThreeByTwoGridForSixPhotos() {
        val grid = PhotoBatchLayout.gridFor(6)

        assertEquals(3, grid.columns)
        assertEquals(2, grid.rows)
    }
}
