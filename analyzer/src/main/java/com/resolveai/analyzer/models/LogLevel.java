package com.resolveai.analyzer.models;

/**
 * Enum class for LogLevel. The levels include: DEBUG, INFO, WARN, ERROR, FATAL.
 * The order of severity: FATAL > ERROR > WARN > [DEBUG, INFO]
 */
public enum LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL
}
