package com.nickanderssohn.declarativejooq.codegen.emitter

import com.nickanderssohn.declarativejooq.codegen.ir.TableIR
import com.nickanderssohn.declarativejooq.codegen.scanner.NamingConventions
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
        val parentFkFieldsType = ClassName("kotlin.collections", "List")
            .parameterizedBy(tableFieldStarType)

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
                    "table = %T.%L, parentNode = null, parentFkFields = emptyList(), recordGraph = graph",
                    tableClass,
                    tableIR.tableConstantName
                )
            )
        } else {
            // Child, intermediate, or self-referential root: constructor takes recordGraph, parentNode?, parentFkFields
            classBuilder.primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(ParameterSpec.builder("recordGraph", recordGraphType).build())
                    .addParameter(ParameterSpec.builder("parentNode", recordNodeType.copy(nullable = true)).build())
                    .addParameter(
                        ParameterSpec.builder("parentFkFields", parentFkFieldsType)
                            .defaultValue("emptyList()")
                            .build()
                    )
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
                    "table = %T.%L, parentNode = parentNode, parentFkFields = parentFkFields, recordGraph = recordGraph, isSelfReferential = isSelfReferential",
                    tableClass,
                    tableIR.tableConstantName
                )
            )
        }

        // Every child-side FK column is filled by parent context or placeholders — exclude from raw properties / buildRecord()
        val fkClaimedPropertyNames = tableIR.outboundFKs.flatMap { fk ->
            fk.childFieldExpressions.mapNotNull { expr ->
                tableIR.columns.find { it.tableFieldRefExpression == expr }?.propertyName
            }
        }.toSet()

        // Mutable var properties for non-identity columns (skip columns that belong to any outbound FK)
        val nonIdentityColumns = tableIR.columns.filter { !it.isIdentity }
        val rawColumnProps = nonIdentityColumns.filter { it.propertyName !in fkClaimedPropertyNames }
        for (col in rawColumnProps) {
            classBuilder.addProperty(
                PropertySpec.builder(col.propertyName, col.kotlinTypeName.copy(nullable = true))
                    .mutable(true)
                    .initializer("null")
                    .build()
            )
        }

        // Placeholder-accepting properties for outbound FKs
        for (fk in tableIR.outboundFKs) {
            val parentResultClass = ClassName(outputPackage, fk.parentResultClassName)
            val pendingPlaceholderRefClass = ClassName("com.nickanderssohn.declarativejooq", "PendingPlaceholderRef")

            val setterBody = CodeBlock.builder()
                .addStatement("field = value")
                .beginControlFlow("if (value != null)")
                .addStatement(
                    "pendingPlaceholderRefs.add(%T(%L, value.record))",
                    pendingPlaceholderRefClass,
                    tableFieldListAsCodeBlock(fk.childFieldExpressions)
                )
                .endControlFlow()
                .build()

            classBuilder.addProperty(
                PropertySpec.builder(fk.placeholderPropertyName, parentResultClass.copy(nullable = true))
                    .mutable(true)
                    .initializer("null")
                    .setter(
                        FunSpec.setterBuilder()
                            .addParameter("value", parentResultClass.copy(nullable = true))
                            .addCode(setterBody)
                            .build()
                    )
                    .build()
            )
        }

        // Override buildRecord()
        val buildRecordBody = CodeBlock.builder()
        buildRecordBody.addStatement("val record = %T()", recordType)
        // Skip columns claimed by placeholder properties — their FK values are resolved by TopologicalInserter
        for (col in rawColumnProps) {
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

        // childBlocks property — lambda receives parentNode, returns Unit
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

        // Child builder functions for each inbound FK
        // Multi-FK groups (sharing same builderFunctionName) produce one function with a required TableField parameter.
        // Single-FK cases produce one function without a TableField parameter.
        if (hasChildren) {
            // Group inbound FKs by builderFunctionName
            val fkGroups = tableIR.inboundFKs.groupBy { it.builderFunctionName }
            val graphVar = if (tableIR.isRoot && !hasSelfRefInbound) "graph" else "recordGraph"

            for ((_, fkGroup) in fkGroups) {
                val fk = fkGroup.first() // Use first FK for type/class info (all share same child table)
                val childBuilderClass = ClassName(outputPackage, toPascalCase(fk.childTableName) + "Builder")
                val childResultClass = ClassName(outputPackage, fk.childResultClassName)
                val childRecordClass = ClassName(fk.childSourcePackage, fk.childRecordClassName)
                val blockType = LambdaTypeName.get(
                    receiver = childBuilderClass,
                    returnType = UNIT
                )

                val isMultiFkGroup = fkGroup.size > 1 || fk.isMultiFk

                if (isMultiFkGroup) {
                    // Multi-FK: generate a single function with required TableField<ChildRecord, *> parameter.
                    // Each when-branch uses a distinct child column (see assignFkWhenDiscriminators) so composite FKs
                    // that share leading columns do not produce duplicate cases. Callers pass that column as fkField.
                    val tableFieldType = ClassName("org.jooq", "TableField")
                        .parameterizedBy(childRecordClass, com.squareup.kotlinpoet.STAR)

                    val childFunBody = CodeBlock.builder()
                    childFunBody.add("val parentFkFields = when (fkField) {\n")
                    for ((g, discExpr) in assignFkWhenDiscriminators(fkGroup)) {
                        childFunBody.add(
                            "  %L -> %L\n",
                            discExpr,
                            tableFieldListAsCodeBlock(g.childFieldExpressions)
                        )
                    }
                    childFunBody.add(
                        "  else -> throw IllegalArgumentException(%S + fkField)\n",
                        "Unknown FK field: "
                    )
                    childFunBody.add("}\n")
                    childFunBody.addStatement(
                        "val builder = %T(recordGraph = $graphVar, parentNode = null, parentFkFields = parentFkFields)",
                        childBuilderClass
                    )
                    childFunBody.addStatement("builder.block()")
                    childFunBody.addStatement("val placeholderRecord = builder.getOrBuildRecord()")
                    childFunBody.beginControlFlow("childBlocks.add { parentNode ->")
                    childFunBody.addStatement("builder.parentNode = parentNode")
                    childFunBody.addStatement("builder.buildWithChildren()")
                    childFunBody.endControlFlow()
                    childFunBody.addStatement("return %T(placeholderRecord)", childResultClass)

                    classBuilder.addFunction(
                        FunSpec.builder(fk.builderFunctionName)
                            .addParameter(ParameterSpec.builder("fkField", tableFieldType).build())
                            .addParameter("block", blockType)
                            .returns(childResultClass)
                            .addCode(childFunBody.build())
                            .build()
                    )
                } else {
                    // Single-FK: generate function without TableField parameter (unchanged behavior)
                    val childFunBody = CodeBlock.builder()
                    if (fk.isSelfReferential) {
                        childFunBody.addStatement(
                            "val builder = %T(recordGraph = $graphVar, parentNode = null, parentFkFields = %L, isSelfReferential = true)",
                            childBuilderClass,
                            tableFieldListAsCodeBlock(fk.childFieldExpressions)
                        )
                    } else {
                        childFunBody.addStatement(
                            "val builder = %T(recordGraph = $graphVar, parentNode = null, parentFkFields = %L)",
                            childBuilderClass,
                            tableFieldListAsCodeBlock(fk.childFieldExpressions)
                        )
                    }
                    childFunBody.addStatement("builder.block()")
                    childFunBody.addStatement("val placeholderRecord = builder.getOrBuildRecord()")
                    childFunBody.beginControlFlow("childBlocks.add { parentNode ->")
                    childFunBody.addStatement("builder.parentNode = parentNode")
                    childFunBody.addStatement("builder.buildWithChildren()")
                    childFunBody.endControlFlow()
                    childFunBody.addStatement("return %T(placeholderRecord)", childResultClass)

                    classBuilder.addFunction(
                        FunSpec.builder(fk.builderFunctionName)
                            .addParameter("block", blockType)
                            .returns(childResultClass)
                            .addCode(childFunBody.build())
                            .build()
                    )
                }
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

    private fun tableFieldListAsCodeBlock(fieldExpressions: List<String>): CodeBlock {
        require(fieldExpressions.isNotEmpty()) { "FK must declare at least one child field" }
        val tf = ClassName("org.jooq", "TableField")
        val b = CodeBlock.builder()
        b.add("listOf(")
        fieldExpressions.forEachIndexed { i, expr ->
            if (i > 0) b.add(", ")
            b.add("%L as %T<*, *>", expr, tf)
        }
        b.add(")")
        return b.build()
    }

    private fun toPascalCase(input: String): String = NamingConventions.toPascalCase(input)
}
