# LED hue analysis for drive bay previews (t88 spot check).
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

foreach ($f in @("drive-bay-preview.png", "drive-bay-preview-filled.png")) {
    $p = Join-Path "D:\DeepSeek\GTNH-ECO\tools" $f
    $bmp = New-Object System.Drawing.Bitmap($p)
    $green = 0; $cyan = 0
    $sampG = New-Object System.Collections.ArrayList
    $sampC = New-Object System.Collections.ArrayList
    for ($y = 0; $y -lt $bmp.Height; $y += 2) {
        for ($x = 0; $x -lt $bmp.Width; $x += 2) {
            $c = $bmp.GetPixel($x, $y)
            if ($c.A -le 40) { continue }
            $mx = [Math]::Max($c.R, [Math]::Max($c.G, $c.B))
            $mn = [Math]::Min($c.R, [Math]::Min($c.G, $c.B))
            $d = $mx - $mn
            if ($d -lt 25 -or $mx -lt 60) { continue }
            $h = 0.0
            if ($mx -eq $c.R) { $h = (($c.G - $c.B) / $d) % 6 }
            elseif ($mx -eq $c.G) { $h = (($c.B - $c.R) / $d) + 2 }
            else { $h = (($c.R - $c.G) / $d) + 4 }
            $h = $h * 60
            if ($h -lt 0) { $h += 360 }
            if ($h -ge 80 -and $h -lt 160) {
                $green++
                if ($sampG.Count -lt 3) { [void]$sampG.Add(("h={0} #{1:x2}{2:x2}{3:x2}" -f [int]$h, $c.R, $c.G, $c.B)) }
            } elseif ($h -ge 160 -and $h -lt 210) {
                $cyan++
                if ($sampC.Count -lt 3) { [void]$sampC.Add(("h={0} #{1:x2}{2:x2}{3:x2}" -f [int]$h, $c.R, $c.G, $c.B)) }
            }
        }
    }
    Write-Output ("=== {0} : green-hue={1} cyan-hue={2} ===" -f $f, $green, $cyan)
    Write-Output ("  green: " + ($sampG -join ", "))
    Write-Output ("  cyan:  " + ($sampC -join ", "))
    $bmp.Dispose()
}
