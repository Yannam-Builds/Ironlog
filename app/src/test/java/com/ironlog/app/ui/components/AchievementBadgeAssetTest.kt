package com.ironlog.app.ui.components

import com.ironlog.app.domain.badges.BadgeDefinitions
import java.io.File
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementBadgeAssetTest {
    @Test
    fun `every canonical achievement resolves to a distinct raster badge`() {
        val definitions = BadgeDefinitions.all
        val resolved = definitions.associate { definition ->
            definition.id to achievementBadgeDrawable(definition.id)
        }

        assertEquals(definitions.size, resolved.values.toSet().size)
        assertTrue(resolved.values.all { it != 0 })
    }

    @Test
    fun `every declared achievement asset exists in drawable nodpi`() {
        val drawableDir = drawableDir()

        BadgeDefinitions.all.forEach { definition ->
            val asset = drawableDir.resolve("${definition.iconResName}.png")
            assertTrue("Missing generated badge asset: ${asset.name}", asset.isFile)
        }
    }

    @Test
    fun `every packaged badge is square rgba and no larger than 384 pixels`() {
        val assets = packagedBadgeAssets()

        assertFalse("No packaged badge PNGs found", assets.isEmpty())
        assets.forEach { asset ->
            val header = readPngHeader(asset)
            assertEquals("Badge must be square: ${asset.name}", header.width, header.height)
            assertTrue("Badge exceeds 384px: ${asset.name} is ${header.width}px", header.width <= 384)
            assertEquals("Badge must use RGBA PNG color type 6: ${asset.name}", 6, header.colorType)
        }
    }

    private fun drawableDir(): File = File(requireNotNull(System.getProperty("user.dir")))
        .resolve("src/main/res/drawable-nodpi")

    private fun packagedBadgeAssets(): List<File> = drawableDir()
        .listFiles { file -> file.isFile && file.name.startsWith("ic_badge_") && file.extension == "png" }
        .orEmpty()
        .sortedBy(File::getName)

    private fun readPngHeader(asset: File): PngHeader {
        val bytes = asset.inputStream().use { input -> ByteArray(29).also { input.readNBytes(it, 0, it.size) } }
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        assertTrue("Invalid PNG signature: ${asset.name}", bytes.copyOfRange(0, 8).contentEquals(signature))
        assertEquals(
            "Missing PNG IHDR chunk: ${asset.name}",
            "IHDR",
            bytes.copyOfRange(12, 16).toString(StandardCharsets.US_ASCII),
        )
        return PngHeader(
            width = bytes.readBigEndianInt(16),
            height = bytes.readBigEndianInt(20),
            colorType = bytes[25].toInt() and 0xff,
        )
    }

    private fun ByteArray.readBigEndianInt(offset: Int): Int =
        ((this[offset].toInt() and 0xff) shl 24) or
            ((this[offset + 1].toInt() and 0xff) shl 16) or
            ((this[offset + 2].toInt() and 0xff) shl 8) or
            (this[offset + 3].toInt() and 0xff)

    private data class PngHeader(val width: Int, val height: Int, val colorType: Int)
}
