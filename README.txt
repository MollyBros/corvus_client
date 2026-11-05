Corvus Launcher (Minecraft 1.21.11 + Fabric + Corvus Client)

ЗАПУСК
1. Установите Python 3.10+ (с галочкой Add to PATH).
2. pip install -r requirements.txt
3. Положите corvus-client-1.0.0.jar в папку mods/
4. python corvus_launcher.py

СБОРКА В EXE БЕЗ PYTHON НА ПК ИГРОКОВ:
 Вариант А (на Windows): запустите «Собрать_лаунчер.bat». Если Python нет, он поставится сам (winget),
   затем получится папка dist\ с CorvusLauncher.exe и mods\. Положите corvus-client-1.0.0.jar в dist\mods\.
 Вариант Б (без установки чего-либо): загрузите проект на GitHub, вкладка Actions ->
   Build Corvus Launcher -> Run workflow. Готовый exe скачайте из Artifacts.
Готовую папку dist можно передавать другим: Python им не нужен (он внутри exe).
Windows SmartScreen может предупредить о неподписанном exe - «Подробнее» -> «Выполнить в любом случае».

ВХОД (Microsoft)
Нужен свой Client ID приложения Azure (бесплатно):
 1. portal.azure.com -> Microsoft Entra ID -> App registrations -> New registration
 2. Supported account types: "Personal Microsoft accounts only" (или любые личные)
 3. Redirect URI: платформа "Public client/native (mobile & desktop)",
    значение: https://login.live.com/oauth20_desktop.srf
 4. Скопируйте Application (client) ID, вставьте в лаунчере: «Настройки входа».
 5. Нажмите «Войти», войдите в браузере и вставьте адрес пустой страницы обратно.
Дальше вход запоминается (refresh-токен хранится в %APPDATA%\.corvus\launcher.json).
Для новых приложений Azure Microsoft может требовать одобрение доступа к API Minecraft
(форма Minecraft Java API permission request) - без него вход вернёт ошибку.

ЧТО ДЕЛАЕТ «ИГРАТЬ»
Ставит Minecraft + Fabric Loader + подходящую Java, скачивает Fabric API с Modrinth,
копирует Corvus Client из mods/ и запускает игру. Данные игры: %APPDATA%\.corvus\game

ИКОНКА: assets\corvus.ico / corvus_icon.png (буква C) - иконка окна, панели задач и exe. Аватар также показан в панели аккаунта.
