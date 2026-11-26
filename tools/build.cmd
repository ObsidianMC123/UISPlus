@echo off
REM Dung: tools\build.cmd <gradle tasks...>   (log ra tools\build_out.txt)
set "JAVA_HOME=C:\Program Files\Java\jdk-25.0.4"
set "GRADLE=C:\Users\PC\.gradle\wrapper\dists\gradle-9.8.2-bin\8wepis63pzuhrlfaiy04bagwu\gradle-9.8.2\bin\gradle.bat"
cd /d D:\mcproject\UISApp
if exist gradlew.bat (
  call gradlew.bat %* --console=plain > tools\build_out.txt 2>&1
) else (
  call "%GRADLE%" %* --console=plain > tools\build_out.txt 2>&1
)
echo EXIT=%ERRORLEVEL% >> tools\build_out.txt
echo [DONE-MARKER] >> tools\build_out.txt
