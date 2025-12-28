# Desktop App Icons

This directory should contain the application window icon files.

## Required Files

Place the following files in this directory:
- `app-icon.png` - Main app icon (recommended: 256x256 or 512x512 pixels)
- `app-icon.ico` (Windows) - Icon file for Windows (contains multiple sizes: 16x16, 32x32, 48x48, 256x256)
- `app-icon.icns` (macOS) - Icon file for macOS (contains multiple sizes)

## Generating Icons from AppLogo

The app branding logo is defined in:
`shared/src/commonMain/kotlin/com/financeapp/ui/components/branding/AppLogo.kt`

### Option 1: Use Online Icon Generators

1. Use the screenshot tool to capture the app logo from the About dialog
2. Upload to an icon generator:
   - https://www.icoconverter.com/ (PNG to ICO/ICNS)
   - https://icon.kitchen/ (Android/iOS/Desktop icons)
   - https://www.appicon.co/ (macOS icons)

### Option 2: Generate Programmatically (Recommended)

Use Compose Desktop to render the logo to a bitmap and export as PNG:

```kotlin
// Add to Main.kt temporarily or create a separate utility
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import com.financeapp.ui.components.branding.AppLogo
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

fun generateAppIcon() {
    // Create bitmap from AppLogo composable
    val bitmap = renderComposableToBitmap(
        width = 512,
        height = 512
    ) {
        AppLogo(size = 512.dp)
    }

    // Save as PNG
    val file = File("desktopApp/src/desktopMain/resources/icons/app-icon.png")
    file.parentFile.mkdirs()
    file.writeBytes(
        bitmap.asSkiaBitmap().encodeToData(EncodedImageFormat.PNG)!!.bytes
    )

    println("Icon generated: ${file.absolutePath}")
}

// Helper function to render composable to bitmap
fun renderComposableToBitmap(
    width: Int,
    height: Int,
    content: @Composable () -> Unit
): ImageBitmap {
    // Implementation depends on Compose Desktop version
    // See: https://github.com/JetBrains/compose-multiplatform/tree/master/tutorials/Image_And_Icons_Manipulations
}
```

### Option 3: Use ImageMagick/GIMP

If you have the PNG file:

```bash
# Install ImageMagick
brew install imagemagick  # macOS
sudo apt install imagemagick  # Linux

# Generate ICO for Windows (multiple sizes)
convert app-icon.png -define icon:auto-resize=256,128,64,48,32,16 app-icon.ico

# Generate ICNS for macOS
mkdir app-icon.iconset
sips -z 16 16     app-icon.png --out app-icon.iconset/icon_16x16.png
sips -z 32 32     app-icon.png --out app-icon.iconset/icon_16x16@2x.png
sips -z 32 32     app-icon.png --out app-icon.iconset/icon_32x32.png
sips -z 64 64     app-icon.png --out app-icon.iconset/icon_32x32@2x.png
sips -z 128 128   app-icon.png --out app-icon.iconset/icon_128x128.png
sips -z 256 256   app-icon.png --out app-icon.iconset/icon_128x128@2x.png
sips -z 256 256   app-icon.png --out app-icon.iconset/icon_256x256.png
sips -z 512 512   app-icon.png --out app-icon.iconset/icon_256x256@2x.png
iconutil -c icns app-icon.iconset
```

## Quick Start (Placeholder)

If you need a placeholder icon quickly:

1. Take a screenshot of the app logo from Settings > About
2. Crop to square
3. Resize to 256x256 pixels
4. Save as `app-icon.png` in this directory
5. Restart the app to see the icon in the window title bar

## Implementation

The icon is loaded in `Main.kt`:

```kotlin
Window(
    onCloseRequest = ::exitApplication,
    title = "FinanceApp",
    icon = painterResource("icons/app-icon.png")
) {
    App()
}
```

## Icon Design Guidelines

Based on the AppLogo component:
- **Design**: Circular logo with $ symbol
- **Colors**: Uses Material 3 primary color (blue)
- **Size**: Scalable vector graphics (rendered at various sizes)
- **Background**: Transparent (PNG with alpha channel)
- **Style**: Modern, minimal, professional

For best results:
- Use 512x512 or 1024x1024 base resolution
- Export with transparent background
- Ensure good visibility at small sizes (16x16, 32x32)
- Test on both light and dark system themes
