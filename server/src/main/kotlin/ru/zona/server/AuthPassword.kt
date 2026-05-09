package ru.zona.server

import at.favre.lib.crypto.bcrypt.BCrypt

fun hashPassword(plain: String): String =
    BCrypt.withDefaults().hashToString(12, plain.toCharArray())

fun verifyPassword(
    plain: String,
    hash: String,
): Boolean = BCrypt.verifyer().verify(plain.toCharArray(), hash).verified
