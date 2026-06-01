package ru.zona.app.feature.profile.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.zona.app.core.model.User
import ru.zona.app.core.mvi.MviStore
import ru.zona.app.core.result.Outcome
import ru.zona.app.feature.profile.domain.ProfileRepository

data class ProfileState(
    val user: User,
    val editing: Boolean = false,
    val displayName: String = user.displayName,
    val bio: String = user.bio,
    val avatarUrl: String = user.avatarUrl.orEmpty(),
    val saving: Boolean = false,
    val refreshing: Boolean = false,
)

sealed interface ProfileIntent {
    data object Refresh : ProfileIntent
    data object StartEdit : ProfileIntent
    data object CancelEdit : ProfileIntent
    data class SetDisplayName(val value: String) : ProfileIntent
    data class SetBio(val value: String) : ProfileIntent
    data class SetAvatarUrl(val value: String) : ProfileIntent
    data object Save : ProfileIntent
}

sealed interface ProfileEffect {
    data class Message(val text: String) : ProfileEffect
    data class Saved(val user: User) : ProfileEffect
}

class ProfileStore(
    initialUser: User,
    private val repository: ProfileRepository,
    scope: CoroutineScope,
) : MviStore<ProfileState, ProfileIntent, ProfileEffect>(ProfileState(initialUser), scope) {

    override fun onIntent(intent: ProfileIntent) {
        when (intent) {
            ProfileIntent.Refresh -> refresh()
            ProfileIntent.StartEdit -> setState { it.copy(editing = true) }
            ProfileIntent.CancelEdit -> resetFields()
            is ProfileIntent.SetDisplayName -> setState { it.copy(displayName = intent.value) }
            is ProfileIntent.SetBio -> setState { it.copy(bio = intent.value) }
            is ProfileIntent.SetAvatarUrl -> setState { it.copy(avatarUrl = intent.value) }
            ProfileIntent.Save -> save()
        }
    }

    fun applyUser(user: User) {
        setState {
            ProfileState(
                user = user,
                displayName = user.displayName,
                bio = user.bio,
                avatarUrl = user.avatarUrl.orEmpty(),
            )
        }
    }

    private fun resetFields() {
        val u = currentState.user
        setState {
            it.copy(
                editing = false,
                displayName = u.displayName,
                bio = u.bio,
                avatarUrl = u.avatarUrl.orEmpty(),
            )
        }
    }

    private fun refresh() {
        if (currentState.refreshing) return
        setState { it.copy(refreshing = true) }
        scope.launch {
            when (val r = repository.fetchMe()) {
                is Outcome.Success ->
                    setState {
                        ProfileState(
                            user = r.data,
                            editing = it.editing,
                            displayName = if (it.editing) it.displayName else r.data.displayName,
                            bio = if (it.editing) it.bio else r.data.bio,
                            avatarUrl = if (it.editing) it.avatarUrl else r.data.avatarUrl.orEmpty(),
                            refreshing = false,
                        )
                    }
                is Outcome.Failure -> {
                    setState { it.copy(refreshing = false) }
                    emit(ProfileEffect.Message(r.message))
                }
            }
        }
    }

    private fun save() {
        val st = currentState
        if (st.saving || st.displayName.isBlank()) return
        setState { it.copy(saving = true) }
        scope.launch {
            when (
                val r =
                    repository.updateProfile(
                        displayName = st.displayName,
                        bio = st.bio,
                        avatarUrl = st.avatarUrl.ifBlank { null },
                    )
            ) {
                is Outcome.Success -> {
                    setState {
                        ProfileState(user = r.data, editing = false)
                    }
                    emit(ProfileEffect.Saved(r.data))
                }
                is Outcome.Failure -> {
                    setState { it.copy(saving = false) }
                    emit(ProfileEffect.Message(r.message))
                }
            }
        }
    }
}
