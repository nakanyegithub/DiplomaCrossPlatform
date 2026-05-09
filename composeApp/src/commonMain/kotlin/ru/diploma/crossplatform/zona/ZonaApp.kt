package ru.diploma.crossplatform.zona

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.min

private sealed interface ZonaScreen {
    data object Auth : ZonaScreen
    data object Home : ZonaScreen

    data class CourseLessons(
        val course: CourseDto,
    ) : ZonaScreen

    data class LessonPlay(
        val lesson: LessonDto,
    ) : ZonaScreen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZonaApp() {
    val scope = rememberCoroutineScope()
    val api = remember { ZonaApi(createZonaHttpClient()) }

    var user by remember { mutableStateOf<UserDto?>(null) }
    var stack by remember { mutableStateOf<List<ZonaScreen>>(listOf(ZonaScreen.Auth)) }
    var busy by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var studentRefreshKey by rememberSaveable { mutableIntStateOf(0) }
    var teacherRefreshKey by rememberSaveable { mutableIntStateOf(0) }

    fun push(s: ZonaScreen) {
        stack = stack + s
    }

    fun pop() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }

    fun replaceRoot(s: ZonaScreen) {
        stack = listOf(s)
    }

    val top = stack.lastOrNull() ?: ZonaScreen.Auth

    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(3200)
            toast = null
        }
    }

    if (toast != null) {
        AlertDialog(
            onDismissRequest = { toast = null },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("OK") } },
            text = { Text(toast!!) },
        )
    }

    when (top) {
        ZonaScreen.Auth -> {
            AuthScreen(
                busy = busy,
                onLogin = { email, pass ->
                    scope.launch {
                        busy = true
                        runCatching { api.login(email, pass) }
                            .onSuccess {
                                api.bearerToken = it.token
                                user = it.user
                                studentRefreshKey++
                                teacherRefreshKey++
                                replaceRoot(ZonaScreen.Home)
                            }
                            .onFailure { toast = friendlyApiError(it) }
                        busy = false
                    }
                },
                onRegister = { email, pass, name ->
                    scope.launch {
                        busy = true
                        runCatching { api.register(email, pass, name) }
                            .onSuccess {
                                api.bearerToken = it.token
                                user = it.user
                                studentRefreshKey++
                                teacherRefreshKey++
                                replaceRoot(ZonaScreen.Home)
                            }
                            .onFailure { toast = friendlyApiError(it) }
                        busy = false
                    }
                },
            )
        }

        ZonaScreen.Home -> {
            val u = user
            if (u == null) {
                replaceRoot(ZonaScreen.Auth)
            } else {
                when (u.role) {
                    "TEACHER" ->
                        TeacherHome(
                            user = u,
                            api = api,
                            refreshKey = teacherRefreshKey,
                            onNeedRefresh = { teacherRefreshKey++ },
                            onMessage = { toast = it },
                            onLogout = {
                                api.bearerToken = null
                                user = null
                                replaceRoot(ZonaScreen.Auth)
                            },
                        )

                    "STUDENT" ->
                        StudentHome(
                            user = u,
                            api = api,
                            refreshKey = studentRefreshKey,
                            onNeedRefresh = {
                                studentRefreshKey++
                                teacherRefreshKey++
                            },
                            onOpenCourse = { push(ZonaScreen.CourseLessons(it)) },
                            onMessage = { toast = it },
                            onLogout = {
                                api.bearerToken = null
                                user = null
                                replaceRoot(ZonaScreen.Auth)
                            },
                        )

                    else ->
                        AdminHome(
                            user = u,
                            api = api,
                            onMessage = { toast = it },
                            onLogout = {
                                api.bearerToken = null
                                user = null
                                replaceRoot(ZonaScreen.Auth)
                            },
                        )
                }
            }
        }

        is ZonaScreen.CourseLessons -> {
            val c = (top as ZonaScreen.CourseLessons).course
            CourseLessonsScreen(
                course = c,
                api = api,
                onBack = { pop() },
                openLesson = { push(ZonaScreen.LessonPlay(it)) },
                onEnroll = {
                    scope.launch {
                        runCatching { api.enroll(c.id) }
                            .onSuccess {
                                toast = "Вы записаны на курс"
                                studentRefreshKey++
                                teacherRefreshKey++
                            }
                            .onFailure { toast = friendlyApiError(it) }
                    }
                },
                onError = { toast = it },
            )
        }

        is ZonaScreen.LessonPlay -> {
            val l = (top as ZonaScreen.LessonPlay).lesson
            LessonPlayScreen(
                lesson = l,
                api = api,
                onBack = { pop() },
                onMessage = { toast = it },
                onProgressSaved = {
                    studentRefreshKey++
                    teacherRefreshKey++
                },
            )
        }
    }
}

