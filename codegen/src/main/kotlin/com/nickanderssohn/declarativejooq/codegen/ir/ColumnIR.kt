package com.nickanderssohn.declarativejooq.codegen.ir

import com.squareup.kotlinpoet.TypeName

data class ColumnIR(
    val columnName: String,           // "organization_id"
    val propertyName: String,         // "organizationId"
    val kotlinTypeName: TypeName,     // KotlinPoet TypeName (e.g., LONG, STRING)
    val isIdentity: Boolean,
    val isNullable: Boolean,
    val tableFieldRefExpression: String // "AppUserTable.APP_USER.ORGANIZATION_ID"
)
