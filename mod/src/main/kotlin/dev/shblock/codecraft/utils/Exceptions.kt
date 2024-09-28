package dev.shblock.codecraft.utils


/**
 * Represents an (expected) exception that is likely the result of a user error.
 */
class UserSourcedException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)