Get-CimInstance Win32_Process -Filter "name='powershell.exe' or name='java.exe' or name='curl.exe'" | ForEach-Object { "$($_.ProcessId) | $($_.CommandLine)" }