@Composable
private fun AuthScreen(
    busy: Boolean,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
) {
    var reg by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("student@zona.local") }
    var pass by rememberSaveable { mutableStateOf("student123") }
    var name by rememberSaveable { mutableStateOf("Новый ученик") }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Zona", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Языковые курсы, задания, домашка и заметки",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !reg, onClick = { reg = false }, label = { Text("Вход") })
            FilterChip(selected = reg, onClick = { reg = true }, label = { Text("Регистрация") })
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Пароль") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (reg) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Имя") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
        if (busy) {
            CircularProgressIndicator()
        } else {
            Button(onClick = { if (reg) onRegister(email, pass, name) else onLogin(email, pass) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (reg) "Создать ученика" else "Войти")
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Сначала запусти сервер: gradlew.bat :server:run",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Демо: teacher@zona.local / teacher123 · student@zona.local / student123",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StudentHome(
    user: UserDto,
    api: ZonaApi,
    refreshKey: Int,
    onNeedRefresh: () -> Unit,
    onOpenCourse: (CourseDto) -> Unit,
    onMessage: (String) -> Unit,
    onLogout: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Задания", "Учителя", "Мои ДЗ", "Топ", "Профиль")

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.School, contentDescription = null) },
                    label = { Text(tabs[0]) },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Text("👩‍🏫") },
                    label = { Text(tabs[1]) },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Text("ДЗ") },
                    label = { Text(tabs[2]) },
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Text("🏆") },
                    label = { Text(tabs[3]) },
                )
                NavigationBarItem(
                    selected = tab == 4,
                    onClick = { tab = 4 },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text(tabs[4]) },
                )
            }
        },
    ) { pad ->
        when (tab) {
            0 ->
                StudentAssignmentsTab(
                    padding = pad,
                    api = api,
                    refreshKey = refreshKey,
                    onOpenCourse = onOpenCourse,
                    onError = onMessage,
                )

            1 ->
                TeachersBookingTab(
                    padding = pad,
                    api = api,
                    refreshKey = refreshKey,
                    onBooked = onNeedRefresh,
                    onError = onMessage,
                )

            2 ->
                StudentHomeworkTab(
                    padding = pad,
                    api = api,
                    refreshKey = refreshKey,
                    onError = onMessage,
                )

            3 ->
                LeaderboardTab(
                    padding = pad,
                    api = api,
                    refreshKey = refreshKey,
                    onError = onMessage,
                )

            else ->
                ProfileTab(
                    padding = pad,
                    user = user,
                    onLogout = onLogout,
                )
        }
    }
}

