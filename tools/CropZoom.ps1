param(
    [string]$Source,          # screenshot to crop
    [int]$X, [int]$Y,         # top-left of the region, in image pixels
    [int]$Width, [int]$Height,
    [int]$Zoom = 4,           # nearest-neighbour magnification, so individual pixels stay visible
    [string]$Out              # where to write the magnified crop
)
# Cuts a region out of a screenshot and blows it up without smoothing. Used to judge how edges
# and glyphs actually landed on the pixel grid, which a full-size screenshot hides.
Add-Type -AssemblyName System.Drawing
$src = [System.Drawing.Bitmap]::FromFile($Source)
$crop = New-Object System.Drawing.Bitmap ($Width * $Zoom), ($Height * $Zoom)
$g = [System.Drawing.Graphics]::FromImage($crop)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$destRect = New-Object System.Drawing.Rectangle 0, 0, ($Width * $Zoom), ($Height * $Zoom)
$srcRect = New-Object System.Drawing.Rectangle $X, $Y, $Width, $Height
$g.DrawImage($src, $destRect, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
$g.Dispose()
$crop.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$crop.Dispose(); $src.Dispose()
Write-Output "wrote $Out"
