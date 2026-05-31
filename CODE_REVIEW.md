# Code Review — проект Zona (sprint-2)

Ревью объединяет замечания руководителя практики и моё собственное ревью кода
по фактическому состоянию ветки `sprint-2`.

Статус: ⬜ не сделано · 🟡 частично · ✅ готово

---

## Что уже сделано хорошо (важно зафиксировать)

Бо́льшая часть архитектурных замечаний руководителя **уже закрыта** в новом слое
(`zona/{domain,data,presentation}`), просто он пока не закоммичен (весь в untracked):

- ✅ **Clean Architecture подключена реально, а не на бумаге.**
  `App.kt` → `ZonaDependencies` (composition root) → `ZonaUseCases` → `ZonaApp(useCases)`.
  UI нигде не дёргает `ZonaApi` напрямую.
- ✅ **API вынесен в репозиторий** (замечание «в API не должен жить код работы с API»).
  `ZonaApi` — теперь тонкий транспорт; оркестрация в `data/repository/ZonaRepositoryImpl`,
  контракт — `domain/repository/ZonaRepository`.
- ✅ **MVI presentation**: `MviStore` + по-экранные/по-ролевые стора, `Effect` для snackbar/навигации.
- ✅ **Бизнес-правила вынесены в policy** (`CoursePolicy`, `BookingPolicy`, `AssignmentPolicy`) и
  валидация — в use-case'ах. Это даёт тестируемость.
- ✅ **Навигация развязана**: `ZonaScreen` — sealed interface, табы — enum, по-ролевые хосты
  `StudentApp/TeacherApp/AdminApp` в `ui/screens/*`.

**Первоочередное действие:** закоммитить этот слой. Сейчас вся проделанная работа — в untracked,
история проекта её не отражает, и для защиты это выглядит так, будто её нет.

---

## Что осталось доделать (реальные замечания)

### 1. 🟡 Ошибки: структура есть, но суть замечания не выполнена

Каркас создан (`domain/error/ErrorMapper`, `data/error/ApiErrorMapper`), **но внутри всё ещё
старый хрупкий подход** — `ApiErrorMapper` просто делегирует в `friendlyApiError`, который
матчит ошибки по тексту:

```kotlin
// ApiErrors.kt
val m = raw.lowercase()
when {
    "timeout" in m -> ...
    "connection refused" in m -> ...
}
```

Это ровно то, на что указывал руководитель: завязка на **описание**, а не на **код ответа**.
Текст зависит от платформы/локали/версии Ktor — обновили библиотеку, и `when` молча перестал ловить.

Корень — `ZonaApi.requireOk()` теряет HTTP-код:

```kotlin
class ZonaApiException(message: String) : Exception(message)  // ← кода нет
```

**Сделать (по предложению руководителя, поддерживаю):**

```kotlin
object NetworkParams {
    const val NO_CONNECTION_CODE = -1
    const val BAD_REQUEST_CODE = 400
    const val INCORRECT_CREDENTIALS = 401
    const val FORBIDDEN = 403
    const val NOT_FOUND_CODE = 404
    const val VALIDATION_ERROR_CODE = 422
    const val FAILED_DEPENDENCY = 424
    const val SERVER_ERROR_CODE = 500
}

enum class ErrorType { NO_CONNECTION, BAD_REQUEST, AUTHORIZATION_REQUIRED,
                       FORBIDDEN, NOT_FOUND, SERVER_ERROR, UNKNOWN_ERROR }

class ZonaApiException(val statusCode: Int, message: String) : Exception(message)
```

`ZonaApi.requireOk()` кладёт `status.value` в исключение → `ApiErrorMapper` мапит код в `ErrorType`
→ текст для UI строится из `ErrorType`, а не из `.lowercase()`.

> Поправка к таблице руководителя: у него 401/403/400 свалены в `NOT_FOUND` — это собьёт UX
> (пользователь увидит «не найдено» вместо «войдите заново»). Развести: 401→AUTHORIZATION_REQUIRED,
> 403→FORBIDDEN, 400/422/424→BAD_REQUEST.

После этого старый `ApiErrors.kt` удалить.

### 2. ⬜ Модели не разнесены по слоям — слой домена «протёк»

Главный незакрытый пункт. Все DTO лежат в одном `Models.kt` в пакете `…zona` и помечены
`@Serializable`, **и их же импортирует домен**:

