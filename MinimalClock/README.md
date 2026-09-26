# «Часы» — минималистичное офлайн-приложение для Android

Нативное приложение на Kotlin + Jetpack Compose (Material 3): мировое время, будильники,
секундомер, таймеры, два виджета, адаптивная иконка и Splash Screen. Без сети, рекламы,
аналитики и аккаунтов; все данные хранятся только на устройстве.

| Параметр | Значение |
|---|---|
| Язык / UI | Kotlin 2.4, Jetpack Compose (BOM 2026.09.00), Material 3 1.4 |
| Архитектура | MVVM + однонаправленный поток данных, ручной DI (`AppContainer`) |
| minSdk / targetSdk | 26 (Android 8.0) / 37 |
| Сборка | Android Gradle Plugin 9.4.0 (встроенный Kotlin), Gradle 9.6.0, JDK 17+ |
| Хранение | DataStore Preferences (Room не нужен — данных мало) |
| Языки | русский (основной), казахский, английский + ручной выбор в настройках |
| CI | GitHub Actions: unit-тесты, debug/release APK, Android Lint (`.github/workflows/minimalclock-android.yml`) |

---

## 1. Структура проекта

```
MinimalClock/
├── build.gradle.kts, settings.gradle.kts, gradle.properties
├── gradle/libs.versions.toml            ← все версии зависимостей в одном месте
├── gradle/wrapper/                      ← Gradle Wrapper 9.6.0
├── tools/strings_source.py              ← единый источник строк RU/KK/EN → strings.xml
└── app/
    ├── build.gradle.kts, proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/personal/clock/
        │   │   ├── ClockApplication.kt, AppContainer.kt, MainActivity.kt
        │   │   ├── domain/        ← чистый Kotlin, без Android: модели и расчёты
        │   │   │   Alarm.kt, AlarmScheduleCalculator.kt, TimerItem.kt, Stopwatch.kt,
        │   │   │   WorldClock.kt, DurationFormat.kt, Codecs.kt
        │   │   ├── data/          ← DataStore-репозитории
        │   │   ├── alarm/         ← AlarmManager, ресиверы, сервис сигнала, контроллеры
        │   │   ├── notification/  ← каналы и уведомления
        │   │   ├── widget/        ← виджеты 2×2 и 4×2
        │   │   ├── util/          ← язык, форматирование времени, источник времени
        │   │   └── ui/            ← Compose: theme, components, clock, alarms,
        │   │                        stopwatch, timers, settings, ringing
        │   └── res/
        │       ├── values/ values-ru/ values-kk/   strings.xml
        │       ├── values-night/ values-v31/ values-night-v31/  (тёмная тема, Material You)
        │       ├── drawable/      ← только векторные XML (иконки, splash, фон виджетов)
        │       ├── mipmap-anydpi-v26/  ic_launcher(.xml), ic_launcher_round(.xml)
        │       ├── layout/        ← разметка виджетов (RemoteViews)
        │       └── xml/           ← widget provider info, locales_config
        └── test/java/com/personal/clock/domain/   ← unit-тесты (JUnit 4)
```

В APK нет ни одного PNG, аудиофайла или шрифта.

## 2. Архитектура и выбор технологий

```
 UI (Compose screens) ──events──▶ ViewModel ──▶ Controller ──▶ Repository (DataStore)
        ▲                            │               │
        └────── StateFlow ◀──────────┘               ├──▶ AlarmScheduler (AlarmManager)
                                                     ├──▶ Notifications
                                                     └──▶ WidgetUpdater
 AlarmManager ──▶ AlarmReceiver ──▶ RingingService (живёт только пока звенит) ──▶ Ringer
 BootReceiver (перезагрузка, смена времени/пояса/языка) ──▶ Controllers.rescheduleAll()
```

* **domain** — чистые функции и неизменяемые модели. Вся «опасная» логика (расчёт следующего
  срабатывания с учётом дней недели и перехода на летнее/зимнее время, отложенный сигнал,
  состояние таймера и секундомера, перезагрузка) покрыта тестами и не зависит от Android.
* **Контроллеры** (`AlarmController`, `TimerController`) — единственная точка изменения
  состояния: сохранить → перерегистрировать в AlarmManager → обновить виджеты/уведомления.
  Поэтому данные, будильники в системе и виджеты не расходятся.
