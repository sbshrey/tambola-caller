# Optional, Windows-only asset utility. The generated icons are checked in.
Add-Type -AssemblyName System.Drawing
$iconDirectory = Join-Path $PSScriptRoot '..\icons'
New-Item -ItemType Directory -Path $iconDirectory -Force | Out-Null
foreach ($size in @(192, 512)) {
    $bitmap = New-Object System.Drawing.Bitmap($size, $size)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $pink = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml('#a91e59'))
    $cream = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml('#fff5cd'))
    $graphics.Clear($pink.Color)
    $graphics.ScaleTransform(($size / 192.0), ($size / 192.0))
    $graphics.TranslateTransform(96, 96)
    $graphics.RotateTransform(-12)
    $graphics.TranslateTransform(-96, -96)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddArc(39, 39, 48, 48, 180, 90)
    $path.AddArc(105, 39, 48, 48, 270, 90)
    $path.AddArc(105, 105, 48, 48, 0, 90)
    $path.AddArc(39, 105, 48, 48, 90, 90)
    $path.CloseFigure()
    $graphics.FillPath($cream, $path)
    foreach ($point in @(@(68, 68), @(124, 68), @(96, 96), @(68, 124), @(124, 124))) {
        $graphics.FillEllipse($pink, ($point[0] - 10), ($point[1] - 10), 20, 20)
    }
    $bitmap.Save((Join-Path $iconDirectory "icon-$size.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $path.Dispose()
    $graphics.Dispose()
    $bitmap.Dispose()
    $pink.Dispose()
    $cream.Dispose()
}
