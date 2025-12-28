package com.financeapp.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.financeapp.ui.components.branding.AppLogo
import com.financeapp.ui.theme.FinanceAppTheme
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import java.io.File

/**
 * Utility to generate app icon from the AppLogo composable
 *
 * Run this file to generate app-icon.png in the resources/icons directory.
 *
 * Usage:
 * 1. Run this main function
 * 2. The icon will be generated at: desktopApp/src/desktopMain/resources/icons/app-icon.png
 * 3. Use online tools to convert to ICO (Windows) and ICNS (macOS) if needed
 */
fun main() {
    println("Generating app icon...")

    // Create a bitmap programmatically
    val iconSize = 512
    val surface = Surface.makeRasterN32Premul(iconSize, iconSize)
    val canvas = surface.canvas

    // Clear with transparent background
    canvas.clear(org.jetbrains.skia.Color.TRANSPARENT)

    // Note: Rendering Compose to bitmap requires more complex setup
    // For now, this is a placeholder. See README.md for alternative approaches.

    println("""
        Icon generation requires additional setup.

        Please use one of these approaches:

        1. Screenshot method (Easiest):
           - Run the app with: ./gradlew :desktopApp:run
           - Navigate to Settings > About
           - Take a screenshot of the logo
           - Crop to square and save as app-icon.png in desktopApp/src/desktopMain/resources/icons/

        2. Online generator:
           - Take a screenshot of the logo
           - Upload to https://icon.kitchen/ or https://www.icoconverter.com/
           - Download generated icons

        3. Manual drawing:
           - Create a 512x512 PNG
           - Draw a circle with the dollar sign (see AppLogo.kt for reference)
           - Save as app-icon.png

        The icon has been configured in Main.kt and will load automatically when present.
    """.trimIndent())
}

/**
 * Alternative: Show the logo in a window for easy screenshot
 */
fun showLogoForScreenshot() {
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "App Icon - Take Screenshot",
            resizable = false
        ) {
            FinanceAppTheme {
                Box(modifier = Modifier.size(512.dp)) {
                    AppLogo(size = 480.dp)
                }
            }
        }
    }
}