* **Ручной DI** вместо Hilt/Koin: меньше APK и время сборки, при таком размере проекта
  фреймворк не даёт выигрыша.
* **DataStore Preferences** вместо Room: будильников, таймеров и результатов секундомера —
  единицы; JSON через встроенный в Android `org.json` не требует библиотек сериализации.
  Файл лежит в *device-protected storage*, поэтому будильники восстанавливаются и звонят
  после перезагрузки **ещё до первой разблокировки** (Direct Boot).
* **Без навигационной библиотеки**: 4 вкладки + экран настроек — это одно состояние.
* **Material Icons** подключены как векторные XML, а не библиотекой `material-icons-extended`.

### Энергопотребление
* Между сигналами приложение ничего не делает в фоне: всё планирует `AlarmManager`.
* Будильники — `setAlarmClock()` (точно, в Doze, иконка в статус-баре), таймеры —
  `setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)` (не зависят от смены часов/пояса).
* Foreground-сервис запускается **только на время звонка** (макс. 10 мин, затем автоотключение).
* Идущий таймер показывается уведомлением с системным хронометром — отсчёт рисует система.
* Экранные «тикеры» работают только пока экран виден (`repeatOnLifecycle(STARTED)`).
* Виджеты: `updatePeriodMillis="0"`, время и дата — `TextClock`, которые обновляет сам
  лаунчер. Приложение обновляет виджет только при изменении будильников/настроек,
  времени, пояса, формата 12/24 и языка.

### Время и часовые пояса
* Расчёты — `java.time` поверх системной базы tzdata Android (обновляется Google Play
  System Updates на Android 10+).
* Смена времени/пояса (`TIME_SET`, `TIMEZONE_CHANGED`) → все будильники пересчитываются,
  «07:00» остаётся «07:00» по местному времени.
* Переход на летнее время: время внутри «провала» (02:30 при переводе 02:00→03:00)
  сдвигается на 03:30; в «перекрытии» будильник звонит один раз.
* Казахстан с 01.03.2024 живёт в UTC+5 — на устройствах со старой tzdata (без системных
  обновлений) смещение для Астаны/Алматы может отображаться по-старому.

## 3. Разрешения

| Разрешение | Зачем | Когда запрашивается |
|---|---|---|
| `POST_NOTIFICATIONS` | Звонящий будильник/таймер, отсчёт таймера, отложенный сигнал | Android 13+: при сохранении первого будильника или запуске таймера; повторно — кнопкой в баннере/настройках |
| `USE_EXACT_ALARM` | Точное срабатывание будильников и таймеров (Android 13+) | Не запрашивается — выдаётся автоматически приложениям-будильникам |
| `SCHEDULE_EXACT_ALARM` (maxSdk 32) | То же для Android 12/12L | Включено по умолчанию; если пользователь отключил — баннер с объяснением и кнопкой в системные настройки; до этого используется неточный запасной режим |
| `USE_FULL_SCREEN_INTENT` | Экран будильника поверх экрана блокировки | Android 14+: если отключено — баннер и кнопка в настройки |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Проигрывание сигнала при выключенном экране — только во время звонка | Не требует диалога |
| `RECEIVE_BOOT_COMPLETED` | Восстановить будильники после перезагрузки | Не требует диалога |
| `WAKE_LOCK` | Не дать процессору уснуть, пока играет мелодия | Не требует диалога |
| `VIBRATE` | Вибрация будильника/таймера | Не требует диалога |

**Не запрашиваются:** интернет (разрешения `INTERNET` нет вообще), геолокация, камера,
микрофон, контакты, хранилище, телефон, оптимизация батареи.

**Отказ от разрешений:** приложение не падает и не блокирует работу. Без уведомлений
сигнал всё равно звучит, а выключить его можно из баннера внутри приложения; без точных
будильников используется неточный режим и показывается предупреждение. Если система
не разрешит запустить сервис сигнала (Android 12/12L с отозванными точными будильниками),
будильник и таймер звонят через резервный канал уведомлений со звуком будильника —
сигнал не теряется.

**Язык по умолчанию:** при выборе «Как в системе» используется язык устройства, если это
русский, казахский или английский; для любого другого языка устройства приложение
показывается на русском.

**Резервные копии:** в облако ничего не выгружается; при переносе на новый телефон
(кабель / локальная передача Android 12+) переносятся только будильники, таймеры и настройки.

