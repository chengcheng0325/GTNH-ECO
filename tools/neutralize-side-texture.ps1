# t88-patch: neutralise the drive-bay SIDE texture (remove blue-black tint).
# Maps the original 8-colour palette to neutral graphite grays (R~=G~=B), keeping the
# machined band layout. Coloured accents (cyan/green dots) become light neutral grays
# so the shell reads as neutral graphite - cyan/green remain only in the slot/LEDs.
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$src = "D:\DeepSeek\GTNH-ECO\src\main\resources\assets\ecoaegtnh\textures\blocks\storage_array_drives_side.png"
$dst = "D:\DeepSeek\GTNH-ECO\src\main\resources\assets\ecoaegtnh\textures\blocks\storage_array_drives_side.png"

# original hex -> neutral hex
$map = @{
    "1c2026" = "1c1d1e"  # dark border   (28,32,38)  -> (28,29,30)
    "525a66" = "545658"  # bezel        (82,90,102) -> (84,86,88)
    "285a82" = "383a3c"  # blue stripe  (40,90,130) -> (56,58,60)  <-- main blue offender
    "2a2f38" = "2b2c2d"  # dark band    (42,47,56)  -> (43,44,45)
    "404752" = "414243"  # mid band     (64,71,82)  -> (65,66,67)
    "3ddc84" = "606162"  # green dot    (61,220,132)-> (96,97,98)
    "4dc3ff" = "6e7072"  # cyan dot     (77,195,255)-> (110,112,114)
    "707a88" = "727476"  # highlight    (112,122,136)-> (114,116,118)
}

$bmp = New-Object System.Drawing.Bitmap($src)
$out = New-Object System.Drawing.Bitmap($bmp.Width, $bmp.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$changed = 0
for ($y = 0; $y -lt $bmp.Height; $y++) {
    for ($x = 0; $x -lt $bmp.Width; $x++) {
        $c = $bmp.GetPixel($x, $y)
        $key = "{0:x2}{1:x2}{2:x2}" -f $c.R, $c.G, $c.B
        if ($map.ContainsKey($key)) {
            $hex = $map[$key]
            $r = [Convert]::ToInt32($hex.Substring(0, 2), 16)
            $g = [Convert]::ToInt32($hex.Substring(2, 2), 16)
            $b = [Convert]::ToInt32($hex.Substring(4, 2), 16)
            $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $b))
            $changed++
        } else {
            $out.SetPixel($x, $y, $c)
        }
    }
}
$tmp = [System.IO.Path]::GetTempFileName() + ".png"
$out.Save($tmp, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose(); $out.Dispose()
# release the source handle before replacing
[GC]::Collect(); [GC]::WaitForPendingFinalizers()
Copy-Item $tmp $dst -Force
Remove-Item $tmp -Force
Write-Output ("remapped {0} px; saved {1}" -f $changed, $dst)
