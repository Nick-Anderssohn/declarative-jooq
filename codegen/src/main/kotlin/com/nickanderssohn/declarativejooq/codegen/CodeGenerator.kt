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

        val result = mutableListOf<Pair<String, String>>()
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
                fileSpec.addImport("org.jooq", "TableField")
            }
            val built = fileSpec.build()
            result.add(Pair("${table.builderClassName}.kt", built.toString()))
        }

        // GeneratedDslResult file
        val dslResultSpec = FileSpec.builder(outputPackage, "GeneratedDslResult")
        dslResultSpec.addType(dslResultEmitter.emit(tables, outputPackage))
        val dslResultBuilt = dslResultSpec.build()
        result.add(Pair("GeneratedDslResult.kt", dslResultBuilt.toString()))

        return result
    }

    /**
     * Adds explicit imports for any child table classes referenced in FK expressions.
     * When a root/intermediate builder has inbound FKs, it references child table classes
     * (e.g., AppUserTable.APP_USER.ORGANIZATION_ID) via raw string expressions in addStatement().
     * KotlinPoet does not automatically import classes used in raw code strings, so we add
     * them explicitly here.
     */
    private fun addFkChildTableImports(fileSpec: FileSpec.Builder, table: TableIR, tableByName: Map<String, TableIR>) {
        for (fk in table.inboundFKs) {
            val childTable = tableByName[fk.childTableName] ?: continue
            fileSpec.addImport(childTable.sourcePackage, childTable.tableClassName)
        }
    }

    private fun scanAndExtract(classDir: File, packageFilter: String?) =
        ClasspathScanner()
            .findTableClassNames(classDir, packageFilter)
            .let { tableNames -> MetadataExtractor().extract(classDir, tableNames) }
}
