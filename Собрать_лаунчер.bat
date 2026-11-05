@echo off
chcp 65001 >nul
title Сборка Corvus Launcher
cd /d "%~dp0"

where python >nul 2>nul
if errorlevel 1 (
  echo Python не найден. Устанавливаю через winget...
  winget install -e --id Python.Python.3.12 --accept-package-agreements --accept-source-agreements
  echo.
  echo Python установлен. ЗАКРОЙТЕ это окно и запустите файл ещё раз.
  pause
  exit /b
)

echo [1/3] Установка зависимостей...
python -m pip install --upgrade pip
python -m pip install -r requirements.txt pyinstaller || goto :err

echo [2/3] Сборка CorvusLauncher.exe...
python -m PyInstaller --noconfirm --noconsole --onefile --name CorvusLauncher --icon assets\corvus.ico --add-data "assets;assets" corvus_launcher.py || goto :err

echo [3/3] Подготовка папки...
if not exist dist\mods mkdir dist\mods
xcopy mods dist\mods /E /I /Y >nul

echo.
echo ГОТОВО. Папка dist\ содержит CorvusLauncher.exe и mods\
echo Положите corvus-client-1.0.0.jar в dist\mods\ (если его там нет).
echo Эту папку можно отправлять кому угодно: Python на их ПК НЕ нужен.
explorer dist
pause
exit /b

:err
echo.
echo ОШИБКА сборки. Пришлите текст ошибки выше.
pause
