package com.nickanderssohn.declarativejooq

/**
 * DSL marker annotation that prevents implicit access to outer builder scopes,
 * ensuring each builder block only sees properties from its own table.
 */
@DslMarker
@Target(AnnotationTarget.CLASS)
annotation class DeclarativeJooqDsl
