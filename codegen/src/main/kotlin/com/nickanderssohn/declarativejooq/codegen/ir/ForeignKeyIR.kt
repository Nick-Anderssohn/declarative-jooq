package com.nickanderssohn.declarativejooq.codegen.ir

data class ForeignKeyIR(
    val fkName: String,                      // "fk_user_organization"
    val childTableName: String,              // "user"
    val childFieldExpression: String,        // "UserTable.USER.ORGANIZATION_ID"
    val parentTableName: String,             // "organization"
    val parentBuilderClassName: String,      // "OrganizationBuilder"
    val parentResultClassName: String,       // "OrganizationResult"
    val builderFunctionName: String,         // "user" — derived from FK column name
    val placeholderPropertyName: String,     // "organization", "createdBy", "parent"
    val childResultClassName: String,        // "UserResult"
    val childRecordClassName: String,        // "UserRecord"
    val childSourcePackage: String,          // "com.nickanderssohn.declarativejooq"
    val isSelfReferential: Boolean = false,
    val isMultiFk: Boolean = false           // true when multiple FKs from same child table point to same parent
)
