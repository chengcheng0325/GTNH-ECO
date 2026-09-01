Add-Type -AssemblyName System.Drawing
$p = (Resolve-Path 'tools\ecal-textures-preview.png').Path
$bmp = [System.Drawing.Bitmap]::new($p)
function Get-RunsAt([int]$yy) {
    $runs = New-Object System.Collections.ArrayList
    $in = $false; $s = 0
    for ($xx = 0; $xx -lt 650; $xx++) {
        $c = $bmp.GetPixel($xx, $yy)
        $avg = ($c.R + $c.G + $c.B) / 3
        $isPanel = ($avg -ge 120 -and $avg -le 215)
        if ($isPanel -and -not $in) { $in = $true; $s = $xx }
        elseif (-not $isPanel -and $in) { $in = $false; [void]$runs.Add(@($s, ($xx - 1))) }
    }
    if ($in) { [void]$runs.Add(@($s, 649)) }
    return $runs
}
foreach ($yy in 20, 70, 120, 180, 230, 280, 335, 360, 380, 400, 420, 440) {
    $desc = (Get-RunsAt $yy | ForEach-Object { "$($_[0])..$($_[1])(w=$($_[1] - $_[0] + 1))" }) -join ', '
    "y=$yy : $desc"
}
$bmp.Dispose()
