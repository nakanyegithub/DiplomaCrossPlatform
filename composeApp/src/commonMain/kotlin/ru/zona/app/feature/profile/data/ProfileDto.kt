package ru.zona.app.feature.profile.data

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequest(
    val displayName: String,
    val bio: String,
    val avatarUrl: String? = null,
)
