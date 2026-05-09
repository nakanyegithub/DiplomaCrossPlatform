package ru.diploma.crossplatform.zona

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

actual fun zonaHttpClientEngine(): HttpClientEngine = CIO.create()
