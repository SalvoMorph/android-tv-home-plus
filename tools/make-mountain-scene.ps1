<#
.SYNOPSIS
  Prepares a photograph as a picture for the Mountain theme: crops it to 16:9, scales it to 1600x900, saves it as
  launcher/app/src/main/res/drawable-nodpi/scenic_<Name>.jpg and traces the mountains' skyline (the edge under the
  smooth sky) into values/scenic_skyline_<Name>.xml, so the clock can sit behind the mountains.

.DESCRIPTION
  PowerShell 7 (pwsh). The crop is in the photograph's own pixels: -cx -cy -cw -ch, or leave -cw at 0 for the whole
  picture. Put the highest peak at the middle of the crop and about 40 % down, where the clock covers it best.
  The skyline is found by looking for a sharp edge under a smooth sky (-Edge, 0.11 by default; lower for a pale, misty
  picture). A preview with the traced line in red is written to your temp folder: look at it. Where the sky and the
  mountains are the same colour (mist) no edge exists, and the line has to be traced by hand into the XML.

  To offer the picture, add it to MountainScenes.java (a new Scene with this Name) and a name string; the picture is
  only offered when its drawable is in the build. Respect the picture's licence: the repository ships only Mountain's.

.EXAMPLE
  pwsh tools/make-mountain-scene.ps1 -SrcPath my-photo.jpg -cx 0 -cy 0 -cw 1560 -ch 877 -Name alpenglow
#>
param([string]$SrcPath,[int]$cx,[int]$cy,[int]$cw,[int]$ch,[string]$Name,[double]$Edge=0.11,[int]$Gap=4,[int]$Ahead=8,[int]$Open=24)
Add-Type -AssemblyName System.Drawing
if (-not ('SkyX' -as [type])) { Add-Type -TypeDefinition @"
using System;
public static class SkyX {
  static double D(byte[] px,int stride,int x,int y1,int y2){ int a=y1*stride+x*3,b=y2*stride+x*3; return (Math.Abs(px[a]-px[b])+Math.Abs(px[a+1]-px[b+1])+Math.Abs(px[a+2]-px[b+2]))/765.0; }
  public static int[] Skyline(byte[] px,int stride,int W,int H,double edge,int gap,int ahead){
    int[] top=new int[W];
    for(int x=0;x<W;x++){ int found=H-1;
      for(int y=gap;y<H-ahead;y++){ if(D(px,stride,x,y,y-gap)>edge && D(px,stride,x,y+ahead,y-gap)>edge*0.8){ found=y; break; } }
      top[x]=found; }
    return top; }
}
"@ }
$work=$env:TEMP; $res=(Resolve-Path "$PSScriptRoot\..\launcher\app\src\main\res").Path
$img=[System.Drawing.Image]::FromFile($SrcPath)
if($cw -le 0){ $cx=0;$cy=0;$cw=$img.Width;$ch=$img.Height }
$out=New-Object System.Drawing.Bitmap 1600,900; $g=[System.Drawing.Graphics]::FromImage($out); $g.InterpolationMode='HighQualityBicubic'; $g.SmoothingMode='HighQuality'
$g.DrawImage($img,(New-Object System.Drawing.Rectangle 0,0,1600,900),(New-Object System.Drawing.Rectangle $cx,$cy,$cw,$ch),[System.Drawing.GraphicsUnit]::Pixel); $g.Dispose(); $img.Dispose()
$enc=[System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object MimeType -eq 'image/jpeg'
$p=[System.Drawing.Imaging.EncoderParameters]::new(1); $p.Param[0]=[System.Drawing.Imaging.EncoderParameter]::new([System.Drawing.Imaging.Encoder]::Quality,[long]86)
$out.Save("$res\drawable-nodpi\scenic_$Name.jpg",$enc,$p)
$data=$out.LockBits((New-Object System.Drawing.Rectangle 0,0,1600,900),[System.Drawing.Imaging.ImageLockMode]::ReadOnly,[System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$bytes=New-Object byte[] ($data.Stride*900); [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0,$bytes,0,$bytes.Length); $stride=$data.Stride; $out.UnlockBits($data)
$top=[SkyX]::Skyline($bytes,$stride,1600,900,$Edge,$Gap,$Ahead); $n=1600
$mx=New-Object int[] $n; for($x=0;$x -lt $n;$x++){ $m=0; for($k=[Math]::Max(0,$x-$Open);$k -le [Math]::Min($n-1,$x+$Open);$k++){ if($top[$k] -gt $m){$m=$top[$k]} }; $mx[$x]=$m }
$op=New-Object int[] $n; for($x=0;$x -lt $n;$x++){ $m=99999; for($k=[Math]::Max(0,$x-$Open);$k -le [Math]::Min($n-1,$x+$Open);$k++){ if($mx[$k] -lt $m){$m=$mx[$k]} }; $op[$x]=$m }
$items=@(); for($xo=0;$xo -le 1600;$xo+=4){ $xs=[Math]::Min(1599,$xo); $pm=[int][Math]::Max(0,[Math]::Min(1000,$op[$xs]/900*1000)); $items += "        <item>$pm</item>" }
$xml="<?xml version=`"1.0`" encoding=`"utf-8`"?>`n<!-- Skyline of scenic_$Name.jpg for the Mountain theme's clock that sits behind the mountains: 1/1000 of the picture's height every 4 px of its 1600 px width (401 values). -->`n<resources>`n    <integer-array name=`"scenic_skyline_$Name`">`n" + ($items -join "`n") + "`n    </integer-array>`n</resources>`n"
[IO.File]::WriteAllText("$res\values\scenic_skyline_$Name.xml",$xml,(New-Object Text.UTF8Encoding $false))
$ov=New-Object System.Drawing.Bitmap $out; $pen=New-Object System.Drawing.Pen ([System.Drawing.Color]::Red),3; $gg=[System.Drawing.Graphics]::FromImage($ov)
for($x=1;$x -lt $n;$x++){ $gg.DrawLine($pen,$x-1,$op[$x-1],$x,$op[$x]) }; $gg.Dispose()
$sm=New-Object System.Drawing.Bitmap 1200,675; $g2=[System.Drawing.Graphics]::FromImage($sm); $g2.DrawImage($ov,0,0,1200,675); $g2.Dispose(); $sm.Save("$work\skyline_$Name.png")
"Wrote scenic_$Name.jpg and scenic_skyline_$Name.xml; check the skyline in $work\skyline_$Name.png"

