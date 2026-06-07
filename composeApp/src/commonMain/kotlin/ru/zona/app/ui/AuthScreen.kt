package ru.zona.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.zona.app.core.design.ZonaPrimaryButton
import ru.zona.app.core.design.ZonaTextField
import zona.resources.Res
import zona.resources.app_name
import zona.resources.auth_create_account
import zona.resources.auth_email
import zona.resources.auth_login
import zona.resources.auth_name
import zona.resources.auth_password
import zona.resources.auth_please_wait
import zona.resources.auth_register
import zona.resources.auth_to_login
import zona.resources.auth_to_register
import zona.resources.auth_welcome_back

/**
 * Глупый экран: только рисует state и шлёт интенты. Состояние формы живёт в RootStore.
 */
@Composable
fun AuthScreen(
    registerMode: Boolean,
    email: String,
    password: String,
    displayName: String,
    busy: Boolean,
    canSubmit: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onToggleMode: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.displaySmall)
            Text(
                stringResource(if (registerMode) Res.string.auth_create_account else Res.string.auth_welcome_back),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (registerMode) {
                ZonaTextField(displayName, onNameChange, stringResource(Res.string.auth_name))
            }
            ZonaTextField(email, onEmailChange, stringResource(Res.string.auth_email))
            ZonaTextField(password, onPasswordChange, stringResource(Res.string.auth_password), isPassword = true)

            ZonaPrimaryButton(
                text = if (busy) stringResource(Res.string.auth_please_wait)
                else stringResource(if (registerMode) Res.string.auth_register else Res.string.auth_login),
                enabled = canSubmit,
                onClick = onSubmit,
            )

            TextButton(onClick = onToggleMode) {
                Text(stringResource(if (registerMode) Res.string.auth_to_login else Res.string.auth_to_register))
            }
        }
    }
}
