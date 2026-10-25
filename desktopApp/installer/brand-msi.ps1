# Pone el ícono de ResuenApp en el asistente de instalación del .msi: reemplaza las imágenes
# por defecto de WiX (banner superior y panel de bienvenida/fin) en la tabla Binary.
# jpackage no permite elegirlas, así que se cambian en el .msi ya generado.
param([Parameter(Mandatory = $true)][string]$Msi)

$ErrorActionPreference = "Stop"
$dir = $PSScriptRoot
$images = @{
    "WixUI_Bmp_Banner" = Join-Path $dir "banner.bmp"
    "WixUI_Bmp_Dialog" = Join-Path $dir "dialog.bmp"
}

function Call($obj, [string]$name, [object[]]$params) {
    $obj.GetType().InvokeMember($name, [Reflection.BindingFlags]::InvokeMethod, $null, $obj, $params)
}

$installer = New-Object -ComObject WindowsInstaller.Installer
$db = Call $installer "OpenDatabase" @((Resolve-Path $Msi).Path, 1)   # 1 = modo transaccional

foreach ($name in $images.Keys) {
    # Comprueba que la fila exista: si jpackage cambiara de diálogos, mejor fallar que no hacer nada.
    $check = Call $db "OpenView" @("SELECT ``Name`` FROM ``Binary`` WHERE ``Name`` = '$name'")
    Call $check "Execute" @() | Out-Null
    $row = Call $check "Fetch" @()
    Call $check "Close" @() | Out-Null
    if ($null -eq $row) { throw "El .msi no tiene la imagen $name" }

    $view = Call $db "OpenView" @("UPDATE ``Binary`` SET ``Data`` = ? WHERE ``Name`` = '$name'")
    $rec = Call $installer "CreateRecord" @(1)
    Call $rec "SetStream" @(1, $images[$name]) | Out-Null
    Call $view "Execute" @($rec) | Out-Null
    Call $view "Close" @() | Out-Null
    Write-Output "$name <- $($images[$name])"
}
Call $db "Commit" @() | Out-Null