```kotlin
// domain/repository/ZonaRepository.kt
interface ZonaRepository {
    suspend fun fetchMe(): Result<UserDto>          // ← домен зависит от data-DTO
    suspend fun getCourses(): Result<List<CourseDto>>
}
```

Сейчас `domain` и `presentation` напрямую завязаны на формат JSON бэка. Поменяется ответ
сервера — придётся править домен и экраны. Это противоречит правилам из вашего же `ARCHITECTURE.md`
(«Domain не импортирует Ktor/сериализацию»).

**Сделать:**
- `data/model/*Dto` — `@Serializable`, форма ответа бэка;
- `domain/model/*` — чистые модели без аннотаций;
- маппинг DTO→domain — в `ZonaRepositoryImpl`;
- `ZonaRepository`, `ZonaUseCases`, стора и UI переключить на доменные модели.

Можно делать поэтапно (сначала `User`, `Course`, `Assignment`), не за один заход.

### 3. ⬜ Роли — всё ещё «магические строки»

`UserDto.role: String`, и разветвление по роли — строкой:

```kotlin
// ZonaApp.kt:68
when (user.role) {
    "TEACHER" -> TeacherApp(...)
    "STUDENT" -> StudentApp(...)
    else      -> AdminApp(...)
}
```

То же в `ZonaUseCases.canSubmitTeacherApplication`: `status == "NEED_INFO" || status == "REJECTED"`.

**Сделать:** `enum class UserRole { STUDENT, TEACHER, ADMIN }` (+ enum для статуса заявки),
парсить строку бэка в enum на этапе маппинга DTO→domain (увязывается с пунктом 2), а в `ZonaApp`
делать `when(role)` без `else`-заглушки под админа (явный `UserRole.ADMIN`).

### 4. 🟡 Бэкенд — монолитный роутинг, тяжело масштабировать

Замечание руководителя про «толстую прослойку» — подтверждается, но не там, где казалось:

| Файл | Строк |
|------|------:|
| `Application.kt` | 92 ✅ |
| **`Routing.kt`** | **1380** ⚠️ |
| `Dto.kt` | 383 |

`Application.kt` уже компактный, декомпозиция начата (`Assignments.kt`, `Chat.kt`, `Schedule.kt`,
`Profile.kt`, `Availability.kt`, `TeacherApplications.kt`). Но `Routing.kt` на 1380 строк всё ещё
смешивает маршруты + бизнес-логику + запросы к БД.

**Сделать:**
- дорезать `Routing.kt` по доменам (часть уже вынесена — довести до конца);
- внутри каждого домена разделить: маршрут → сервис (логика) → DAO (Exposed-запросы);
- транзакции узкие: `transaction { }` только вокруг обращений к БД, а не вокруг всего обработчика
  (руководитель прямо указал на раздутое потребление памяти при транзакциях).

> По договорённости с руководителем подход бэка не меняем (тонкая связка с Postgres),
> разбор бэка — отдельно. Здесь — только расслоение и сужение транзакций.

---

## Попутные находки (моё ревью)

- **Баг в `ZonaApi`:** методы без тела ответа (`enroll`, `bookSession`, `submitAssignment`,
  `saveTeacherAvailability`, …) вызывают `.requireOk<ErrorResponse>()`. На успешном `200/204`
  тело — не `ErrorResponse` (или пустое) → `body<ErrorResponse>()` упадёт на десериализации.
  Нужен путь «успех без тела»: проверять только статус, возвращать `Unit`.
- **`local.properties` в git** — проверь, что там нет секретов (обычно не коммитят).
- **Константы вместо чисел** (отдельное замечание руководителя) — пройтись по `ui/` и
  `deadlineDays * 24L * 60 * 60 * 1000` в `ZonaUseCases` вынести в именованную константу.
- **Тесты** — для защиты сильно поможет юнит-тест на `mapToErrorType()` и на 2–3 policy/use-case:
  это наглядный аргумент «зачем мы разносили на слои».

---

## Приоритеты

1. **Закоммитить новый слой** (вся работа сейчас в untracked).
2. **`ErrorType` + `statusCode` в `ZonaApiException`**, маппинг по кодам, удалить `ApiErrors.kt` (п.1).
3. **Фикс `requireOk<ErrorResponse>()`** для методов без тела (находки).
4. **Разнести модели data/domain + роли в enum** (п.2, п.3).
5. **Дорезать `Routing.kt`, сузить транзакции** (п.4).
6. **Константы, тесты на маппер/policy.**
