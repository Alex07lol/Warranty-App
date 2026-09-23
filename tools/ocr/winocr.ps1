param([int]$Scale = 1, [string]$Out, [Parameter(ValueFromRemainingArguments = $true)][string[]]$Paths)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Runtime.WindowsRuntime | Out-Null

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Await($op, $type) {
    $m = $asTaskGeneric.MakeGenericMethod($type)
    $t = $m.Invoke($null, @($op))
    $t.Wait(-1) | Out-Null
    return $t.Result
}

[Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime] | Out-Null
[Windows.Graphics.Imaging.BitmapDecoder, Windows.Graphics.Imaging, ContentType = WindowsRuntime] | Out-Null
[Windows.Graphics.Imaging.BitmapTransform, Windows.Graphics.Imaging, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.Ocr.OcrEngine, Windows.Foundation, ContentType = WindowsRuntime] | Out-Null

$engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages()
if ($null -eq $engine) { Write-Error "No OCR engine available"; exit 2 }

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("### ENGINE lang=$($engine.RecognizerLanguage.LanguageTag) scale=$Scale")

foreach ($p in $Paths) {
    $full = (Resolve-Path -LiteralPath $p).Path
    [void]$sb.AppendLine("### FILE $([System.IO.Path]::GetFileName($full))")
    $file = Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync($full)) ([Windows.Storage.StorageFile])
    $stream = Await ($file.OpenAsync([Windows.Storage.FileAccessMode]::Read)) ([Windows.Storage.Streams.IRandomAccessStream])
    $decoder = Await ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream)) ([Windows.Graphics.Imaging.BitmapDecoder])

    if ($Scale -gt 1) {
        $transform = New-Object Windows.Graphics.Imaging.BitmapTransform
        $transform.ScaledWidth = [uint32]($decoder.PixelWidth * $Scale)
        $transform.ScaledHeight = [uint32]($decoder.PixelHeight * $Scale)
        $transform.InterpolationMode = [Windows.Graphics.Imaging.BitmapInterpolationMode]::Fant
        $bitmap = Await ($decoder.GetSoftwareBitmapAsync([Windows.Graphics.Imaging.BitmapPixelFormat]::Bgra8, [Windows.Graphics.Imaging.BitmapAlphaMode]::Premultiplied, $transform, [Windows.Graphics.Imaging.ExifOrientationMode]::IgnoreExifOrientation, [Windows.Graphics.Imaging.ColorManagementMode]::DoNotColorManage)) ([Windows.Graphics.Imaging.SoftwareBitmap])
    } else {
        $bitmap = Await ($decoder.GetSoftwareBitmapAsync([Windows.Graphics.Imaging.BitmapPixelFormat]::Bgra8, [Windows.Graphics.Imaging.BitmapAlphaMode]::Premultiplied)) ([Windows.Graphics.Imaging.SoftwareBitmap])
    }

    $result = Await ($engine.RecognizeAsync($bitmap)) ([Windows.Media.Ocr.OcrResult])
    foreach ($line in $result.Lines) { [void]$sb.AppendLine($line.Text) }
    $bitmap.Dispose()
    $stream.Dispose()
}

if ($Out) {
    # UTF-8 keeps OCR look-alike symbols (₹ for B) that console redirection would destroy.
    [System.IO.File]::WriteAllText($Out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
} else {
    Write-Output $sb.ToString()
}