**Защита компонентов:** экспортированы только `MainActivity` (лаунчер) и `BootReceiver`
(получает лишь защищённые системные broadcast-ы и проверяет action). `AlarmReceiver`,
`RingingService`, `AlarmRingingActivity` и виджет-провайдеры — `exported="false"`; все
`PendingIntent` явные и `FLAG_IMMUTABLE`. `allowBackup="false"`.

## 4. Открытие в Android Studio

1. Установите Android Studio последней стабильной версии (с поддержкой AGP 9.4).
2. **File → Open** → выберите папку `MinimalClock` (где лежит `settings.gradle.kts`).
3. Дождитесь Gradle Sync. Studio сама скачает Gradle 9.6.0, AGP и Android SDK Platform 37
   (если SDK 37 не установлен — примите предложение установить через SDK Manager).
4. JDK: *Settings → Build Tools → Gradle → Gradle JDK* = встроенный JetBrains Runtime (17+).
5. Выберите конфигурацию `app` и устройство/эмулятор → **Run**.

## 5. Сборка APK

```bash
# Debug APK (отдельный applicationId *.debug — можно ставить рядом с release)
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Unit-тесты
./gradlew testDebugUnitTest

# Release APK (R8 + shrinkResources)
./gradlew assembleRelease
# без ключа → app/build/outputs/apk/release/app-release-unsigned.apk
```

**Подписанный release.** Создайте ключ один раз:
```bash
keytool -genkeypair -v -keystore clock-release.jks -alias clock -keyalg RSA -keysize 4096 -validity 10000
```
и файл `keystore.properties` в корне проекта (он в `.gitignore`):
```properties
storeFile=clock-release.jks
storePassword=********
keyAlias=clock
keyPassword=********
```
После этого `./gradlew assembleRelease` выдаст подписанный `app-release.apk`.
Альтернатива: **Build → Generate Signed App Bundle / APK** в Android Studio.

Установка: `adb install -r app/build/outputs/apk/release/app-release.apk`.

**Готовый APK без Android Studio.** Каждый push в PR запускает GitHub Actions
(вкладка *Actions* → *MinimalClock Android*): тесты, сборка и Lint. Артефакт
`minimalclock-apks` содержит `app-debug.apk`, который можно сразу установить на телефон.

## 6. Тесты

`app/src/test/java/com/personal/clock/domain/` — 55 тестов JUnit 4:

| Файл | Что проверяет |
|---|---|
| `AlarmScheduleCalculatorTest` | однократные/повторяющиеся будильники, пропуск выходных, DST-«провал» и «перекрытие» (Берлин, Нью-Йорк), смена часового пояса, отложенный сигнал, выбор ближайшего |
| `AlarmTest` | отключение однократного будильника после срабатывания, лимит повторов, сброс отложенного сигнала, маски дней недели |
| `TimerItemTest` | старт/пауза/продолжение/сброс/повтор, +1 мин, прогресс, восстановление после перезагрузки |
| `StopwatchTest` | накопление времени, круги, лучший/худший круг, перезагрузка, история |
| `WorldClockTest` | каталог городов ↔ tzdata, разница с учётом летнего времени, пояса с 30 мин, «вчера/завтра», сортировка (Collator), поиск |
| `DurationFormatTest`, `CodecsTest` | форматирование, JSON-сериализация и устойчивость к повреждённым данным |

## 7. Настройка и расширение

* Цвета приложения — `ui/theme/Theme.kt`; фон splash — `@color/splash_background`
  (`values/` — тёмно-синий для светлой темы, `values-night/` — графитовый для тёмной).
* Строки — правьте `tools/strings_source.py` и выполните `python3 tools/strings_source.py`.
* Список городов — `domain/WorldClock.kt` (`CityCatalog`) + названия в `strings_source.py`
  + строка в `ui/clock/CityNames.kt`.

## 8. Известные ограничения

* Сборку и тесты проверяет CI; поведение на устройствах — нет. Перед ежедневным
  использованием проверьте на реальном телефоне полноэкранный будильник на
  заблокированном экране и виджеты у вашего лаунчера.
* Казахские переводы стоит показать носителю языка для стилистической вычитки.
* Динамические цвета виджетов на Android 12+ берутся из системной палитры всегда
  (независимо от переключателя Material You внутри приложения).
