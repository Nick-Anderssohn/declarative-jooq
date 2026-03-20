package com.nickanderssohn.declarativejooq.codegen.ir

/**
 * Intermediate representation of a single database table, produced by [MetadataExtractor][com.nickanderssohn.declarativejooq.codegen.scanner.MetadataExtractor]
 * and consumed by the emitters to generate builder, result, and scope classes.
 */
data class TableIR(
    val tableName: String,               // "organization"
    val tableClassName: String,          // "OrganizationTable"
    val tableConstantName: String,       // "ORGANIZATION"
    val recordClassName: String,         // "OrganizationRecord"
    val builderClassName: String,        // "OrganizationBuilder"
    val resultClassName: String,         // "OrganizationResult"
    val dslFunctionName: String,         // "organization"
    val sourcePackage: String,           // "com.nickanderssohn.declarativejooq"
    val recordSourcePackage: String = sourcePackage,
    val columns: List<ColumnIR>,
    val outboundFKs: List<ForeignKeyIR>,
    val inboundFKs: MutableList<ForeignKeyIR> = mutableListOf(),
    val isRoot: Boolean                  // true if no non-self-referential outbound FKs
)
