@echo off
set "JAVA_HOME=C:\Users\AG\jdk17\jdk-17.0.11"
set "ANDROID_HOME=C:\Users\AG\Android\Sdk"

:: Accept all licenses
powershell -Command "'y','y','y','y','y','y','y','y','y','y' | & '%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat' --sdk_root='%ANDROID_HOME%' --licenses"

:: Install required packages
call "%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="%ANDROID_HOME%" --install "platforms;android-35" "build-tools;35.0.0" "platform-tools"
