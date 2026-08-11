$ErrorActionPreference = 'Stop'

# Paths are fixed relative to this script so invocation location and arguments cannot redirect cleanup.
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$buildRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot 'xiaochengxu-source\unpackage\dist\dev\mp-weixin'))
$targetRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot 'xiaochengxu'))
$pathComparison = [System.StringComparison]::OrdinalIgnoreCase

function Test-PathAtOrInsideRepository {
  param([Parameter(Mandatory = $true)][string]$Path)

  if ($Path.Equals($repoRoot, $pathComparison)) {
    return $true
  }

  $isUnderRoot = $Path.StartsWith($repoRoot, $pathComparison)
  $hasPathSeparatorAfterRoot = $Path.Length -gt $repoRoot.Length -and (
    $Path[$repoRoot.Length] -eq [System.IO.Path]::DirectorySeparatorChar -or
    $Path[$repoRoot.Length] -eq [System.IO.Path]::AltDirectorySeparatorChar
  )
  return $isUnderRoot -and $hasPathSeparatorAfterRoot
}

function Assert-PathInsideRepository {
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][string]$Label
  )

  if (-not (Test-PathAtOrInsideRepository -Path $Path) -or $Path.Equals($repoRoot, $pathComparison)) {
    throw "$Label path escaped repository root."
  }
}

function Test-PathContains {
  param(
    [Parameter(Mandatory = $true)][string]$Parent,
    [Parameter(Mandatory = $true)][string]$Child
  )

  if ($Parent.Equals($Child, $pathComparison)) {
    return $true
  }

  $prefix = $Parent.TrimEnd(
    [System.IO.Path]::DirectorySeparatorChar,
    [System.IO.Path]::AltDirectorySeparatorChar
  ) + [System.IO.Path]::DirectorySeparatorChar
  return $Child.StartsWith($prefix, $pathComparison)
}

function Assert-ItemIsNotReparsePoint {
  param(
    [Parameter(Mandatory = $true)]$Item,
    [Parameter(Mandatory = $true)][string]$Label
  )

  if (($Item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
    throw "$Label contains a reparse point: $($Item.FullName)"
  }
}

function Assert-PathComponentsHaveNoReparsePoint {
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][string]$Label
  )

  $current = [System.IO.Path]::GetFullPath($Path)
  while ($true) {
    if (-not (Test-PathAtOrInsideRepository -Path $current)) {
      throw "$Label path escaped repository root while checking path components."
    }
    if (Test-Path -LiteralPath $current) {
      $item = Get-Item -LiteralPath $current -Force
      Assert-ItemIsNotReparsePoint -Item $item -Label $Label
    }
    if ($current.Equals($repoRoot, $pathComparison)) {
      break
    }
    $current = [System.IO.Path]::GetDirectoryName($current)
  }
}

function Assert-TreeHasNoReparsePoint {
  param(
    [Parameter(Mandatory = $true)][string]$Root,
    [Parameter(Mandatory = $true)][string]$Label
  )

  Assert-PathComponentsHaveNoReparsePoint -Path $Root -Label $Label
  $rootItem = Get-Item -LiteralPath $Root -Force
  if (-not $rootItem.PSIsContainer) {
    throw "$Label is not a directory: $Root"
  }

  $pending = New-Object 'System.Collections.Generic.Queue[System.IO.DirectoryInfo]'
  $pending.Enqueue($rootItem)
  while ($pending.Count -gt 0) {
    $directory = $pending.Dequeue()
    foreach ($item in Get-ChildItem -LiteralPath $directory.FullName -Force) {
      Assert-ItemIsNotReparsePoint -Item $item -Label $Label
      if ($item.PSIsContainer) {
        $pending.Enqueue($item)
      }
    }
  }
}

function Assert-FilesHaveEqualBytes {
  param(
    [Parameter(Mandatory = $true)][string]$Expected,
    [Parameter(Mandatory = $true)][string]$Actual,
    [Parameter(Mandatory = $true)][string]$Label
  )

  $expectedBytes = [System.IO.File]::ReadAllBytes($Expected)
  $actualBytes = [System.IO.File]::ReadAllBytes($Actual)
  if ($expectedBytes.Length -ne $actualBytes.Length) {
    throw "$Label bytes changed while staging."
  }
  for ($index = 0; $index -lt $expectedBytes.Length; $index += 1) {
    if ($expectedBytes[$index] -ne $actualBytes[$index]) {
      throw "$Label bytes changed while staging."
    }
  }
}

Assert-PathInsideRepository -Path $buildRoot -Label 'Build'
Assert-PathInsideRepository -Path $targetRoot -Label 'Target'
if ((Test-PathContains -Parent $buildRoot -Child $targetRoot) -or
    (Test-PathContains -Parent $targetRoot -Child $buildRoot)) {
  throw 'Source and target paths must not overlap.'
}

# Check every existing component before even considering a filesystem mutation.
Assert-PathComponentsHaveNoReparsePoint -Path $buildRoot -Label 'Build path'
Assert-PathComponentsHaveNoReparsePoint -Path $targetRoot -Label 'Target path'

$appJson = Join-Path $buildRoot 'app.json'
$projectConfig = Join-Path $targetRoot 'project.config.json'
$privateConfig = Join-Path $targetRoot 'project.private.config.json'
# Fixed configuration paths: xiaochengxu\project.config.json and xiaochengxu\project.private.config.json.
if (-not (Test-Path -LiteralPath $appJson -PathType Leaf)) {
  throw 'HBuilderX output is missing app.json. Build the mp-weixin target first.'
}
if (-not (Test-Path -LiteralPath $projectConfig -PathType Leaf)) {
  throw 'Current WeChat project.config.json is missing.'
}

