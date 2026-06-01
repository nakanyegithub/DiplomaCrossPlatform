package ru.zona.app.core.network

/** Базовый адрес API. Платформенная реализация: эмулятор Android = 10.0.2.2, прочее = localhost. */
expect fun defaultApiBaseUrl(): String
