# SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
# SPDX-License-Identifier: Apache-2.0

<#
.SYNOPSIS
    Installs the Community Edition JAR over an existing RTX Remix for Minecraft
    game directory, or restores what it replaced.

.DESCRIPTION
    Install moves the current rtx-remix-renderer JAR out of mods/, copies the
    Community Edition JAR in, and keeps a backup of the replaced JAR and of
    the rtx-remix/ conf payload under community-edition-backup/<timestamp>/.
    On its next launch the mod re-extracts its runtime and upgrades shipped
    confs that were never edited; edited confs are left alone.

    Restore puts the newest backup back: the replaced JAR and the rtx-remix/
    payload, and removes the Community Edition JAR.

.EXAMPLE
    .\community-install.ps1 -GameDir 'D:\PCL\.minecraft\versions\RTX' -Jar '.\build\distributions\rtx-remix-renderer-0.1.0-ce.1-....jar'

.EXAMPLE
    .\community-install.ps1 -GameDir 'D:\PCL\.minecraft\versions\RTX' -Restore
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$GameDir,
    [string]$Jar,
    [switch]$Restore
)

$ErrorActionPreference = 'Stop'
$mods = Join-Path $GameDir 'mods'
$backupRoot = Join-Path $GameDir 'community-edition-backup'
if (-not (Test-Path $mods)) { throw "No mods folder in $GameDir" }

if ($Restore) {
    $latest = Get-ChildItem $backupRoot -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if (-not $latest) { throw "No backup under $backupRoot" }
    Get-ChildItem $mods -Filter 'rtx-remix-renderer-*.jar' | Remove-Item
    Get-ChildItem $latest.FullName -Filter 'rtx-remix-renderer-*.jar' | Copy-Item -Destination $mods
    $payload = Join-Path $latest.FullName 'rtx-remix'
    if (Test-Path $payload) {
        $live = Join-Path $GameDir 'rtx-remix'
        if (Test-Path $live) { Remove-Item $live -Recurse }
        Copy-Item $payload -Destination $GameDir -Recurse
    }
    Write-Host "Restored $($latest.Name). The next launch re-extracts the original runtime."
    return
}

if (-not $Jar -or -not (Test-Path $Jar)) { throw 'Pass -Jar with the Community Edition JAR to install.' }
$backup = Join-Path $backupRoot (Get-Date -Format 'yyyyMMdd-HHmmss')
New-Item -ItemType Directory -Force $backup | Out-Null

Get-ChildItem $mods -Filter 'rtx-remix-renderer-*.jar' | Move-Item -Destination $backup
$payload = Join-Path $GameDir 'rtx-remix'
if (Test-Path $payload) { Copy-Item $payload -Destination $backup -Recurse }
Copy-Item $Jar -Destination $mods

Write-Host "Installed $(Split-Path $Jar -Leaf)."
Write-Host "Backup of the replaced files: $backup"
