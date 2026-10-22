package com.routemine.common.api

import com.routemine.common.error.ApiException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

data class ApiError(
    val timestamp: Instant,
    val status: Int,
    val code: String,
    val message: String,
    val path: String,
    val fieldErrors: Map<String, String> = emptyMap(),
)

@RestControllerAdvice
class ApiExceptionHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(exception: ApiException, request: HttpServletRequest): ResponseEntity<ApiError> =
        response(exception.status, exception.code, exception.message, request)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        exception: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiError> {
        val fields = exception.bindingResult.fieldErrors.associate { error ->
            error.field to (error.defaultMessage ?: "Invalid value")
        }
        return response(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_FAILED",
            "Request validation failed",
            request,
            fields,
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class, IllegalArgumentException::class, MethodArgumentTypeMismatchException::class)
    fun handleBadRequest(exception: Exception, request: HttpServletRequest): ResponseEntity<ApiError> =
        response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.message ?: "Invalid request", request)

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(exception: AccessDeniedException, request: HttpServletRequest): ResponseEntity<ApiError> =
        response(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access is denied", request)

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(exception: AuthenticationException, request: HttpServletRequest): ResponseEntity<ApiError> =
        response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication is required", request)

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleConflict(exception: DataIntegrityViolationException, request: HttpServletRequest): ResponseEntity<ApiError> {
        logger.warn("Database constraint rejected request", exception)
        return response(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "The resource was changed concurrently", request)
    }

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticConflict(
        exception: OptimisticLockingFailureException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiError> {
        logger.info("Concurrent update rejected: {}", exception.message)
        return response(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "The resource was changed concurrently", request)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception, request: HttpServletRequest): ResponseEntity<ApiError> {
        logger.error("Unhandled request failure", exception)
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", request)
    }

    private fun response(
        status: HttpStatus,
        code: String,
        message: String,
        request: HttpServletRequest,
        fieldErrors: Map<String, String> = emptyMap(),
    ) = ResponseEntity.status(status).body(
        ApiError(Instant.now(), status.value(), code, message, request.requestURI, fieldErrors),
    )
}
