package ru.zona.app.core.network

import kotlinx.serialization.Serializable

/** Единый формат тела ошибки от сервера. */
@Serializable
data class ApiErrorBody(
    val error: String = "",
    val code: Int? = null,
)