# Traverse without following reparse directories: a directory is enqueued only after its attributes pass.
Assert-TreeHasNoReparsePoint -Root $buildRoot -Label 'Build tree'
Assert-TreeHasNoReparsePoint -Root $targetRoot -Label 'Target tree'

$operationId = [System.Guid]::NewGuid().ToString('N')
$stagingRoot = Join-Path $repoRoot ".miniapp-sync-staging-$operationId"
$backupRoot = Join-Path $repoRoot ".miniapp-sync-backup-$operationId"
Assert-PathInsideRepository -Path $stagingRoot -Label 'Staging'
Assert-PathInsideRepository -Path $backupRoot -Label 'Backup'
Assert-PathComponentsHaveNoReparsePoint -Path $stagingRoot -Label 'Staging path'
Assert-PathComponentsHaveNoReparsePoint -Path $backupRoot -Label 'Backup path'
if ((Test-PathContains -Parent $buildRoot -Child $stagingRoot) -or
    (Test-PathContains -Parent $targetRoot -Child $stagingRoot) -or
    (Test-PathContains -Parent $buildRoot -Child $backupRoot) -or
    (Test-PathContains -Parent $targetRoot -Child $backupRoot)) {
  throw 'Temporary paths must not overlap source or target.'
}
if ((Test-Path -LiteralPath $stagingRoot) -or (Test-Path -LiteralPath $backupRoot)) {
  throw 'Unique staging or backup path already exists.'
}

$stagingCreated = $false
try {
  New-Item -ItemType Directory -Path $stagingRoot | Out-Null
  $stagingCreated = $true
  Assert-TreeHasNoReparsePoint -Root $stagingRoot -Label 'Staging tree'

  foreach ($item in Get-ChildItem -LiteralPath $buildRoot -Force) {
    if ($item.Name -notin @('project.config.json', 'project.private.config.json')) {
      Copy-Item -LiteralPath $item.FullName -Destination $stagingRoot -Recurse -Force
    }
  }
  Copy-Item -LiteralPath $projectConfig -Destination (Join-Path $stagingRoot 'project.config.json') -Force
  if (Test-Path -LiteralPath $privateConfig -PathType Leaf) {
    Copy-Item -LiteralPath $privateConfig -Destination (Join-Path $stagingRoot 'project.private.config.json') -Force
  }

  Assert-TreeHasNoReparsePoint -Root $stagingRoot -Label 'Staging tree'
  $stagedAppJson = Join-Path $stagingRoot 'app.json'
  $stagedProjectConfig = Join-Path $stagingRoot 'project.config.json'
  $stagedPrivateConfig = Join-Path $stagingRoot 'project.private.config.json'
  if (-not (Test-Path -LiteralPath $stagedAppJson -PathType Leaf)) {
    throw 'Staged miniapp output is missing app.json.'
  }
  if (-not (Test-Path -LiteralPath $stagedProjectConfig -PathType Leaf)) {
    throw 'Staged miniapp output is missing project.config.json.'
  }
  Assert-FilesHaveEqualBytes -Expected $projectConfig -Actual $stagedProjectConfig -Label 'project.config.json'
  if (Test-Path -LiteralPath $privateConfig -PathType Leaf) {
    if (-not (Test-Path -LiteralPath $stagedPrivateConfig -PathType Leaf)) {
      throw 'Staged miniapp output is missing project.private.config.json.'
    }
    Assert-FilesHaveEqualBytes -Expected $privateConfig -Actual $stagedPrivateConfig -Label 'project.private.config.json'
  }

  $targetMovedToBackup = $false
  $stagingMovedToTarget = $false
  try {
    Move-Item -LiteralPath $targetRoot -Destination $backupRoot
    $targetMovedToBackup = $true
    Move-Item -LiteralPath $stagingRoot -Destination $targetRoot
    $stagingMovedToTarget = $true
    $stagingCreated = $false
  } catch {
    $exchangeError = $_
    if ($targetMovedToBackup -and -not $stagingMovedToTarget) {
      if (Test-Path -LiteralPath $targetRoot) {
        throw 'Target exchange failed and rollback was blocked by an unexpected target path.'
      }
      Assert-TreeHasNoReparsePoint -Root $backupRoot -Label 'Backup tree'
      Move-Item -LiteralPath $backupRoot -Destination $targetRoot
      $targetMovedToBackup = $false
    }
    throw $exchangeError
  }

  Assert-TreeHasNoReparsePoint -Root $targetRoot -Label 'Activated target tree'
  Assert-TreeHasNoReparsePoint -Root $backupRoot -Label 'Backup tree'
  Remove-Item -LiteralPath $backupRoot -Recurse -Force
  Write-Host 'Miniapp output synchronized. WeChat project configuration was preserved.'
} finally {
  if ($stagingCreated -and (Test-Path -LiteralPath $stagingRoot)) {
    Assert-PathInsideRepository -Path $stagingRoot -Label 'Staging cleanup'
    Assert-TreeHasNoReparsePoint -Root $stagingRoot -Label 'Staging cleanup tree'
    Remove-Item -LiteralPath $stagingRoot -Recurse -Force
  }
}
