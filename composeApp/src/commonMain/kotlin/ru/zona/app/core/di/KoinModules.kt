package ru.zona.app.core.di

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * Koin-модуль приложения. Граф зависимостей (сеть, репозитории) собирается в [AppGraph]
 * и регистрируется как singleton — экраны/сторы получают репозитории через него.
 */
val appModule = module {
    single { AppGraph() }
}

/** Инициализация Koin из любой платформенной точки входа. */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    startKoin {
        appDeclaration()
        modules(appModule)
    }
}
