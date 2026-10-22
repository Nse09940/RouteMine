package com.routemine.deliveries.application

import com.routemine.common.error.ApiException
import org.springframework.http.HttpStatus

class ConfirmationCodeException(blocked: Boolean) : ApiException(
    if (blocked) HttpStatus.TOO_MANY_REQUESTS else HttpStatus.CONFLICT,
    if (blocked) "CONFIRMATION_CODE_LOCKED" else "INVALID_CONFIRMATION_CODE",
    if (blocked) "Confirmation attempts are temporarily locked" else "Confirmation code is invalid",
)
