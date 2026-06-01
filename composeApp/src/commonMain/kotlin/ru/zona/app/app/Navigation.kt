package ru.zona.app.app

import androidx.compose.runtime.mutableStateListOf

/** Экраны поверх главного таб-навигатора (стек переходов). */
sealed interface Destination {
    data class CourseDetail(val courseId: Long, val title: String) : Destination
    data class Lesson(val lessonId: Long, val title: String) : Destination
    data class Deck(val deckId: Long, val title: String) : Destination
    data class Chat(val conversationId: Long, val peerName: String) : Destination
    data class CourseEditor(val courseId: Long, val title: String) : Destination
    data class ManageDeck(val deckId: Long, val title: String) : Destination
    data class TeacherProfile(val teacherId: Long, val name: String) : Destination
    data object Teachers : Destination
    data object Application : Destination
    data object Wallet : Destination
    data object Authoring : Destination
}

/** Простой стек навигации без сторонних библиотек — работает на всех платформах. */
class NavStack {
    private val stack = mutableStateListOf<Destination>()

    val current: Destination? get() = stack.lastOrNull()

    fun push(d: Destination) { stack.add(d) }

    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    fun clear() { stack.clear() }
}
