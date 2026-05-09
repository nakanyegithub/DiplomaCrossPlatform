package ru.diploma.crossplatform.zona

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

actual fun zonaHttpClientEngine(): HttpClientEngine = OkHttp.create()
