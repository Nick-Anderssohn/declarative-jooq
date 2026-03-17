package com.nickanderssohn.declarativejooq.codegen.emitter

import com.nickanderssohn.declarativejooq.codegen.ir.TableIR
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.asTypeName

class BuilderEmitter {

    fun emit(tableIR: TableIR, outputPackage: String): TypeSpec {
        val recordType = ClassName(tableIR.sourcePackage, tableIR.recordClassName)
        val tableClass = ClassName(tableIR.sourcePackage, tableIR.tableClassName)
        val recordBuilderType = ClassName("com.nickanderssohn.declarativejooq", "RecordBuilder")
            .parameterizedBy(recordType)
        val recordGraphType = ClassName("com.nickanderssohn.declarativejooq", "RecordGraph")
        val recordNodeType = ClassName("com.nickanderssohn.declarativejooq", "RecordNode")

        val hasChildren = tableIR.inboundFKs.isNotEmpty()
        val hasSelfRefInbound = tableIR.inboundFKs.any { it.isSelfReferential }

        val tableFieldStarType = ClassName("org.jooq", "TableField")
            .parameterizedBy(
                com.squareup.kotlinpoet.STAR,
                com.squareup.kotlinpoet.STAR
            )

        val classBuilder = TypeSpec.classBuilder(tableIR.builderClassName)

        // Constructor parameters and superclass call
        // Self-referential root tables use child-style constructor so they can serve as children too.
        // Regular root tables use graph-only constructor.
        if (tableIR.isRoot && !hasSelfRefInbound) {
            // Root table: constructor takes graph: RecordGraph (private val)
            classBuilder.primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(
                        ParameterSpec.builder("graph", recordGraphType)
                            .build()
                    )
                    .build()
            )
            classBuilder.addProperty(
                PropertySpec.builder("graph", recordGraphType, KModifier.PRIVATE)
                    .initializer("graph")
                    .build()
            )
            classBuilder.superclass(recordBuilderType)
            classBuilder.addSuperclassConstructorParameter(
                CodeBlock.of(
                    "table = %T.%L, parentNode = null, parentFkField = null, recordGraph = graph",
                    tableClass,
                    tableIR.tableConstantName
                )
            )
        } else {
            // Child, intermediate, or self-referential root: constructor takes recordGraph, parentNode?, parentFkField?
            classBuilder.primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(ParameterSpec.builder("recordGraph", recordGraphType).build())
                    .addParameter(ParameterSpec.builder("parentNode", recordNodeType.copy(nullable = true)).build())
                    .addParameter(ParameterSpec.builder("parentFkField", tableFieldStarType.copy(nullable = true)).build())
                    .addParameter(
                        ParameterSpec.builder("isSelfReferential", BOOLEAN)
                            .defaultValue("false")
                            .build()
                    )
                    .build()
            )
            classBuilder.superclass(recordBuilderType)
            classBuilder.addSuperclassConstructorParameter(
                CodeBlock.of(
                    "table = %T.%L, parentNode = parentNode, parentFkField = parentFkField, recordGraph = recordGraph, isSelfReferential = isSelfReferential",
                    tableClass,
                    tableIR.tableConstantName
                )
            )
        }

        // Mutable var properties for non-identity columns
        val nonIdentityColumns = tableIR.columns.filter { !it.isIdentity }
        for (col in nonIdentityColumns) {
            classBuilder.addProperty(
                PropertySpec.builder(col.propertyName, col.kotlinTypeName.copy(nullable = true))
                    .mutable(true)
                    .initializer("null")
                    .build()
            )
        }

        // Override buildRecord()
        val buildRecordBody = CodeBlock.builder()
        buildRecordBody.addStatement("val record = %T()", recordType)
        for (col in nonIdentityColumns) {
            buildRecordBody.addStatement(
                "record.set(%L, %L)",
                col.tableFieldRefExpression,
                col.propertyName
            )
        }
        buildRecordBody.addStatement("return record")

        classBuilder.addFunction(
            FunSpec.builder("buildRecord")
                .addModifiers(KModifier.OVERRIDE)
                .returns(recordType)
                .addCode(buildRecordBody.build())
                .build()
        )

        // childBlocks property (always present so buildWithChildren() can reference it)
        val lambdaType = LambdaTypeName.get(
            parameters = arrayOf(recordNodeType),
            returnType = UNIT
        )
        val mutableListType = ClassName("kotlin.collections", "MutableList")
            .parameterizedBy(lambdaType)
        classBuilder.addProperty(
            PropertySpec.builder("childBlocks", mutableListType, KModifier.PRIVATE)
                .initializer("mutableListOf()")
                .build()
        )

        // Child builder functions for each inbound FK (only if this table has children)
        if (hasChildren) {
            for (fk in tableIR.inboundFKs) {
                val childBuilderClass = ClassName(outputPackage, toPascalCase(fk.childTableName) + "Builder")
                val blockType = LambdaTypeName.get(
                    receiver = childBuilderClass,
                    returnType = UNIT
                )
                val childFunBody = CodeBlock.builder()
                childFunBody.beginControlFlow("childBlocks.add { parentNode ->")
                // Determine which graph variable to use:
                // - Regular root tables use "graph" (private val)
                // - Self-ref root tables and child tables use "recordGraph" (constructor param)
                val graphVar = if (tableIR.isRoot && !hasSelfRefInbound) "graph" else "recordGraph"
                if (fk.isSelfReferential) {
                    childFunBody.addStatement(
                        "val builder = %T(recordGraph = $graphVar, parentNode = parentNode, parentFkField = %L, isSelfReferential = true)",
                        childBuilderClass,
                        fk.childFieldExpression
                    )
                } else {
                    childFunBody.addStatement(
                        "val builder = %T(recordGraph = $graphVar, parentNode = parentNode, parentFkField = %L)",
                        childBuilderClass,
                        fk.childFieldExpression
                    )
                }
                childFunBody.addStatement("builder.block()")
                // Always call buildWithChildren() so any nested children are processed
                childFunBody.addStatement("builder.buildWithChildren()")
                childFunBody.endControlFlow()

                classBuilder.addFunction(
                    FunSpec.builder(fk.builderFunctionName)
                        .addParameter("block", blockType)
                        .addCode(childFunBody.build())
                        .build()
                )
            }

        }

        // buildWithChildren() — always present on all builders
        classBuilder.addFunction(
            FunSpec.builder("buildWithChildren")
                .returns(recordNodeType)
                .addStatement("val node = build()")
                .addStatement("childBlocks.forEach { it(node) }")
                .addStatement("return node")
                .build()
        )

        return classBuilder.build()
    }

    private fun toPascalCase(input: String): String {
        return input.split("_").joinToString("") { it.replaceFirstChar { c -> c.uppercaseChar() } }
    }
}
