package com.nickanderssohn.declarativejooq.codegen.ir

/**
 * Intermediate representation of a single-column foreign key relationship. Captures both
 * the schema metadata (table/column names) and the derived naming used in generated code
 * (builder function names, placeholder property names).
 */
data class ForeignKeyIR(
    val fkName: String,                      // "fk_user_organization"
    val childTableName: String,              // "user"
    val childFieldExpression: String,        // "UserTable.USER.ORGANIZATION_ID"
    val parentTableName: String,             // "organization"
    val parentBuilderClassName: String,      // "OrganizationBuilder"
    val parentResultClassName: String,       // "OrganizationResult"
    val builderFunctionName: String,         // "user" — derived from child table name
    val placeholderPropertyName: String,     // "organization", "createdBy", "parent"
    val childResultClassName: String,        // "UserResult"
    val childRecordClassName: String,        // "UserRecord"
    val childSourcePackage: String,          // "com.nickanderssohn.declarativejooq"
    val childRecordSourcePackage: String = childSourcePackage,
    val isSelfReferential: Boolean = false,
    val isMultiFk: Boolean = false           // true when multiple FKs from same child table point to same parent
)
