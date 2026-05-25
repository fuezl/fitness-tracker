# Дневник тренировок

Нативное offline-first Android-приложение для ведения тренировок в зале: упражнения, подходы, вес, повторения, RPE, история, прогресс, личные рекорды и настройки.

## Стек

- Kotlin, Single Activity, Jetpack Compose, Material 3
- Navigation Compose
- MVVM с `StateFlow`
- Coroutines, Flow
- Room + KSP
- Hilt
- DataStore Preferences
- Kotlinx Serialization JSON
- Gradle Kotlin DSL, Version Catalog, Compose BOM
- Unit tests, Room DAO instrumentation tests, Compose UI tests

## Структура

- `ru.fuezl.gymdiary` — `Application`, `MainActivity`, навигация
- `core.common` — форматирование дат и значений
- `core.database` — Room entities, DAO, database, seed-данные
- `core.datastore` — настройки пользователя
- `core.di` — Hilt modules
- `core.model` — domain/UI модели и enum
- `core.ui` — тема и переиспользуемые Compose-компоненты
- `data.repository` — репозитории для упражнений, тренировок, прогресса и настроек
- `domain.usecase` — расчёты и валидация
- `feature.dashboard` — главный экран
- `feature.exercises` — список и редактирование упражнений
- `feature.workout` — старт, активная тренировка, добавление упражнений
- `feature.history` — история и детали тренировки
- `feature.progress` — аналитика и личные рекорды
- `feature.settings` — тема, таймер, экспорт/импорт, очистка данных

## Запуск в Android Studio

1. Откройте корневую папку проекта `fitness-tracker`.
2. Дождитесь Gradle sync.
3. Выберите конфигурацию `app`.
4. Запустите на эмуляторе или устройстве Android 13+.

Локально проект использует Android SDK из `local.properties`.

## Сборка и тесты

Windows:

```powershell
gradlew.bat clean build
gradlew.bat test
gradlew.bat assembleDebug
gradlew.bat compileDebugAndroidTestKotlin
```

Unix shell:

```bash
./gradlew clean build
./gradlew test
./gradlew assembleDebug
./gradlew compileDebugAndroidTestKotlin
```

### Что делает каждая команда

- `gradlew.bat clean build` — полная локальная проверка проекта: clean, компиляция debug/release, unit tests, lint, сборка APK.
- `gradlew.bat test` — JVM unit tests из `app/src/test`.
- `gradlew.bat assembleDebug` — сборка debug APK.
- `gradlew.bat assembleRelease` — сборка release APK без production signing config.
- `gradlew.bat compileDebugAndroidTestKotlin` — компиляция instrumentation и Compose UI tests без запуска устройства.
- `gradlew.bat connectedDebugAndroidTest` — запуск instrumentation/Compose/DAO tests на подключённом устройстве или эмуляторе.

### Как собрать debug APK

```powershell
gradlew.bat assembleDebug
```

Готовый файл:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Как собрать release APK

```powershell
gradlew.bat assembleRelease
```

Готовый файл:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

Для публикации нужен отдельный release signing config и keystore. В репозитории они намеренно не добавлены.

### Как запустить тесты

Unit tests:

```powershell
gradlew.bat test
```

Instrumentation/DAO/Compose tests на устройстве:

```powershell
gradlew.bat connectedDebugAndroidTest
```

Для `connectedDebugAndroidTest` нужен запущенный Android Emulator или подключённое устройство с включённой USB-отладкой. Проверить список устройств можно так:

```powershell
adb devices
```

Если устройства нет, можно хотя бы проверить компиляцию androidTest:

```powershell
gradlew.bat compileDebugAndroidTestKotlin
```

### Текущий тестовый набор

- JVM unit tests:
  - расчёт тренировочного объёма;
  - расчёт estimated 1RM по Epley;
  - подсчёт повторений с собственным весом;
  - валидация упражнения;
  - валидация подхода;
  - сортировка истории тренировок.
- Room DAO tests:
  - добавление и получение упражнения;
  - создание тренировки с упражнениями и подходами;
  - получение деталей тренировки;
  - cascade delete тренировки.
- Repository integration tests:
  - старт/завершение/повтор/удаление тренировки;
  - создание, старт и удаление шаблона;
  - расчёт прогресса и работа с массой тела;
  - экспорт/импорт JSON со связанными данными;
  - защита текущих данных при некорректном JSON;
  - seed/search/update/delete для упражнений.
- Compose UI tests:
  - главный экран показывает “Начать тренировку”;
  - список упражнений отображает упражнение;
  - экран редактирования показывает ошибку пустого названия;
  - активная тренировка показывает “Добавить подход”.

## Реализовано

- Предзаполнение 18 базовых упражнений при первом запуске.
- Главный экран со стартом тренировки, последней тренировкой, недельной/месячной статистикой и последними весами.
- Список упражнений с поиском, фильтрами и редактированием.
- Создание пользовательских упражнений с русской валидацией.
- Старт пустой тренировки.
- Создание шаблона из активной тренировки.
- Старт тренировки из шаблона.
- Удаление шаблона.
- Активная тренировка: добавление упражнений, подходов, веса, повторений, RPE, комментариев, выполнение/удаление подходов.
- Таймер отдыха с паузой, пропуском и добавлением 30 секунд.
- Завершение тренировки с предупреждениями для пустых/незавершённых данных.
- История тренировок с группировкой по месяцу.
- Детали тренировки, удаление и повтор тренировки.
- Экран прогресса: недельная статистика, график, личные рекорды, estimated 1RM по Epley.
- Выбор упражнения на экране прогресса, отдельные графики максимального веса и объёма.
- Учёт массы тела: добавление, просмотр и удаление записей.
- Раздел “Ещё” для прогресса и настроек при пяти пунктах нижней навигации.
- Настройки темы, таймера отдыха, экспорт/импорт JSON через Storage Access Framework, очистка данных.
- Unit tests для расчётов, валидации и сортировки.
- DAO tests для упражнений, деталей тренировки и cascade delete.
- Compose UI tests для основных экранов.

## Ограничения первой версии

- Уведомления таймера отдыха не реализованы.
- Графики сделаны на Compose Canvas без внешней chart-библиотеки.
- Синхронизация, backend, авторизация и Firebase отсутствуют намеренно.
