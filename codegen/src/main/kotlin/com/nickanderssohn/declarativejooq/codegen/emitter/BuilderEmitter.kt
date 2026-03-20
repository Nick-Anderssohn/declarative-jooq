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

/**
 * Emits a per-table builder class (e.g., `OrganizationBuilder`) from a [TableIR].
 * The builder extends [RecordBuilder][com.nickanderssohn.declarativejooq.RecordBuilder],
 * exposes mutable properties for each column, placeholder properties for outbound FKs,
 * and child builder functions for each inbound FK relationship.
 */
class BuilderEmitter {

    fun emit(tableIR: TableIR, outputPackage: String): TypeSpec {
        val recordType = ClassName(tableIR.recordSourcePackage, tableIR.recordClassName)
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

        // Collect placeholder property names — these claim the column property slot for outbound FKs
        // where the placeholder name equals the column name (e.g., created_by -> createdBy).
        // Such columns are excluded from the raw column property list and buildRecord() set calls.
        val placeholderClaimedNames = tableIR.outboundFKs
            .map { it.placeholderPropertyName }
            .toSet()

        // Mutable var properties for non-identity columns (skip any claimed by a placeholder property)
        val nonIdentityColumns = tableIR.columns.filter { !it.isIdentity }
        val rawColumnProps = nonIdentityColumns.filter { it.propertyName !in placeholderClaimedNames }
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
            val tableFieldRawType = ClassName("org.jooq", "TableField")

            val setterBody = CodeBlock.builder()
                .addStatement("field = value")
                .beginControlFlow("if (value != null)")
                .addStatement(
                    "pendingPlaceholderRefs.add(%T(%L as %T<*, *>, value.record))",
                    pendingPlaceholderRefClass,
                    fk.childFieldExpression,
                    tableFieldRawType
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
        // Only set columns that were explicitly assigned (non-null). This avoids overriding
        // database DEFAULT values (e.g., created_at TIMESTAMP DEFAULT NOW()) with null.
        for (col in rawColumnProps) {
            buildRecordBody.addStatement(
                "%L?.let { record.set(%L, it) }",
                col.propertyName,
                col.tableFieldRefExpression
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
                val childRecordClass = ClassName(fk.childRecordSourcePackage, fk.childRecordClassName)
                val blockType = LambdaTypeName.get(
                    receiver = childBuilderClass,
                    returnType = UNIT
                )

                val isMultiFkGroup = fkGroup.size > 1 || fk.isMultiFk

                if (isMultiFkGroup) {
                    // Multi-FK: generate a single function with required TableField<ChildRecord, *> parameter
                    // The caller passes the specific FK field (e.g., TaskTable.TASK.CREATED_BY) to disambiguate
                    val tableFieldType = ClassName("org.jooq", "TableField")
                        .parameterizedBy(childRecordClass, com.squareup.kotlinpoet.STAR)

                    val childFunBody = CodeBlock.builder()
                    childFunBody.addStatement(
                        "val builder = %T(recordGraph = $graphVar, parentNode = null, parentFkField = fkField)",
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
                            "val builder = %T(recordGraph = $graphVar, parentNode = null, parentFkField = %L, isSelfReferential = true)",
                            childBuilderClass,
                            fk.childFieldExpression
                        )
                    } else {
                        childFunBody.addStatement(
                            "val builder = %T(recordGraph = $graphVar, parentNode = null, parentFkField = %L)",
                            childBuilderClass,
                            fk.childFieldExpression
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

    private fun toPascalCase(input: String): String = NamingConventions.toPascalCase(input)
}
