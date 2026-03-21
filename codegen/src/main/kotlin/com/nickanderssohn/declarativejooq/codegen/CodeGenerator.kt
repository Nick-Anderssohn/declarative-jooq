package com.nickanderssohn.declarativejooq.codegen

import com.nickanderssohn.declarativejooq.codegen.emitter.BuilderEmitter
import com.nickanderssohn.declarativejooq.codegen.emitter.DslResultEmitter
import com.nickanderssohn.declarativejooq.codegen.emitter.DslScopeEmitter
import com.nickanderssohn.declarativejooq.codegen.emitter.ResultEmitter
import com.nickanderssohn.declarativejooq.codegen.ir.TableIR
import com.nickanderssohn.declarativejooq.codegen.scanner.ClasspathScanner
import com.nickanderssohn.declarativejooq.codegen.scanner.MetadataExtractor
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import java.io.File

/**
 * Top-level orchestrator for code generation. Scans compiled jOOQ table classes from a
 * class directory, extracts schema metadata into [TableIR], and emits typed Kotlin DSL
 * source files (builders, result classes, DslScope extensions, and GeneratedDslResult).
 */
class CodeGenerator {

    /**
     * Generates Kotlin source files from jOOQ table classes in [classDir] and writes them to [outputDir].
     * One file per table (containing builder class, result class, optional DslScope extension function).
     * One additional file for GeneratedDslResult.
     */
    fun generate(
        classDir: File,
        outputDir: File,
        outputPackage: String,
        packageFilter: String? = null
    ) {
        val tables = scanAndExtract(classDir, packageFilter)

        val builderEmitter = BuilderEmitter()
        val resultEmitter = ResultEmitter()
        val dslScopeEmitter = DslScopeEmitter()
        val dslResultEmitter = DslResultEmitter()

        val tableByName = tables.associateBy { it.tableName }
        for (table in tables) {
            val fileSpec = FileSpec.builder(outputPackage, table.builderClassName)
            fileSpec.addType(builderEmitter.emit(table, outputPackage))
            fileSpec.addType(resultEmitter.emit(table, outputPackage))
            if (table.isRoot) {
                fileSpec.addFunction(dslScopeEmitter.emit(table, outputPackage))
            }
            addFkChildTableImports(fileSpec, table, tableByName)
            if (table.outboundFKs.isNotEmpty()) {
                fileSpec.addImport("com.nickanderssohn.declarativejooq", "PendingPlaceholderRef")
            }
            if (table.outboundFKs.isNotEmpty() || table.inboundFKs.isNotEmpty()) {
                fileSpec.addImport("org.jooq", "TableField")
            }
            fileSpec.build().writeTo(outputDir)
        }

        // GeneratedDslResult file
        val dslResultSpec = FileSpec.builder(outputPackage, "GeneratedDslResult")
        dslResultSpec.addType(dslResultEmitter.emit(tables, outputPackage))
        dslResultSpec.build().writeTo(outputDir)
    }

    /**
     * Returns list of (filename, sourceCode) pairs for each generated file — useful for testing
     * with kotlin-compile-testing without writing to disk.
     */
    fun generateSource(
        classDir: File,
        outputPackage: String,
        packageFilter: String? = null
    ): List<Pair<String, String>> {
        val tables = scanAndExtract(classDir, packageFilter)

        val builderEmitter = BuilderEmitter()
        val resultEmitter = ResultEmitter()
        val dslScopeEmitter = DslScopeEmitter()
        val dslResultEmitter = DslResultEmitter()

        val tableByName = tables.associateBy { it.tableName }

        val tableFiles = tables.map { table ->
            val fileSpec = FileSpec.builder(outputPackage, table.builderClassName)
            fileSpec.addType(builderEmitter.emit(table, outputPackage))
            fileSpec.addType(resultEmitter.emit(table, outputPackage))
            if (table.isRoot) {
                fileSpec.addFunction(dslScopeEmitter.emit(table, outputPackage))
            }
            addFkChildTableImports(fileSpec, table, tableByName)
            if (table.outboundFKs.isNotEmpty()) {
                fileSpec.addImport("com.nickanderssohn.declarativejooq", "PendingPlaceholderRef")
            }
            if (table.outboundFKs.isNotEmpty() || table.inboundFKs.isNotEmpty()) {
                fileSpec.addImport("org.jooq", "TableField")
            }
            val built = fileSpec.build()
            "${table.builderClassName}.kt" to built.toString()
        }

        val dslResultFile = FileSpec.builder(outputPackage, "GeneratedDslResult")
            .apply { addType(dslResultEmitter.emit(tables, outputPackage)) }
            .build()
            .let { "GeneratedDslResult.kt" to it.toString() }

        return tableFiles + dslResultFile
    }

    /**
     * Adds explicit imports for table classes referenced in raw FK field expressions.
     * KotlinPoet does not automatically import classes used in raw code strings, so we add
     * them explicitly here.
     *
     * - Inbound FKs: child table classes (e.g., UserTable) referenced in childFieldExpressions
     *   and parent table classes referenced in parentFieldExpressions (for multi-FK when blocks).
     * - Outbound FKs: parent table classes referenced in parentFieldExpressions (for placeholder setters).
     */
    private fun addFkChildTableImports(fileSpec: FileSpec.Builder, table: TableIR, tableByName: Map<String, TableIR>) {
        for (fk in table.inboundFKs) {
            val childTable = tableByName[fk.childTableName] ?: continue
            fileSpec.addImport(childTable.sourcePackage, childTable.tableClassName)
            // Parent table class is also needed in the generated multi-FK when block
            // for parentRefFields expressions (e.g., OrganizationTable.ORGANIZATION.ID)
            val parentTable = tableByName[fk.parentTableName] ?: continue
            if (parentTable.sourcePackage != table.sourcePackage || parentTable.tableClassName != table.tableClassName) {
                fileSpec.addImport(parentTable.sourcePackage, parentTable.tableClassName)
            }
        }
        for (fk in table.outboundFKs) {
            val parentTable = tableByName[fk.parentTableName] ?: continue
            fileSpec.addImport(parentTable.sourcePackage, parentTable.tableClassName)
        }
    }

    private fun scanAndExtract(classDir: File, packageFilter: String?) =
        ClasspathScanner()
            .findTableClassNames(classDir, packageFilter)
            .let { tableNames -> MetadataExtractor().extract(classDir, tableNames) }
}
