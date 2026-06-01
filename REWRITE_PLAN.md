# Zona — План полного переписывания

Кроссплатформенная обучающая платформа (маркетплейс): **web + Android + Desktop**.
Цель: логически целостный продукт, чистая архитектура (Clean + MVI), красивый единый дизайн,
без «дыр». Разработка идёт **вертикальными срезами**: каждая фича доводится до конца через все
слои (UI → MVI → domain → data → Ktor → Postgres) и проверяется до начала следующей.

---

## 1. Технологический стек

| Слой | Технология |
|------|-----------|
| UI | Compose Multiplatform 1.7.x (Android / Desktop JVM / wasmJs Web) |
| Архитектура клиента | Clean Architecture + MVI (unidirectional) |
| DI | Ручной composition root (`AppGraph`) — без Hilt (Hilt не работает на web/desktop) |
| Сеть | Ktor Client (OkHttp/CIO/JS-движки per platform) |
| Хранение токена | `multiplatform-settings` (общий код, работает и на web) |
| Картинки | Coil 3 (поддерживает wasm) |
| Сервер | Ktor Server (Netty) |
| БД | PostgreSQL + Exposed |
| Доступ к БД | DAO с **узкими** транзакциями (`transaction { }` только вокруг запросов) |
| Auth | JWT (access-токен), пароли — BCrypt |
| Оплата | Имитация: кошелёк (баланс) + транзакции, без реальных денег |

**Ключевые отказы от текущего проекта:** Hilt (→ ручной DI), смешение DTO и домена (→ раздельные
модели), строковые роли (→ enum), матчинг ошибок по тексту (→ коды → `ErrorType`),
монолитный `Routing.kt` (→ routing/service/dao по фичам).

---

## 2. Структура проекта

```
:composeApp                       (Compose Multiplatform: android, desktop, wasmJs)
  commonMain/
    core/
      design/        — тема, цвета, типографика, компоненты (кнопки, карточки, поля, состояния)
      network/       — HttpClient factory, базовый URL, интерсептор токена
      error/         — AppError, ErrorType, маппинг кодов
      result/        — Outcome<T> (sealed: Success/Failure)
      di/            — AppGraph (composition root)
    feature/
      auth/          — domain | data | presentation | ui
      profile/
      teacher/       — заявка в преподы + документы + профиль препода
      catalog/       — каталог курсов (красивые карточки), поиск, фильтры
      course/        — детали курса, уроки, запись, оплата
      learning/      — упражнения (тест/ввод), проверка, XP, прогресс
      flashcards/    — колоды + режим изучения (простой spaced repetition)
      sessions/      — групповые и индивидуальные занятия + бронирование
      chat/          — диалоги преподаватель ↔ ученик
      wallet/        — баланс, пополнение (mock), история
      admin/         — модерация заявок в преподы
    app/             — навигация (sealed Screen), корневой стор, App()
  androidMain/       — actual: движок Ktor, выбор файлов, точка входа
  desktopMain/       — actual: движок Ktor, выбор файлов, main()
  wasmJsMain/        — actual: движок Ktor (js), localStorage, file input, main()

:server
  src/main/kotlin/ru/zona/server/
    plugins/         — Serialization, Auth(JWT), CORS, StatusPages
    security/        — JwtService, PasswordHasher
    db/              — Database, Tables, Migrations, Seed
    feature/<name>/  — Routes.kt (тонкие) | Service.kt (логика) | Dao.kt (Exposed) | Dto.kt
    Application.kt    — сборка модулей
```

**Правила зависимостей (строго):**
- `domain` не знает про Ktor, Compose, сериализацию, Android.
- `data` реализует интерфейсы `domain`, мапит `*Dto` ↔ доменные модели.
- `presentation` (MVI Store) зависит только от `domain` (use-cases), не от `data`/`api`.
- `ui` зависит от `presentation` + `core/design`.

---

## 3. Доменная модель и схема БД

> Принцип ролей: **TEACHER — это STUDENT, прошедший модерацию.** Преподаватель сохраняет все
> возможности ученика (записываться на чужие курсы, нанимать преподавателя). ADMIN — отдельная роль.

### Идентичность
- **User**(id, email, passwordHash, displayName, avatarUrl?, bio, role: `STUDENT|TEACHER|ADMIN`, createdAt)
- **TeacherApplication**(id, userId, motivation, status: `PENDING|NEED_INFO|APPROVED|REJECTED`, adminMessage?, createdAt, decidedAt?)
- **TeacherApplicationDocument**(id, applicationId, fileName, mimeType, contentBase64/url) — дипломы/документы
- **TeacherProfile**(userId PK, headline, languages, pricePerHourCents?, ratingAvg?, ratingCount)

