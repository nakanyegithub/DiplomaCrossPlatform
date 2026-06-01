package ru.zona.app.feature.profile.data

import io.ktor.client.call.body
import ru.zona.app.core.model.User
import ru.zona.app.core.network.safeApiCall
import ru.zona.app.core.result.Outcome
import ru.zona.app.feature.auth.data.UserDto
import ru.zona.app.feature.auth.data.toDomain
import ru.zona.app.feature.profile.domain.ProfileRepository

class ProfileRepositoryImpl(
    private val api: ProfileApi,
) : ProfileRepository {

    override suspend fun fetchMe(): Outcome<User> =
        safeApiCall(request = { api.me() }, decode = { it.body<UserDto>().toDomain() })

    override suspend fun updateProfile(
        displayName: String,
        bio: String,
        avatarUrl: String?,
    ): Outcome<User> =
        safeApiCall(
            request = {
                api.updateProfile(
                    UpdateProfileRequest(
                        displayName = displayName.trim(),
                        bio = bio.trim(),
                        avatarUrl = avatarUrl?.trim()?.ifBlank { null },
                    ),
                )
            },
            decode = { it.body<UserDto>().toDomain() },
        )
}
