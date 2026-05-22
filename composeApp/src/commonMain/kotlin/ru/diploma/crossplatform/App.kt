package ru.diploma.crossplatform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ru.diploma.crossplatform.zona.ZonaApi
import ru.diploma.crossplatform.zona.TokenStorage
import ru.diploma.crossplatform.zona.ZonaApp
import ru.diploma.crossplatform.zona.createZonaHttpClient
import ru.diploma.crossplatform.zona.domain.usecase.ZonaUseCases
import ru.diploma.crossplatform.zona.presentation.di.ZonaDependencies
import ru.diploma.crossplatform.zona.ui.theme.ZonaTheme

/**
 * @param useCases на Android передаётся из Hilt ([MainActivity]);
 * на Desktop — null, тогда собирается [ZonaDependencies] вручную.
 */
@Composable
fun App(useCases: ZonaUseCases? = null) {
    val resolved =
        useCases ?: run {
            val api = remember { ZonaApi(createZonaHttpClient()) }
            val tokenStore = remember { TokenStorage() }
            remember(api, tokenStore) { ZonaDependencies(api, tokenStore).useCases }
        }
    ZonaTheme {
        ZonaApp(resolved)
    }
}
