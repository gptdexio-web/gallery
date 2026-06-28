@echo off
set "JAVA_HOME=C:\Users\AG\jdk\jdk-21.0.3"
set "ANDROID_HOME=C:\Users\AG\Android\Sdk"
set "PATH=%ANDROID_HOME%\cmdline-tools\latest\bin;%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\emulator;%PATH%"
call "%*"
