package com.example.declarativejooq.codegen.ir

data class TableIR(
    val tableName: String,               // "organization"
    val tableClassName: String,          // "OrganizationTable"
    val tableConstantName: String,       // "ORGANIZATION"
    val recordClassName: String,         // "OrganizationRecord"
    val builderClassName: String,        // "OrganizationBuilder"
    val resultClassName: String,         // "OrganizationResult"
    val dslFunctionName: String,         // "organization"
    val sourcePackage: String,           // "com.example.declarativejooq"
    val columns: List<ColumnIR>,
    val outboundFKs: List<ForeignKeyIR>,
    val inboundFKs: MutableList<ForeignKeyIR> = mutableListOf(),
    val isRoot: Boolean                  // true if outboundFKs is empty
)
