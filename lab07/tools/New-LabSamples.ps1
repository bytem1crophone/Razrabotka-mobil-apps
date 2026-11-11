[CmdletBinding()]
param(
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$sampleDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\samples'))
[IO.Directory]::CreateDirectory($sampleDirectory) | Out-Null
$samplePath = Join-Path $sampleDirectory 'lab07_long_20mb.txt'
$targetSize = 20MB
$utf8Encoding = [Text.UTF8Encoding]::new($false)
$sampleLine = "Лабораторная работа № 7. Большой UTF-8-файл для предпросмотра первых 500 символов.`n"
$sampleBlock = $utf8Encoding.GetBytes($sampleLine * 128)
$fileMode = if ($Force) { [IO.FileMode]::Create } else { [IO.FileMode]::CreateNew }

# Без -Force существующий образец не перезаписывается.
$sampleStream = [IO.FileStream]::new($samplePath, $fileMode, [IO.FileAccess]::Write)
try {
    while (($targetSize - $sampleStream.Position) -ge $sampleBlock.Length) {
        $sampleStream.Write($sampleBlock, 0, $sampleBlock.Length)
    }

    # ASCII-хвост даёт точный размер и не обрывает многобайтный UTF-8-символ.
    $remainingBytes = [int]($targetSize - $sampleStream.Position)
    if ($remainingBytes -gt 0) {
        $padding = $utf8Encoding.GetBytes((' ' * ($remainingBytes - 1)) + "`n")
        $sampleStream.Write($padding, 0, $padding.Length)
    }
} finally {
    $sampleStream.Dispose()
}

Write-Output "Создан: $samplePath"
Write-Output "Размер: $targetSize байт (20 МиБ), UTF-8 без BOM."
