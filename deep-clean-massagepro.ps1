Write-Host "`n⛏️  Запуск глубокой очистки Android-проекта 'MassagePRO'..." -ForegroundColor Cyan

$projectRoot = Get-Location

$pathsToDelete = @(
    ".gradle",
    ".idea",
    "build",
    "app/.cxx",
    "app/build",
    "app/schemas",
    "app/build/tmp",
    "$env:USERPROFILE\.gradle\caches",
    "$env:USERPROFILE\.android\build-cache"
)

foreach ($path in $pathsToDelete) {
    $fullPath = Join-Path $projectRoot $path
    if (Test-Path $fullPath) {
        Write-Host "🧹 Удаление: $fullPath"
        Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $fullPath
    }
}

Write-Host "🧼 Поиск и удаление *.iml файлов"
Get-ChildItem -Path $projectRoot -Recurse -Include *.iml | Remove-Item -Force -ErrorAction SilentlyContinue

Write-Host "🧠 Очистка Kotlin-компилятора"
Get-ChildItem -Path $projectRoot -Recurse -Include *.class,*.kotlin_module,*.kotlin_metadata | Remove-Item -Force -ErrorAction SilentlyContinue

Write-Host "`n✅ Очистка завершена. Теперь открой Android Studio и выбери:" -ForegroundColor Green
Write-Host "  File -> New -> Import Project -> укажи build.gradle в корне." -ForegroundColor Yellow
Write-Host "  Затем: Build -> Clean Project -> Rebuild Project' -ForegroundColor Yellow
