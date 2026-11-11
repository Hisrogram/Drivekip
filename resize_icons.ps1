Add-Type -AssemblyName System.Drawing

$srcPath = "C:\Users\heeseung.lee\.gemini\antigravity\brain\9aa41b60-ce6b-40dc-ab7e-e212947e1520\drivekip_app_icon_1794357327034.jpg"
$baseRes = "C:\Users\heeseung.lee\.gemini\antigravity\scratch\DriveKip\app\src\main\res"

$srcImage = [System.Drawing.Image]::FromFile($srcPath)

function Resize-Image($img, $w, $h) {
    $bmp = New-Object System.Drawing.Bitmap($w, $h)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.DrawImage($img, 0, 0, $w, $h)
    $g.Dispose()
    return $bmp
}

function Create-Foreground($img, $totalSize, $contentRatio) {
    $bmp = New-Object System.Drawing.Bitmap($totalSize, $totalSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

    $contentSize = [int]($totalSize * $contentRatio)
    $offset = [int](($totalSize - $contentSize) / 2)

    $g.DrawImage($img, $offset, $offset, $contentSize, $contentSize)
    $g.Dispose()
    return $bmp
}

# 1. Playstore icon 512x512
$playstore = Resize-Image $srcImage 512 512
$playstore.Save("$baseRes\..\ic_launcher-playstore.png", [System.Drawing.Imaging.ImageFormat]::Png)
$playstore.Dispose()

# 2. DPI maps
$dpis = @{
    "mipmap-mdpi" = @{ size = 48; fgSize = 108 }
    "mipmap-hdpi" = @{ size = 72; fgSize = 162 }
    "mipmap-xhdpi" = @{ size = 96; fgSize = 216 }
    "mipmap-xxhdpi" = @{ size = 144; fgSize = 324 }
    "mipmap-xxxhdpi" = @{ size = 192; fgSize = 432 }
}

foreach ($dpi in $dpis.Keys) {
    $dir = "$baseRes\$dpi"
    if (!(Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force }

    # Remove old webp files
    Get-ChildItem -Path $dir -Filter "*.webp" | Remove-Item -Force

    $size = $dpis[$dpi].size
    $fgSize = $dpis[$dpi].fgSize

    # Standard launcher
    $icon = Resize-Image $srcImage $size $size
    $icon.Save("$dir\ic_launcher.png", [System.Drawing.Imaging.ImageFormat]::Png)
    $icon.Save("$dir\ic_launcher_round.png", [System.Drawing.Imaging.ImageFormat]::Png)
    $icon.Dispose()

    # Foreground adaptive
    $fg = Create-Foreground $srcImage $fgSize 0.72
    $fg.Save("$dir\ic_launcher_foreground.png", [System.Drawing.Imaging.ImageFormat]::Png)
    $fg.Dispose()

    Write-Host "Generated icons for $dpi - size $size, fgSize $fgSize"
}

$srcImage.Dispose()
Write-Host "All icons generated successfully!"
