package com.nickanderssohn.declarativejooq.codegen.ir

/**
 * Intermediate representation of a foreign key relationship (single-column or composite).
 * Captures both the schema metadata (table/column names) and the derived naming used in
 * generated code (builder function names, placeholder property names).
 *
 * [childFieldExpressions] and [parentFieldExpressions] are positionally matched:
 * childFieldExpressions[i] references parentFieldExpressions[i].
 */
data class ForeignKeyIR(
    val fkName: String,                      // "fk_user_organization"
    val childTableName: String,              // "user"
    val childFieldExpressions: List<String>, // ["UserTable.USER.ORGANIZATION_ID"]
    val parentTableName: String,             // "organization"
    val parentFieldExpressions: List<String>,// ["OrganizationTable.ORGANIZATION.ID"]
    val parentBuilderClassName: String,      // "OrganizationBuilder"
    val parentResultClassName: String,       // "OrganizationResult"
    val builderFunctionName: String,         // "user" — derived from child table name
    val placeholderPropertyName: String,     // "organization", "createdBy", "parent"
    val childResultClassName: String,        // "UserResult"
    val childRecordClassName: String,        // "UserRecord"
    val childSourcePackage: String,          // "com.nickanderssohn.declarativejooq"
    val childRecordSourcePackage: String = childSourcePackage,
    val isSelfReferential: Boolean = false,
    val isMultiFk: Boolean = false,          // true when multiple FKs from same child table point to same parent
    val fkColumnNames: List<String> = emptyList() // raw FK column names for multi-FK disambiguation
) {
    val isComposite: Boolean get() = childFieldExpressions.size > 1
}
