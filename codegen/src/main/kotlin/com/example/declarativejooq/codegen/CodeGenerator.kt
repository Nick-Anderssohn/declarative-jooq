package com.example.declarativejooq.codegen

import com.example.declarativejooq.codegen.emitter.BuilderEmitter
import com.example.declarativejooq.codegen.emitter.DslResultEmitter
import com.example.declarativejooq.codegen.emitter.DslScopeEmitter
import com.example.declarativejooq.codegen.emitter.ResultEmitter
import com.example.declarativejooq.codegen.scanner.ClasspathScanner
import com.example.declarativejooq.codegen.scanner.MetadataExtractor
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

        for (table in tables) {
            val fileSpec = FileSpec.builder(outputPackage, table.builderClassName)
            fileSpec.addType(builderEmitter.emit(table, outputPackage))
            fileSpec.addType(resultEmitter.emit(table, outputPackage))
            if (table.isRoot) {
                fileSpec.addFunction(dslScopeEmitter.emit(table, outputPackage))
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

        for (table in tables) {
            val fileSpec = FileSpec.builder(outputPackage, table.builderClassName)
            fileSpec.addType(builderEmitter.emit(table, outputPackage))
            fileSpec.addType(resultEmitter.emit(table, outputPackage))
            if (table.isRoot) {
                fileSpec.addFunction(dslScopeEmitter.emit(table, outputPackage))
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

    private fun scanAndExtract(classDir: File, packageFilter: String?) =
        ClasspathScanner()
            .findTableClassNames(classDir, packageFilter)
            .let { tableNames -> MetadataExtractor().extract(classDir, tableNames) }
}
