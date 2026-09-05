# Сборка TDLib для Android

TDLib не публикуется в Maven Central для Android — официальный способ получить
`libtdjni.so` и Java-биндинги `org.drinkless.tdlib.*` — собрать библиотеку самостоятельно.
Сборка занимает 30–90 минут на среднем ноутбуке и делается **один раз**: результат
кладётся в `app/libs/` и дальше не пересобирается.

> Проверенная версия: **TDLib 1.8.67**. Клиент использует пакет `org.drinkless.tdlib`
> (не устаревший `org.drinkless.td.libcore.telegram`), обобщённую сигнатуру
> `TdApi.Function<R>`, «плоский» запрос `SetTdlibParameters`, `TdApi.User.usernames` и
> `DraftMessage.content`. На более старой 1.8.x последнее поле называется иначе, и код не
> соберётся — какой именно версии соответствует ваш чекаут, покажет
> `tools/verify-tdlib-api.sh`.

---

## 1. Предварительные требования

| Компонент | Версия | Примечание |
| --- | --- | --- |
| JDK | 17 | тот же, что используется Android Gradle Plugin 8.5 |
| Android SDK | API 34 | `platforms;android-34`, `build-tools;34.0.0` |
| Android NDK | r25c или новее | `ndk;25.2.9519653` |
| CMake | 3.22+ | из SDK Manager или системный |
| gperf, git, make | любые | для сборки TDLib и OpenSSL |
| PHP CLI | 8.x | TDLib использует PHP-скрипты при генерации кода |

На Ubuntu:

```bash
sudo apt-get install -y git cmake gperf php-cli make g++ zlib1g-dev
```

Компоненты Android:

```bash
sdkmanager "platforms;android-34" "build-tools;34.0.0" \
           "ndk;25.2.9519653" "cmake;3.22.1"
```

---

## 2. Сборка

TDLib содержит готовый набор скриптов именно для Android:

```bash
git clone https://github.com/tdlib/td.git
cd td/example/android

export ANDROID_SDK_ROOT=$HOME/Android/Sdk
export ANDROID_NDK_ROOT=$ANDROID_SDK_ROOT/ndk/25.2.9519653

./check-environment.sh      # скажет, чего не хватает
./build-openssl.sh          # OpenSSL под все ABI, самый долгий шаг
./build-tdlib.sh            # сама TDLib + JNI-обвязка
```

Чтобы сократить время сборки примерно вдвое, ограничьте набор ABI: в
`build-openssl.sh` и `build-tdlib.sh` оставьте только `arm64-v8a` и `x86_64`
(первое покрывает все современные ТВ-приставки, второе — эмулятор).

По завершении в `td/example/android/tdlib/` появятся:

```
tdlib/libs/<abi>/libtdjni.so          # нативные библиотеки
tdlib/java/org/drinkless/tdlib/*.java # сгенерированные биндинги (TdApi.java ~ 5 МБ)
```

---

## 3. Установка в проект

Самый короткий путь — вариант **B** из `app/libs/README.md`:

```bash
TD=~/td/example/android/tdlib
APP=<этот репозиторий>/android-tv-telegram/app

mkdir -p "$APP/libs/java" "$APP/libs/jniLibs"
cp -r "$TD/java/org"   "$APP/libs/java/"
cp -r "$TD/libs/."     "$APP/libs/jniLibs/"
```

Должно получиться:

```
app/libs/java/org/drinkless/tdlib/Client.java
app/libs/java/org/drinkless/tdlib/TdApi.java
app/libs/jniLibs/arm64-v8a/libtdjni.so
app/libs/jniLibs/x86_64/libtdjni.so
```

`TdApi.java` — очень большой файл; если `javac` падает по памяти, добавьте в
`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
```

---

## 4. Альтернатива: собрать jar

Если предпочтительнее бинарный артефакт:

```bash
cd ~/td/example/android/tdlib/java
javac -encoding UTF-8 -d classes org/drinkless/tdlib/*.java
jar cf tdlib.jar -C classes .
cp tdlib.jar <репозиторий>/android-tv-telegram/app/libs/
```

Нативные `.so` всё равно нужно положить в `app/libs/jniLibs/`.

---

## 5. Проверка

Есть две независимые проверки.

**До сборки TDLib** — соответствие кода схеме API. Не требует ни Android SDK, ни NDK, ни
собранной TDLib, занимает пару минут:

```bash
tools/verify-tdlib-api.sh              # против master
tools/verify-tdlib-api.sh v1.8.50      # против конкретной ревизии
```

Скрипт клонирует схему, генерирует из неё `TdApi.java` и компилирует против него весь
слой `td/` и `data/`, затем прогоняет юнит-тесты. Любое расхождение по именам классов и
полей всплывает здесь, а не через час сборки под Android.

**После установки артефактов** — их наличие:

```bash
./gradlew :app:verifyTdlib
```

Задача завершится ошибкой с понятным текстом, если TDLib не найден, и предупреждением,
если отсутствуют `.so` для нужных ABI.

---

## 6. Частые проблемы

| Симптом | Причина | Решение |
| --- | --- | --- |
| `UnsatisfiedLinkError: dlopen failed: library "libtdjni.so" not found` | нет `.so` для ABI устройства | добавьте нужный ABI в сборку TDLib и в `abiFilters` |
| `NoClassDefFoundError: org/drinkless/tdlib/Client` | положены только `.so`, без Java-биндингов | скопируйте `java/org` или `tdlib.jar` |
| `Unresolved reference: usernames` | TDLib старее 1.8.6 | обновите TDLib |
| `Unresolved reference: content` в `DraftMessage` | TDLib старее той, где черновик стал `DraftMessageContent` | обновите TDLib |
| `Function<R>` — «type arguments not allowed» | TDLib старее 1.8.6 | обновите TDLib |
| OpenSSL собирается вечно | собираются все 4 ABI | оставьте `arm64-v8a` и `x86_64` |
