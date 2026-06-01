package ru.zona.app.feature.profile.domain

import ru.zona.app.core.model.User
import ru.zona.app.core.result.Outcome

interface ProfileRepository {
    suspend fun fetchMe(): Outcome<User>

    suspend fun updateProfile(
        displayName: String,
        bio: String,
        avatarUrl: String?,
    ): Outcome<User>
}
