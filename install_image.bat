@echo off
set "JAVA_HOME=C:\Users\AG\jdk\jdk-21.0.3"
echo y | "C:\Users\AG\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="C:\Users\AG\Android\Sdk" --install "system-images;android-34;google_apis;x86_64"