### Обучение
- **Course**(id, teacherId, title, description, languageFrom, languageTo, priceCents? (null = бесплатно), coverUrl?, published, createdAt)
- **Lesson**(id, courseId, title, sortOrder)
- **Exercise**(id, lessonId, type: `SINGLE_CHOICE|MULTIPLE_CHOICE|TEXT_INPUT`, prompt, choicesJson?, correctAnswer, xp, sortOrder)
- **Enrollment**(id, userId, courseId, paid, createdAt) — UNIQUE(userId, courseId)
- **ExerciseAttempt**(id, userId, exerciseId, answer, correct, createdAt)
- **LessonProgress**(userId, lessonId, completed, completedAt?) — PK(userId, lessonId)

### Карточки (flashcards)
- **Deck**(id, courseId?, lessonId?, title)
- **Flashcard**(id, deckId, front, back, sortOrder)
- **FlashcardReview**(userId, flashcardId, box: Int, dueAtEpochMs) — простой Leitner SR. PK(userId, flashcardId)

### Занятия (live)
- **Session**(id, teacherId, courseId?, type: `GROUP|INDIVIDUAL`, title, description, startsAtEpochMs, durationMin, capacity, priceCents?, createdAt)
- **SessionBooking**(id, sessionId, studentId, status: `BOOKED|CANCELLED`, paid, createdAt) — UNIQUE(sessionId, studentId)
- Индивидуальные слоты: **TeacherAvailability**(id, teacherId, dayOfWeek, startMinute, endMinute) + booking-запрос с подтверждением.

### Оплата (mock)
- **Wallet**(userId PK, balanceCents)
- **WalletTransaction**(id, userId, amountCents, kind: `TOPUP|PURCHASE`, refType?, refId?, createdAt)

### Чат
- **Conversation**(id, userAId, userBId, createdAt) — UNIQUE упорядоченная пара
- **Message**(id, conversationId, senderId, text, sentAtEpochMs)

---

## 4. Обработка ошибок (сквозная)

```kotlin
enum class ErrorType { NO_CONNECTION, BAD_REQUEST, UNAUTHORIZED, FORBIDDEN,
                       NOT_FOUND, CONFLICT, SERVER_ERROR, UNKNOWN }

sealed interface Outcome<out T> {
    data class Success<T>(val data: T) : Outcome<T>
    data class Failure(val type: ErrorType, val message: String) : Outcome<Nothing>
}
```

- Клиент: HTTP-код → `ErrorType` (в data-слое), domain/UI работают с `ErrorType`, а не с текстом.
- Сервер: единый `StatusPages` → `{ "error": "...", "code": ... }`, осмысленные статусы (401/403/404/409/422).

---

## 5. Дизайн-система (единый красивый стиль)

- Material 3 + кастомная палитра (бренд-цвет, мягкие поверхности, тёмная/светлая темы).
- Базовые компоненты в `core/design`: `ZonaScaffold`, `CourseCard`, `TeacherCard`, `PriceTag`,
  `PrimaryButton`, `TextField`, `LoadingState`, `EmptyState`, `ErrorState`, `Avatar`, `Badge`.
- Все экраны обязаны иметь состояния Loading / Empty / Error (это и убирает «дыры» в UX).
- Анимации переходов, скелетоны при загрузке списков.

---

## 6. Дорожная карта (вертикальные срезы)

| Фаза | Содержание | Критерий готовности |
|-----|-----------|---------------------|
| **0. Foundation** | Gradle на 3 таргета, ручной DI, core (network/error/result/design), сервер-скелет (плагины, БД, миграции, seed), health-check | Все 3 клиента запускаются, сервер отвечает |
| **1. Auth + Профиль** | Регистрация/вход (JWT), восстановление сессии, просмотр/редактирование профиля | Полный вход на android/desktop/web |
| **2. Заявка в преподы** | Кнопка в профиле → форма + загрузка документов → модерация админом | Студент становится преподом |
| **3. Каталог + запись + кошелёк** | Красивые карточки курсов, поиск/фильтр, детали, запись, mock-оплата (баланс) | Запись на платный/бесплатный курс |
| **4. Обучение** | Уроки → упражнения (тест/множественный/ввод), проверка, XP, прогресс | Прохождение урока с прогрессом |
| **5. Flashcards** | Колоды + режим изучения (Leitner), привязка к курсу/уроку | Изучение колоды |
| **6. Занятия** | Создание групповых/индивидуальных, бронирование, mock-оплата | Бронь занятия |
| **7. Чат** | Диалоги препод ↔ ученик, список + переписка | Обмен сообщениями |
| **8. Полировка** | Состояния загрузки/пустоты/ошибок, темы, анимации, юнит-тесты (use-cases, error-mapper) | Чисто и стабильно |

Каждая фаза мёржится только когда работает на **всех трёх** платформах.

---

## 7. Первый шаг

Фаза 0: переписать каталог зависимостей и Gradle (добавить wasmJs, убрать Hilt, добавить
multiplatform-settings + Coil3), затем core-слой и серверный скелет с миграциями.
Старый код в пакете `ru.diploma.crossplatform.zona` удаляется по мере замены.
