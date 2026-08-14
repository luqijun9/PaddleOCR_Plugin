$watcher = New-Object System.IO.FileSystemWatcher
$watcher.Path = "C:\Users\LP\Desktop\OcrPlugin\app\src"
$watcher.IncludeSubdirectories = $true
$watcher.EnableRaisingEvents = $true

Register-ObjectEvent $watcher "Changed" -Action {
    Start-Sleep -Seconds 2
    Write-Host "检测到文件变化，开始构建..."
    Set-Location "C:\Users\LP\Desktop\OcrPlugin"
    ./gradlew assembleDebug
    Write-Host "构建完成，APK已更新！"
}
