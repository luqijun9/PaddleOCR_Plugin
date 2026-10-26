package com.paddle.ocr.demo.plugin

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrResultProcessorTest {

    // ==========================================
    // 1. 起点与宽高 (XYWH) 模式测试
    // ==========================================

    @Test
    fun testParseRegionXywh_percentValid() {
        // x=10, y=20, w=30, h=40 -> left=10, top=20, right=40, bottom=60
        val result = OcrResultProcessor.parseRegionXywh(
            listOf("10", "20", "30", "40"),
            usePercent = true
        )
        assertNotNull(result)
        assertArrayEquals(intArrayOf(10, 20, 40, 60), result)
    }

    @Test
    fun testParseRegionXywh_percentFullRange() {
        // x=0, y=0, w=100, h=100 -> left=0, top=0, right=100, bottom=100
        val result = OcrResultProcessor.parseRegionXywh(
            listOf("0", "0", "100", "100"),
            usePercent = true
        )
        assertNotNull(result)
        assertArrayEquals(intArrayOf(0, 0, 100, 100), result)
    }

    @Test
    fun testParseRegionXywh_percentExceedsBoundary() {
        // x=50, w=60 -> 110 > 100, 超过 100% 边界应返回 null
        val result = OcrResultProcessor.parseRegionXywh(
            listOf("50", "20", "60", "30"),
            usePercent = true
        )
        assertNull(result)
    }

    @Test
    fun testParseRegionXywh_pixelValid() {
        // 像素模式支持大于 100 的数值
        val result = OcrResultProcessor.parseRegionXywh(
            listOf("100", "200", "400", "600"),
            usePercent = false
        )
        assertNotNull(result)
        assertArrayEquals(intArrayOf(100, 200, 500, 800), result)
    }

    @Test
    fun testParseRegionXywh_zeroOrNegativeDimensions() {
        // 宽或高 <= 0 必须返回 null
        assertNull(OcrResultProcessor.parseRegionXywh(listOf("10", "10", "0", "50"), usePercent = true))
        assertNull(OcrResultProcessor.parseRegionXywh(listOf("10", "10", "-5", "50"), usePercent = false))
        assertNull(OcrResultProcessor.parseRegionXywh(listOf("-1", "10", "20", "50"), usePercent = false))
    }

    @Test
    fun testParseRegionXywh_floatStringTolerance() {
        // 宿主变量可能传如 "10.0" 这样的浮点数字符串
        val result = OcrResultProcessor.parseRegionXywh(
            listOf("10.0", "20.5", "30.0", "40.0"),
            usePercent = true
        )
        assertNotNull(result)
        assertArrayEquals(intArrayOf(10, 20, 40, 60), result)
    }

    @Test
    fun testParseRegionXywh_commaSeparatedFallback() {
        // 用户可能在第一个输入框里直接填入逗号分隔的 "10,20,30,40"
        val result = OcrResultProcessor.parseRegionXywh(
            listOf("10, 20, 30, 40", "", "", ""),
            usePercent = true
        )
        assertNotNull(result)
        assertArrayEquals(intArrayOf(10, 20, 40, 60), result)
    }

    // ==========================================
    // 2. 一次传四个 (SINGLE) 模式测试
    // ==========================================

    @Test
    fun testParseRegionSpec_percentValid() {
        val result = OcrResultProcessor.parseRegionSpec("0,25,100,75", usePercent = true)
        assertNotNull(result)
        assertArrayEquals(intArrayOf(0, 25, 100, 75), result)
    }

    @Test
    fun testParseRegionSpec_delimiters() {
        // 支持中英文逗号、分号、空格混用
        val result = OcrResultProcessor.parseRegionSpec("10， 20； 80  90", usePercent = true)
        assertNotNull(result)
        assertArrayEquals(intArrayOf(10, 20, 80, 90), result)
    }

    @Test
    fun testParseRegionSpec_invalidOrder() {
        // right <= left 或 bottom <= top
        assertNull(OcrResultProcessor.parseRegionSpec("50,20,30,80", usePercent = true))
        assertNull(OcrResultProcessor.parseRegionSpec("10,80,90,50", usePercent = true))
    }

    // ==========================================
    // 3. 分别传四个 (SEPARATE) 模式测试
    // ==========================================

    @Test
    fun testParseRegionValues_percentAndPixel() {
        val percent = OcrResultProcessor.parseRegionValues(listOf("0", "10", "90", "100"), usePercent = true)
        assertNotNull(percent)
        assertArrayEquals(intArrayOf(0, 10, 90, 100), percent)

        val pixel = OcrResultProcessor.parseRegionValues(listOf("100", "200", "800", "900"), usePercent = false)
        assertNotNull(pixel)
        assertArrayEquals(intArrayOf(100, 200, 800, 900), pixel)
    }

    // ==========================================
    // 4. 像素边界换算 (toPixelBounds) 测试
    // ==========================================

    @Test
    fun testToPixelBounds_percentCalculation() {
        // 屏幕 1080 x 2400，百分比 [0, 25, 100, 75]
        val bounds = OcrResultProcessor.toPixelBounds(
            values = intArrayOf(0, 25, 100, 75),
            width = 1080,
            height = 2400,
            usePercent = true
        )
        assertNotNull(bounds)
        assertArrayEquals(intArrayOf(0, 600, 1080, 1800), bounds)
    }

    @Test
    fun testToPixelBounds_pixelWithinBounds() {
        val bounds = OcrResultProcessor.toPixelBounds(
            values = intArrayOf(100, 200, 800, 1000),
            width = 1080,
            height = 2400,
            usePercent = false
        )
        assertNotNull(bounds)
        assertArrayEquals(intArrayOf(100, 200, 800, 1000), bounds)
    }

    @Test
    fun testToPixelBounds_pixelOutOfBounds() {
        // 像素超出图像宽高
        val bounds = OcrResultProcessor.toPixelBounds(
            values = intArrayOf(100, 200, 1200, 1000),
            width = 1080,
            height = 2400,
            usePercent = false
        )
        assertNull(bounds)
    }

    // ==========================================
    // 5. 目标文本匹配 (matchesTarget) 测试
    // ==========================================

    @Test
    fun testMatchesTarget_contains() {
        assertTrue(OcrResultProcessor.matchesTarget("验证码是 8892 请勿泄露", "8892", isRegex = false, isExactMatch = false))
        assertTrue(OcrResultProcessor.matchesTarget("Hello World", "hello", isRegex = false, isExactMatch = false, isIgnoreCase = true))
        assertFalse(OcrResultProcessor.matchesTarget("Hello World", "hello", isRegex = false, isExactMatch = false, isIgnoreCase = false))
    }

    @Test
    fun testMatchesTarget_exact() {
        assertTrue(OcrResultProcessor.matchesTarget("确认", "确认", isExactMatch = true))
        assertFalse(OcrResultProcessor.matchesTarget("确认付款", "确认", isExactMatch = true))
    }

    @Test
    fun testMatchesTarget_regex() {
        // 正则包含
        assertTrue(OcrResultProcessor.matchesTarget("订单号: TD2026102601", "TD\\d{10}", isRegex = true, isExactMatch = false))
        // 正则完全匹配
        assertTrue(OcrResultProcessor.matchesTarget("13800138000", "^1[3-9]\\d{9}$", isRegex = true, isExactMatch = true))
        assertFalse(OcrResultProcessor.matchesTarget("手机号: 13800138000", "^1[3-9]\\d{9}$", isRegex = true, isExactMatch = true))
        // 非法正则容错，返回 false 而非崩溃
        assertFalse(OcrResultProcessor.matchesTarget("test", "[a-z", isRegex = true))
    }
}
