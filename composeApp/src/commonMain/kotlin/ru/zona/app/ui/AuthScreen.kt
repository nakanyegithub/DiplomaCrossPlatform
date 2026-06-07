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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.zona.app.core.design.ZonaPrimaryButton
import ru.zona.app.core.design.ZonaTextField
import zona.resources.Res
import zona.resources.app_name
import zona.resources.auth_email
import zona.resources.auth_login
import zona.resources.auth_name
import zona.resources.auth_password
import zona.resources.auth_register
import zona.resources.auth_to_login
import zona.resources.auth_to_register

@Composable
fun AuthScreen(
    busy: Boolean,
    onLogin: (email: String, password: String) -> Unit,
    onRegister: (email: String, password: String, displayName: String) -> Unit,
) {
    var registerMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    val canSubmit =
        email.isNotBlank() && password.isNotBlank() && (!registerMode || name.isNotBlank()) && !busy

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
                if (registerMode) "Создайте аккаунт" else "С возвращением",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (registerMode) {
                ZonaTextField(name, { name = it }, stringResource(Res.string.auth_name))
            }
            ZonaTextField(email, { email = it }, stringResource(Res.string.auth_email))
            ZonaTextField(password, { password = it }, stringResource(Res.string.auth_password), isPassword = true)

            ZonaPrimaryButton(
                text = if (busy) "Подождите…" else stringResource(if (registerMode) Res.string.auth_register else Res.string.auth_login),
                enabled = canSubmit,
            ) {
                if (registerMode) onRegister(email, password, name) else onLogin(email, password)
            }

            TextButton(onClick = { registerMode = !registerMode }) {
                Text(stringResource(if (registerMode) Res.string.auth_to_login else Res.string.auth_to_register))
            }
        }
    }
}
