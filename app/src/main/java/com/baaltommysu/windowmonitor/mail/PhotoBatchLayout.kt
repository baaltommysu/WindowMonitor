package com.baaltommysu.windowmonitor.mail

data class PhotoBatchGrid(
    val columns: Int,
    val rows: Int,
)

object PhotoBatchLayout {
    fun gridFor(photoCount: Int): PhotoBatchGrid {
        require(photoCount > 0) { "photoCount must be positive" }
        val columns = when {
            photoCount == 1 -> 1
            photoCount <= 4 -> 2
            else -> 3
        }
        return PhotoBatchGrid(
            columns = columns,
            rows = (photoCount + columns - 1) / columns,
        )
    }
}