@Composable
private fun TeacherHome(
    user: UserDto,
    api: ZonaApi,
    refreshKey: Int,
    onNeedRefresh: () -> Unit,
    onMessage: (String) -> Unit,
    onLogout: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Ученики", "Домашка", "Заметки", "Профиль")

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("👥") }, label = { Text(tabs[0]) })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("ДЗ") }, label = { Text(tabs[1]) })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("✍") }, label = { Text(tabs[2]) })
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text(tabs[3]) },
                )
            }
        },
    ) { pad ->
        when (tab) {
            0 -> TeacherStudentsTab(padding = pad, api = api, refreshKey = refreshKey, onError = onMessage)
            1 ->
                TeacherHomeworkTab(
                    padding = pad,
                    api = api,
                    refreshKey = refreshKey,
                    onSaved = {
                        onMessage("Домашнее задание сохранено")
                        onNeedRefresh()
                    },
                    onError = onMessage,
                )

            2 ->
                TeacherNotesTab(
                    padding = pad,
                    api = api,
                    refreshKey = refreshKey,
                    onSaved = {
                        onMessage("Приватная заметка сохранена")
                        onNeedRefresh()
                    },
                    onError = onMessage,
                )

            else -> ProfileTab(padding = pad, user = user, onLogout = onLogout)
        }
    }
}

@Composable
private fun AdminHome(
    user: UserDto,
    api: ZonaApi,
    onMessage: (String) -> Unit,
    onLogout: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("A") }, label = { Text("Пользователи") })
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("Профиль") },
                )
            }
        },
    ) { pad ->
        when (tab) {
            0 -> AdminTab(padding = pad, api = api, onError = onMessage)
            else -> ProfileTab(padding = pad, user = user, onLogout = onLogout)
        }
    }
}

