package com.nickanderssohn.declarativejooq.codegen.ir

data class ForeignKeyIR(
    val fkName: String,                  // "fk_app_user_organization"
    val childTableName: String,          // "app_user"
    val childFieldExpression: String,    // "AppUserTable.APP_USER.ORGANIZATION_ID"
    val parentTableName: String,         // "organization"
    val parentBuilderClassName: String,  // "OrganizationBuilder"
    val builderFunctionName: String,     // "user" — derived from FK column name
    val isSelfReferential: Boolean = false
)
