@echo off
chcp 65001 >nul
title Küfür Alarmı - Kurulum
cd /d "%~dp0"
echo.
echo  === Küfür Alarmı kuruluyor ===
echo.

rem --- Python 3.12 var mı? ---
py -3.12 --version >nul 2>&1
if errorlevel 1 (
    echo  Python 3.12 bulunamadı, indiriliyor...
    winget install -e --id Python.Python.3.12 --scope user --silent --accept-package-agreements --accept-source-agreements
    if errorlevel 1 (
        echo  winget olmadı, doğrudan indiriliyor...
        powershell -NoProfile -Command "Invoke-WebRequest https://www.python.org/ftp/python/3.12.7/python-3.12.7-amd64.exe -OutFile $env:TEMP\py312.exe"
        "%TEMP%\py312.exe" /quiet InstallAllUsers=0 PrependPath=1 Include_launcher=1 Include_tcltk=1
    )
)
py -3.12 --version >nul 2>&1
if errorlevel 1 (
    echo.
    echo  Python kurulamadı. Bilgisayarı yeniden başlatıp Kurulum.bat'ı tekrar çalıştır.
    pause
    exit /b 1
)

rem --- Sanal ortam ve paketler ---
if not exist venv (
    py -3.12 -m venv venv
)
venv\Scripts\python.exe -m pip install --upgrade pip >nul
venv\Scripts\python.exe -m pip install SpeechRecognition PyAudio
if errorlevel 1 (
    echo  Paketler kurulamadı. İnternet bağlantını kontrol et.
    pause
    exit /b 1
)

rem --- Masaüstü kısayolu ---
powershell -NoProfile -Command "$s=(New-Object -ComObject WScript.Shell).CreateShortcut([Environment]::GetFolderPath('Desktop')+'\Kufur Alarmi.lnk');$s.TargetPath='%~dp0venv\Scripts\pythonw.exe';$s.Arguments='\"%~dp0kufur_alarm.py\"';$s.WorkingDirectory='%~dp0';$s.Save()"

echo.
set /p OTO=" Windows açılınca otomatik başlasın mı? (E/H): "
if /i "%OTO%"=="E" (
    powershell -NoProfile -Command "$s=(New-Object -ComObject WScript.Shell).CreateShortcut([Environment]::GetFolderPath('Startup')+'\Kufur Alarmi.lnk');$s.TargetPath='%~dp0venv\Scripts\pythonw.exe';$s.Arguments='\"%~dp0kufur_alarm.py\"';$s.WorkingDirectory='%~dp0';$s.Save()"
    echo  Otomatik başlatma açıldı.
)

echo.
echo  Kurulum bitti. Masaüstündeki "Küfür Alarmı" kısayoluyla açabilirsin.
echo  Şimdi başlatılıyor...
start "" "%~dp0venv\Scripts\pythonw.exe" "%~dp0kufur_alarm.py"
timeout /t 3 >nul
