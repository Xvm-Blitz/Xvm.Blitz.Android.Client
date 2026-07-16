# XVM Blitz Android Client

Android-порт [Xvm.Blitz.Windows.Client](https://github.com/Xvm-Blitz/Xvm.Blitz.Windows.Client): companion для Tanks Blitz.

## Стек

- Kotlin + Jetpack Compose
- Retrofit + kotlinx.serialization
- EncryptedSharedPreferences (API key)
- DataStore (настройки overlay)
- MediaProjection (ручной захват экрана)
- `TYPE_APPLICATION_OVERLAY` (статистика поверх игры)

## Сборка

Требования: JDK 17+, Android SDK (platform 35 подтянется Gradle).

```powershell
.\gradlew.bat :app:assembleDebug
```

```bash
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Или откройте папку в Android Studio / Rider и запустите `app`.

### Подпись (debug)

В репозитории лежит **debug keystore** `app/keystore/xvm-debug.jks` (alias `androiddebugkey`, пароль `android`). Он нужен только для локальной разработки и CI без production-секретов.

Настройки подписи: `app/keystore.properties` (шаблон — `app/keystore.properties.example`).

```powershell
.\gradlew.bat :app:assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk` (при локальной сборке тоже подписывается debug-ключом, пока не подставите свой production keystore в `keystore.properties`).

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
