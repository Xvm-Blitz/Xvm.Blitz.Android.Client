# XVM Blitz Android Client

Android-порт [Xvm.Blitz.Windows.Client](../Xvm.Blitz.Windows.Client): companion для Tanks Blitz.

## Стек

- Kotlin + Jetpack Compose
- Retrofit + kotlinx.serialization
- EncryptedSharedPreferences (API key)
- DataStore (настройки overlay)
- MediaProjection (ручной захват экрана)
- `TYPE_APPLICATION_OVERLAY` (статистика поверх игры)

## MVP

1. Авторизация по API-ключу (`X-Xvm-Api-Key`)
2. Квота `GET v1/api_keys/usage`
3. Кнопка «Считать статистику» → MediaProjection → grayscale PNG → `POST v1/battles/statistics`
4. Overlay союзников/противников (7 слотов, цвета win-rate)
5. Настройки: drag в режиме конфигурации, размер шрифта, show/hide

Не перенесено: replay-watcher, патч `.dvpl`, hotkeys, tray.

## Сборка

Требования: JDK 17+, Android SDK (platform 35 подтянется Gradle).

```powershell
cd "C:\Disk D\Rider projects\Xvm.Blitz.Android.Client"
.\gradlew.bat :app:assembleDebug
```

APK: `app\build\outputs\apk\debug\app-debug.apk`

Или откройте папку в Android Studio / Rider и запустите `app`.

## Ручной checklist

1. Установить debug APK, выдать разрешение «Поверх других окон» и уведомления.
2. Ввести валидный API-ключ → квота отображается.
3. Открыть экран загрузки боя в Tanks Blitz (или любой тестовый скрин таблицы).
4. В XVM нажать «Считать статистику» → разрешить захват экрана.
5. Убедиться, что появились overlay-панели союзников/противников.
6. Включить «Режим настройки» и перетащить панели; позиции сохраняются после перезапуска.
7. Скрыть overlay из notification action / переключателя в приложении.
8. «Выйти» очищает ключ и останавливает overlay.

API base URL: `https://xvmblitz.ru/api/`
