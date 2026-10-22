package com.routemine.common.error

import org.springframework.http.HttpStatus

open class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
) : RuntimeException(message)

class NotFoundException(code: String, message: String) :
    ApiException(HttpStatus.NOT_FOUND, code, message)

class ConflictException(code: String, message: String) :
    ApiException(HttpStatus.CONFLICT, code, message)

class BusinessRuleException(code: String, message: String) :
    ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message)

class ForbiddenException(code: String, message: String) :
    ApiException(HttpStatus.FORBIDDEN, code, message)

class UnauthorizedException(code: String, message: String) :
    ApiException(HttpStatus.UNAUTHORIZED, code, message)