@Composable
private fun CoursesTab(
    padding: PaddingValues,
    api: ZonaApi,
    refreshKey: Int,
    onOpenCourse: (CourseDto) -> Unit,
    onError: (String) -> Unit,
) {
    var list by remember { mutableStateOf<List<CourseDto>?>(null) }
    LaunchedEffect(refreshKey) {
        runCatching { api.courses() }
            .onSuccess { list = it }
            .onFailure { onError(friendlyApiError(it)) }
    }
    Column(Modifier.padding(padding)) {
        Text("Курсы языков", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        when (val l = list) {
            null -> CircularProgressIndicator(Modifier.padding(24.dp))
            else ->
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(l, key = { it.id }) { c ->
                        Card(onClick = { onOpenCourse(c) }, modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(c.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${c.languageFrom.uppercase()} → ${c.languageTo.uppercase()} · ${c.teacherName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                c.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                if (c.enrolled) Text("Вы записаны", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun StudentAssignmentsTab(
    padding: PaddingValues,
    api: ZonaApi,
    refreshKey: Int,
    onOpenCourse: (CourseDto) -> Unit,
    onError: (String) -> Unit,
) {
    var allCourses by remember { mutableStateOf<List<CourseDto>?>(null) }
    var progress by remember { mutableStateOf<Map<Long, Float>>(emptyMap()) }
    LaunchedEffect(refreshKey) {
        runCatching {
            val courses = api.courses()
            val map = courses.associate { course ->
                val lessons = api.lessons(course.id)
                val total = lessons.sumOf { it.exerciseCount }
                val done = lessons.sumOf { it.completedByUser }
                val p = if (total > 0) done.toFloat() / total.toFloat() else 0f
                course.id to p
            }
            courses to map
        }.onSuccess {
            allCourses = it.first
            progress = it.second
        }
            .onFailure { onError(friendlyApiError(it)) }
    }
    Column(Modifier.padding(padding)) {
        Text("Задания (для самостоятельной практики)", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        when (val list = allCourses) {
            null -> CircularProgressIndicator(Modifier.padding(24.dp))
            else ->
                if (list.isEmpty()) {
                    Text("Курсы пока отсутствуют.", modifier = Modifier.padding(horizontal = 16.dp))
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(list, key = { it.id }) { course ->
                            Card(onClick = { onOpenCourse(course) }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(course.title, style = MaterialTheme.typography.titleMedium)
                                    Text("Преподаватель: ${course.teacherName}", style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(8.dp))
                                    val p = progress[course.id] ?: 0f
                                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                                    Text("Прогресс: ${(p * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                                    Text("Открыть задания курса", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun StudentHomeworkTab(
    padding: PaddingValues,
    api: ZonaApi,
    refreshKey: Int,
    onError: (String) -> Unit,
) {
    var homework by remember { mutableStateOf<List<StudentHomeworkDto>?>(null) }
    LaunchedEffect(refreshKey) {
        runCatching { api.studentHomework() }
            .onSuccess { homework = it }
            .onFailure { onError(friendlyApiError(it)) }
    }
    Column(Modifier.padding(padding)) {
        Text("Домашние задания", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        when (val list = homework) {
            null -> CircularProgressIndicator(Modifier.padding(24.dp))
            else ->
                if (list.isEmpty()) {
                    Text("Пока нет домашнего задания.", modifier = Modifier.padding(horizontal = 16.dp))
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(list, key = { "${it.teacherId}-${it.homework.hashCode()}" }) { hw ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("От преподавателя: ${hw.teacherName}", style = MaterialTheme.typography.labelLarge)
                                    Spacer(Modifier.height(8.dp))
                                    Text(hw.homework)
                                }
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun TeachersBookingTab(
    padding: PaddingValues,
    api: ZonaApi,
    refreshKey: Int,
    onBooked: () -> Unit,
    onError: (String) -> Unit,
) {
    var teachers by remember { mutableStateOf<List<TeacherShortDto>>(emptyList()) }
    var selected by rememberSaveable { mutableStateOf<TeacherShortDto?>(null) }
    var openDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) {
        runCatching { api.teachers() }
            .onSuccess {
                teachers = it
                if (selected == null) selected = it.firstOrNull()
            }
            .onFailure { onError(friendlyApiError(it)) }
    }

    if (openDialog && selected != null) {
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val days = remember(today) { (0..13).map { today.plus(DatePeriod(days = it)) } }
        val slots = remember {
            buildList {
                for (h in 0..23) {
                    add("${h.toString().padStart(2, '0')}:00")
                    if (h != 23) add("${h.toString().padStart(2, '0')}:30")
                }
                add("23:59")
            }
        }
        var selectedDayIdx by rememberSaveable { mutableIntStateOf(0) }
        var selectedTime by rememberSaveable { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { openDialog = false },
            title = { Text("Бронь у ${selected!!.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Выбери день", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(days.size) { i ->
                            val d = days[i]
                            val selectedDay = i == selectedDayIdx
                            Card(
                                modifier = Modifier.clickable { selectedDayIdx = i }.padding(vertical = 2.dp),
                                colors =
                                    if (selectedDay) {
                                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                    } else {
                                        CardDefaults.cardColors()
                                    },
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(weekdayRu(d), style = MaterialTheme.typography.labelMedium)
                                    Text("${d.dayOfMonth}.${d.monthNumber.toString().padStart(2, '0')}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Выбери время (00:00–23:59)", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(slots) { time ->
                            val selectedSlot = selectedTime == time
                            Card(
                                modifier = Modifier.clickable { selectedTime = time }.padding(vertical = 2.dp),
                                colors =
                                    if (selectedSlot) {
                                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                    } else {
                                        CardDefaults.cardColors()
                                    },
                            ) {
                                Text(time, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val time = selectedTime ?: return@TextButton
                        val date = days[selectedDayIdx]
                        val hour = time.substringBefore(':').toInt()
                        val minute = time.substringAfter(':').toInt()
                        val dateTime = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute)
                        val whenMs = dateTime.toInstant(tz).toEpochMilliseconds()
                        val teacherId = selected!!.id
                        scope.launch {
                            runCatching { api.requestTeacherBooking(teacherId, whenMs) }
                                .onSuccess {
                                    onBooked()
                                    openDialog = false
                                }
                                .onFailure { onError(friendlyApiError(it)) }
                        }
                    },
                    enabled = selectedTime != null,
                ) {
                    Text("Отправить")
                }
            },
            dismissButton = {
                TextButton(onClick = { openDialog = false }) { Text("Отмена") }
            },
        )
    }

    Column(Modifier.padding(padding)) {
        Text("Учителя", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(teachers, key = { it.id }) { t ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(t.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                selected = t
                                openDialog = true
                            },
                        ) {
                            Text("Забронировать")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardTab(
    padding: PaddingValues,
    api: ZonaApi,
    refreshKey: Int,
    onError: (String) -> Unit,
) {
    var list by remember { mutableStateOf<List<LeaderboardEntryDto>?>(null) }
    LaunchedEffect(refreshKey) {
        runCatching { api.leaderboard() }
            .onSuccess { list = it }
            .onFailure { onError(friendlyApiError(it)) }
    }

    Column(Modifier.padding(padding)) {
        Text("Топ лидеров", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        when (val rows = list) {
            null -> CircularProgressIndicator(Modifier.padding(24.dp))
            else ->
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rows, key = { it.userId }) { row ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(row.userName, style = MaterialTheme.typography.titleMedium)
                                    Text("Верных: ${row.totalCorrect}/${row.totalAttempts}", style = MaterialTheme.typography.labelSmall)
                                }
                                Text("${row.totalXp} XP", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun TeacherStudentsTab(
    padding: PaddingValues,
    api: ZonaApi,
    refreshKey: Int,
    onError: (String) -> Unit,
) {
    var students by remember { mutableStateOf<List<TeacherStudentDto>?>(null) }
    var requests by remember { mutableStateOf<List<TeacherBookingRequestDto>?>(null) }
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) {
        runCatching { api.teacherStudents() }
            .onSuccess { students = it }
            .onFailure { onError(friendlyApiError(it)) }
        runCatching { api.teacherBookingRequests() }
            .onSuccess { requests = it }
            .onFailure { onError(friendlyApiError(it)) }
    }

    Column(Modifier.padding(padding)) {
        Text("Мои ученики", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        val pending = requests?.filter { it.status == "PENDING" }.orEmpty()
        if (pending.isNotEmpty()) {
            Text("Новые заявки", modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary)
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(220.dp)) {
                items(pending, key = { it.id }) { req ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(req.studentName, style = MaterialTheme.typography.titleSmall)
                            Text(req.studentEmail, style = MaterialTheme.typography.bodySmall)
                            Text("Запрос на ${formatEpochForUser(req.scheduledAtEpochMs)}", style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            runCatching { api.confirmBookingRequest(req.id) }
                                                .onSuccess {
                                                    requests = requests?.map { if (it.id == req.id) it.copy(status = "CONFIRMED") else it }
                                                    runCatching { students = api.teacherStudents() }
                                                }
                                                .onFailure { onError(friendlyApiError(it)) }
                                        }
                                    },
                                ) { Text("Подтвердить") }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            runCatching { api.declineBookingRequest(req.id) }
                                                .onSuccess {
                                                    requests = requests?.map { if (it.id == req.id) it.copy(status = "DECLINED") else it }
                                                }
                                                .onFailure { onError(friendlyApiError(it)) }
                                        }
                                    },
                                ) { Text("Отказать") }
                            }
                        }
                    }
                }
            }
        }
        when (val list = students) {
            null -> CircularProgressIndicator(Modifier.padding(24.dp))
            else ->
                if (list.isEmpty()) {
                    Text("Пока нет подтверждённых учеников.", modifier = Modifier.padding(horizontal = 16.dp))
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(list, key = { it.studentId }) { st ->
                            val open = expanded[st.studentId] == true
                            Card(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { expanded[st.studentId] = !open },
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(st.studentName, style = MaterialTheme.typography.titleMedium)
                                    Text(st.studentEmail, style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        if (open) "Скрыть прогресс" else "Показать прогресс",
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    if (open) {
                                        Spacer(Modifier.height(10.dp))
                                        st.lastActivityEpochMs?.let { Text("Последняя активность: $it", style = MaterialTheme.typography.labelSmall) }
                                        if (st.courseProgress.isEmpty()) {
                                            Text("Нет записей по курсам.")
                                        } else {
                                            st.courseProgress.forEach { cp ->
                                                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                                    Column(Modifier.padding(12.dp)) {
                                                        Text(cp.courseTitle, style = MaterialTheme.typography.labelLarge)
                                                        Text("Уроки: ${cp.completedLessons}/${cp.totalLessons}")
                                                        Text("Задания: ${cp.completedExercises}/${cp.totalExercises}")
                                                    }
                                                }
                                                Spacer(Modifier.height(6.dp))
                                            }
                                        }
                                        if (st.homework.isNotBlank()) {
                                            Text("Домашка: ${st.homework}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        if (st.teacherHistory.isNotEmpty()) {
                                            Spacer(Modifier.height(8.dp))
                                            Text("История преподавателей:", style = MaterialTheme.typography.labelMedium)
                                            st.teacherHistory.take(5).forEach { h ->
                                                Text("• ${h.teacherName} (${h.status})", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun TeacherHomeworkTab(
    padding: PaddingValues,
    api: ZonaApi,
    refreshKey: Int,
    onSaved: () -> Unit,
    onError: (String) -> Unit,
) {
    var students by remember { mutableStateOf<List<TeacherStudentDto>>(emptyList()) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) {
        runCatching { api.teacherStudents() }
            .onSuccess {
                students = it
                if (selectedId == null || students.none { row -> row.studentId == selectedId }) {
                    selectedId = students.firstOrNull()?.studentId
                }
            }
            .onFailure { onError(friendlyApiError(it)) }
    }

    Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Назначить домашнее задание", style = MaterialTheme.typography.titleLarge)
        DropdownPicker(
            title = "Ученик",
            selectedText = students.firstOrNull { it.studentId == selectedId }?.studentName ?: "Выберите ученика",
            expanded = menuExpanded,
            onExpandedChange = { menuExpanded = it },
        ) {
            students.forEach { st ->
                DropdownMenuItem(
                    text = { Text(st.studentName) },
                    onClick = {
                        selectedId = st.studentId
                        menuExpanded = false
                    },
                )
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Текст задания + дедлайн") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        Button(
            onClick = {
                val sid = selectedId ?: return@Button
                scope.launch {
                    runCatching { api.assignHomework(sid, text.trim()) }
                        .onSuccess {
                            text = ""
                            onSaved()
                        }
                        .onFailure { onError(friendlyApiError(it)) }
                }
            },
            enabled = selectedId != null && text.isNotBlank(),
        ) {
            Text("Сохранить")
        }
    }
}

@Composable
private fun TeacherNotesTab(
    padding: PaddingValues,
    api: ZonaApi,
    refreshKey: Int,
    onSaved: () -> Unit,
    onError: (String) -> Unit,
) {
    var students by remember { mutableStateOf<List<TeacherStudentDto>>(emptyList()) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) {
        runCatching { api.teacherStudents() }
            .onSuccess {
                students = it
                if (selectedId == null || students.none { row -> row.studentId == selectedId }) {
                    selectedId = students.firstOrNull()?.studentId
                }
                text = students.firstOrNull { it.studentId == selectedId }?.notes.orEmpty()
            }
            .onFailure { onError(friendlyApiError(it)) }
    }

    Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Приватные заметки по ученику", style = MaterialTheme.typography.titleLarge)
        DropdownPicker(
            title = "Ученик",
            selectedText = students.firstOrNull { it.studentId == selectedId }?.studentName ?: "Выберите ученика",
            expanded = menuExpanded,
            onExpandedChange = { menuExpanded = it },
        ) {
            students.forEach { st ->
                DropdownMenuItem(
                    text = { Text(st.studentName) },
                    onClick = {
                        selectedId = st.studentId
                        text = st.notes
                        menuExpanded = false
                    },
                )
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Заметка (видит только преподаватель)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
        )
        Button(
            onClick = {
                val sid = selectedId ?: return@Button
                scope.launch {
                    runCatching { api.saveTeacherNote(sid, text.trim()) }
                        .onSuccess { onSaved() }
                        .onFailure { onError(friendlyApiError(it)) }
                }
            },
            enabled = selectedId != null,
        ) {
            Text("Сохранить")
        }
    }
}

@Composable
private fun DropdownPicker(
    title: String,
    selectedText: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium)
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(true) },
        ) {
            Text(selectedText, modifier = Modifier.padding(12.dp))
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
            ) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseLessonsScreen(
    course: CourseDto,
    api: ZonaApi,
    onBack: () -> Unit,
    openLesson: (LessonDto) -> Unit,
    onEnroll: () -> Unit,
    onError: (String) -> Unit,
) {
    var lessons by remember { mutableStateOf<List<LessonDto>?>(null) }
    LaunchedEffect(course.id) {
        runCatching { api.lessons(course.id) }
            .onSuccess { lessons = it }
            .onFailure { onError(friendlyApiError(it)) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(course.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            if (!course.enrolled) {
                Button(onClick = onEnroll) { Text("Записаться на курс") }
                Spacer(Modifier.height(12.dp))
            }
            when (val ls = lessons) {
                null -> CircularProgressIndicator()
                else ->
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ls, key = { it.id }) { le ->
                            val done = le.completedByUser >= le.exerciseCount && le.exerciseCount > 0
                            Card(onClick = { openLesson(le) }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(le.title)
                                    Text("Заданий: ${le.completedByUser}/${le.exerciseCount}", style = MaterialTheme.typography.labelMedium)
                                    if (done) Text("Урок пройден ✓", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonPlayScreen(
    lesson: LessonDto,
    api: ZonaApi,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    onProgressSaved: () -> Unit,
) {
    var exercises by remember { mutableStateOf<List<ExercisePublicDto>?>(null) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var answer by rememberSaveable { mutableStateOf("") }
    var last by remember { mutableStateOf<SubmitExerciseResponse?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var allowRetake by rememberSaveable { mutableStateOf(lesson.attemptedByUser < lesson.exerciseCount || lesson.exerciseCount == 0) }
    val mistakes = remember { mutableStateMapOf<Long, String>() }
    val solved = remember { mutableStateMapOf<Long, Boolean>() }

    LaunchedEffect(lesson.id) {
        runCatching { api.exercises(lesson.id) }
            .onSuccess {
                exercises = it
                index = 0
                answer = ""
                last = null
                submitting = false
                allowRetake = lesson.attemptedByUser < lesson.exerciseCount || lesson.exerciseCount == 0
                mistakes.clear()
                solved.clear()
            }
            .onFailure { onMessage(friendlyApiError(it)) }
    }

    val list = exercises
    val ex = list?.getOrNull(index)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(lesson.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxSize(),
        ) {
            if (list != null && list.isNotEmpty()) {
                val p = min((index + 1).toFloat() / list.size.toFloat(), 1f)
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text("Прогресс: ${(p * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(16.dp))
            }

            when {
                list == null -> CircularProgressIndicator()
                list.isEmpty() -> Text("В этом уроке пока нет заданий.")
                !allowRetake -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Урок уже пройден", style = MaterialTheme.typography.titleLarge)
                            Text("Старый результат: ${lesson.completedByUser} / ${lesson.exerciseCount}")
                            Text(
                                "Можешь оставить как есть или пройти заново для практики.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = {
                                    allowRetake = true
                                    index = 0
                                    answer = ""
                                    last = null
                                    mistakes.clear()
                                    solved.clear()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Пройти заново")
                            }
                        }
                    }
                }
                ex == null -> {
                    val total = list.size
                    val correct = solved.count { it.value }
                    val wrong = total - correct
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Красава, прошёл урок!", style = MaterialTheme.typography.titleLarge)
                            Text("Результат: $correct / $total")
                            Text("Ошибок: $wrong")
                            if (wrong > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text("Где ошибся:", style = MaterialTheme.typography.labelLarge)
                                list.filter { mistakes.containsKey(it.id) }.forEach { q ->
                                    Text("• ${q.prompt}")
                                    Text("  Твой ответ: ${mistakes[q.id].orEmpty()}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                                Text("Готово")
                            }
                        }
                    }
                }
                else -> {
                    Text(ex.prompt, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    if (ex.type == "CHOICE" && ex.choices != null) {
                        ex.choices.forEach { ch ->
                            OutlinedButton(
                                onClick = { answer = ch },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            ) {
                                Text(ch)
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = answer,
                            onValueChange = { answer = it },
                            label = { Text("Ответ") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    val scope = rememberCoroutineScope()
                    Button(
                        onClick = {
                            if (submitting) return@Button
                            val given = answer.trim()
                            if (given.isBlank()) {
                                onMessage("Введите ответ")
                                return@Button
                            }
                            scope.launch {
                                submitting = true
                                runCatching { api.submitExercise(ex.id, given) }
                                    .onSuccess { res ->
                                        last = res
                                        if (res.correct) {
                                            solved[ex.id] = true
                                            mistakes.remove(ex.id)
                                        } else {
                                            solved[ex.id] = false
                                            mistakes[ex.id] = given
                                        }
                                        if (res.xp > 0) onProgressSaved()
                                        onMessage(
                                            when {
                                                res.correct && res.xp > 0 -> "+${res.xp} XP"
                                                res.correct && res.xp == 0 -> "Уже зачтено ранее"
                                                else -> "Ошибка"
                                            },
                                        )
                                        index++
                                        answer = ""
                                    }
                                    .onFailure { onMessage(friendlyApiError(it)) }
                                submitting = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !submitting,
                    ) {
                        Text(if (submitting) "Проверка..." else "Проверить")
                    }
                    last?.let {
                        Text(
                            if (it.correct) "Верно!" else "Неверно",
                            color = if (it.correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

private fun weekdayRu(date: LocalDate): String =
    when (date.dayOfWeek.name) {
        "MONDAY" -> "Пн"
        "TUESDAY" -> "Вт"
        "WEDNESDAY" -> "Ср"
        "THURSDAY" -> "Чт"
        "FRIDAY" -> "Пт"
        "SATURDAY" -> "Сб"
        "SUNDAY" -> "Вс"
        else -> date.dayOfWeek.name
    }

private fun formatEpochForUser(ms: Long): String {
    val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
    val dd = dt.dayOfMonth.toString().padStart(2, '0')
    val mm = dt.monthNumber.toString().padStart(2, '0')
    val hh = dt.hour.toString().padStart(2, '0')
    val min = dt.minute.toString().padStart(2, '0')
    return "$dd.$mm $hh:$min"
}

@Composable
private fun ProfileTab(
    padding: PaddingValues,
    user: UserDto,
    onLogout: () -> Unit,
) {
    Column(
        Modifier
            .padding(padding)
            .padding(24.dp),
    ) {
        Text(user.displayName, style = MaterialTheme.typography.headlineSmall)
        Text(user.email)
        Text(
            when (user.role) {
                "ADMIN" -> "Администратор"
                "TEACHER" -> "Преподаватель"
                else -> "Ученик"
            },
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onLogout) { Text("Выйти") }
    }
}

@Composable
private fun AdminTab(
    padding: PaddingValues,
    api: ZonaApi,
    onError: (String) -> Unit,
) {
    var users by remember { mutableStateOf<List<UserDto>?>(null) }
    LaunchedEffect(Unit) {
        runCatching { api.adminUsers() }
            .onSuccess { users = it }
            .onFailure { onError(friendlyApiError(it)) }
    }
    Column(Modifier.padding(padding)) {
        Text("Пользователи", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        when (val u = users) {
            null -> CircularProgressIndicator(Modifier.padding(24.dp))
            else ->
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(u, key = { it.id }) { row ->
                        Text("${row.displayName} (${row.role}) — ${row.email}")
                        Spacer(Modifier.height(8.dp))
                    }
                }
        }
    }
}
