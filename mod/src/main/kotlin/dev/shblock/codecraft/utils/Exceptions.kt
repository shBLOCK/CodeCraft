package dev.shblock.codecraft.utils

//interface NotUnexpected

/**
 * Represents an internal (unexpected) exception.
 */
class InternalException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Represents an (expected) exception that is likely the result of a user error.
 */
class UserSourcedException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)