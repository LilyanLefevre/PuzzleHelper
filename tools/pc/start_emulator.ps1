# Starts the headless API 34 emulator outside the ssh session (OpenSSH kills its children on logout).
$emu = "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe"
$cmd = "cmd /c `"$emu`" -avd puzzle34 -no-window -no-audio -no-boot-anim -no-snapshot-save -gpu swiftshader_indirect > C:\dev\emu-$(Get-Date -Format HHmmss).log 2>&1"
(Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{CommandLine=$cmd; CurrentDirectory="C:\dev"}).ReturnValue
