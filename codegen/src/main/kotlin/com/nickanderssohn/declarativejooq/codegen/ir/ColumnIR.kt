package com.nickanderssohn.declarativejooq.codegen.ir

import com.squareup.kotlinpoet.TypeName

/**
 * Intermediate representation of a single table column, including its Kotlin type mapping
 * and the jOOQ field reference expression used in generated code.
 */
data class ColumnIR(
    val columnName: String,           // "organization_id"
    val propertyName: String,         // "organizationId"
    val kotlinTypeName: TypeName,     // KotlinPoet TypeName (e.g., LONG, STRING)
    val isIdentity: Boolean,
    val isNullable: Boolean,
    val tableFieldRefExpression: String // "UserTable.USER.ORGANIZATION_ID"
)
