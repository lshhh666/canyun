[CmdletBinding()]
param(
  [string]$BuildRelativePath = 'xiaochengxu-source\unpackage\dist\dev\mp-weixin'
)

$ErrorActionPreference = 'Stop'

# This script is intentionally rooted at its own worktree, never the caller's directory.
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$buildRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::Combine($repoRoot, $BuildRelativePath))
$targetRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot 'xiaochengxu'))

function Assert-PathInsideRepository {
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][string]$Label
  )

  $isUnderRoot = $Path.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)
  $hasPathSeparatorAfterRoot = $Path.Length -gt $repoRoot.Length -and (
    $Path[$repoRoot.Length] -eq [System.IO.Path]::DirectorySeparatorChar -or
    $Path[$repoRoot.Length] -eq [System.IO.Path]::AltDirectorySeparatorChar
  )

  if (-not $isUnderRoot -or -not $hasPathSeparatorAfterRoot) {
    throw "$Label path escaped repository root."
  }
}

Assert-PathInsideRepository -Path $buildRoot -Label 'Build'
Assert-PathInsideRepository -Path $targetRoot -Label 'Target'

$appJson = Join-Path $buildRoot 'app.json'
$projectConfig = Join-Path $targetRoot 'project.config.json'
$privateConfig = Join-Path $targetRoot 'project.private.config.json'

# Preserved files resolve to xiaochengxu\project.config.json and xiaochengxu\project.private.config.json.
if (-not (Test-Path -LiteralPath $appJson -PathType Leaf)) {
  throw 'HBuilderX output is missing app.json. Build the mp-weixin target first.'
}
if (-not (Test-Path -LiteralPath $projectConfig -PathType Leaf)) {
  throw 'Current WeChat project.config.json is missing.'
}

$projectConfigBytes = [System.IO.File]::ReadAllBytes($projectConfig)
$privateConfigBytes = if (Test-Path -LiteralPath $privateConfig -PathType Leaf) {
  [System.IO.File]::ReadAllBytes($privateConfig)
} else {
  $null
}

Get-ChildItem -LiteralPath $targetRoot -Force |
  Where-Object { $_.Name -notin @('project.config.json', 'project.private.config.json') } |
  ForEach-Object { Remove-Item -LiteralPath $_.FullName -Recurse -Force }

Get-ChildItem -LiteralPath $buildRoot -Force |
  Where-Object { $_.Name -notin @('project.config.json', 'project.private.config.json') } |
  ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $targetRoot -Recurse -Force }

[System.IO.File]::WriteAllBytes($projectConfig, $projectConfigBytes)
if ($null -ne $privateConfigBytes) {
  [System.IO.File]::WriteAllBytes($privateConfig, $privateConfigBytes)
}

Write-Host 'Miniapp output synchronized. WeChat project configuration was preserved.'
