package com.moneymanager.domain.model

/**
 * Result of a repository operation. Errors cross the domain boundary as data,
 * not thrown exceptions, so the UI never has to catch a Room/SQL exception.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>

    data class Failure(val error: AppError) : AppResult<Nothing>
}

/**
 * Domain-level error kinds. Mirrors the backend's HTTP semantics without any
 * HTTP coupling: [NotFound] ~ 404, [Conflict] ~ 409, [Validation] ~ 422.
 */
sealed interface AppError {
    data object NotFound : AppError

    data class Conflict(val message: String) : AppError

    data class Validation(val message: String) : AppError

    data class Unknown(val message: String) : AppError
}

/** Convenience for the common success case. */
fun <T> T.asSuccess(): AppResult<T> = AppResult.Success(this)

/** Convenience for the common failure case. */
fun fail(error: AppError): AppResult<Nothing> = AppResult.Failure(error)
