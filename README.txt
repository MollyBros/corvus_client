Corvus Client (Fabric 1.21.11)

Сборка:
1. Установите JDK 21 и Gradle (или выполните: gradle wrapper).
2. Сверьте версии в gradle.properties на https://fabricmc.net/develop
3. gradle build
4. build/libs/corvus-client-1.0.0.jar -> в папку mods (нужен Fabric API).

Клавиши (меняются в настройках управления, категория Corvus Client):
  Right Shift - меню, R - KillAura, G - ESP

Меню (Right Shift): вкладки Combat / Visuals / Server / Theme, свои переключатели, слайдеры, список биндов (удаление кнопкой x), выбор темы (Blue по умолчанию).
Настройки сохраняются в config/corvus.json.

ServerHelper (бинды на предметы):
  /bind add <клавиша> <предмет>   пример: /bind add x ender_pearl
  /bind remove <клавиша> | /bind list | /bind clear
Бинды сохраняются в config/corvus-binds.json.

Target HUD: два стиля (меню -> Visuals -> Target HUD style): Classic и Compact (с экипировкой).

HUD: вкладка HUD в меню - Target HUD (2 стиля), список модулей, инфо-панель (ник, FPS, пинг, координаты).
