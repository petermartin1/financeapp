package com.financeapp.ui.components.charts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PieGeometryTest {

    // A 200x200 canvas: center (100,100), radius 100. Four equal slices start at 12 o'clock
    // and sweep clockwise: [0]=12→3, [1]=3→6, [2]=6→9, [3]=9→12.
    private val quarters = listOf(1f, 1f, 1f, 1f)

    @Test
    fun `taps land in the slice under them, clockwise from 12 o'clock`() {
        assertEquals(0, pieSliceAt(tapX = 140f, tapY = 40f, width = 200f, height = 200f, values = quarters))   // ~1:30
        assertEquals(1, pieSliceAt(tapX = 160f, tapY = 160f, width = 200f, height = 200f, values = quarters))  // ~4:30
        assertEquals(2, pieSliceAt(tapX = 40f, tapY = 160f, width = 200f, height = 200f, values = quarters))   // ~7:30
        assertEquals(3, pieSliceAt(tapX = 40f, tapY = 40f, width = 200f, height = 200f, values = quarters))    // ~10:30
    }

    @Test
    fun `a boundary angle belongs to the next slice`() {
        // Exactly 3 o'clock (90° from top) is the start of slice 1, not the end of slice 0.
        assertEquals(1, pieSliceAt(tapX = 150f, tapY = 100f, width = 200f, height = 200f, values = quarters))
    }

    @Test
    fun `unequal values shift the boundaries proportionally`() {
        // [3,1]: slice 0 covers 270° (12 o'clock clockwise to 9 o'clock), slice 1 the rest.
        val values = listOf(3f, 1f)
        assertEquals(0, pieSliceAt(tapX = 160f, tapY = 160f, width = 200f, height = 200f, values = values)) // ~4:30
        assertEquals(1, pieSliceAt(tapX = 40f, tapY = 40f, width = 200f, height = 200f, values = values))   // ~10:30
    }

    @Test
    fun `taps outside the pie radius miss`() {
        assertNull(pieSliceAt(tapX = 1f, tapY = 1f, width = 200f, height = 200f, values = quarters))
        // Non-square canvas: pie diameter is min(300,200)=200 centered at (150,100); (295,100) is outside.
        assertNull(pieSliceAt(tapX = 295f, tapY = 100f, width = 300f, height = 200f, values = quarters))
    }

    @Test
    fun `taps inside the donut hole miss`() {
        assertNull(pieSliceAt(tapX = 100f, tapY = 100f, width = 200f, height = 200f, values = quarters, centerHoleRatio = 0.5f))
        // Just outside the hole (radius 50) still hits.
        assertEquals(1, pieSliceAt(tapX = 160f, tapY = 100f, width = 200f, height = 200f, values = quarters, centerHoleRatio = 0.5f))
    }

    @Test
    fun `empty or non-positive totals miss`() {
        assertNull(pieSliceAt(tapX = 100f, tapY = 100f, width = 200f, height = 200f, values = emptyList()))
        assertNull(pieSliceAt(tapX = 100f, tapY = 100f, width = 200f, height = 200f, values = listOf(0f, 0f)))
    }

    @Test
    fun `non-square canvas centers the pie on min dimension`() {
        // 300x200: center (150,100). Straight up from center lands in slice 0.
        assertEquals(0, pieSliceAt(tapX = 151f, tapY = 20f, width = 300f, height = 200f, values = quarters))
    }
}
