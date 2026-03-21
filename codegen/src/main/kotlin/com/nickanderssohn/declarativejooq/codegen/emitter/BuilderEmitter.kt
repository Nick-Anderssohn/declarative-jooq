package com.nickanderssohn.declarativejooq.codegen.emitter

import com.nickanderssohn.declarativejooq.codegen.ir.ForeignKeyIR
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
 * The builder composes a [RecordBuilder][com.nickanderssohn.declarativejooq.RecordBuilder],
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
        val dslAnnotation = ClassName("com.nickanderssohn.declarativejooq", "DeclarativeJooqDsl")

        val hasChildren = tableIR.inboundFKs.isNotEmpty()

        val tableFieldStarType = ClassName("org.jooq", "TableField")
            .parameterizedBy(
                com.squareup.kotlinpoet.STAR,
                com.squareup.kotlinpoet.STAR
            )
        val listOfTableFieldStarType = ClassName("kotlin.collections", "List")
            .parameterizedBy(tableFieldStarType)

        val classBuilder = TypeSpec.classBuilder(tableIR.builderClassName)
        classBuilder.addAnnotation(dslAnnotation)

        // Unified constructor for all builders
        classBuilder.primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(ParameterSpec.builder("recordGraph", recordGraphType).build())
                .addParameter(ParameterSpec.builder("parentNode", recordNodeType.copy(nullable = true)).build())
                .addParameter(
                    ParameterSpec.builder("parentFkFields", listOfTableFieldStarType)
                        .defaultValue("emptyList()")
                        .build()
                )
                .addParameter(
                    ParameterSpec.builder("parentRefFields", listOfTableFieldStarType)
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

        // Collect placeholder property names — these claim the column property slot for outbound FKs
        // where the placeholder name equals the column name (e.g., created_by -> createdBy).
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

            val fkFieldsExpr = listOfFieldExprs(fk.childFieldExpressions)
            val refFieldsExpr = listOfFieldExprs(fk.parentFieldExpressions)

            val setterBody = CodeBlock.builder()
                .addStatement("field = value")
                .beginControlFlow("if (value != null)")
                .addStatement(
                    "recordBuilder.pendingPlaceholderRefs.add(%T($fkFieldsExpr, $refFieldsExpr, value.record))",
                    pendingPlaceholderRefClass
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

        // Composed RecordBuilder property with buildRecord lambda
        val recordBuilderInit = CodeBlock.builder()
        recordBuilderInit.add(
            "%T(\n⇥table = %T.%L,\nparentNode = parentNode,\nparentFkFields = parentFkFields,\nparentRefFields = parentRefFields,\nrecordGraph = recordGraph,\nisSelfReferential = isSelfReferential,\nbuildRecord = {\n⇥",
            recordBuilderType.rawType,
            tableClass,
            tableIR.tableConstantName
        )
        recordBuilderInit.addStatement("val record = %T()", recordType)
        for (col in rawColumnProps) {
            recordBuilderInit.addStatement(
                "%L?.let { record.set(%L, it) }",
                col.propertyName,
                col.tableFieldRefExpression
            )
        }
        recordBuilderInit.addStatement("record")
        recordBuilderInit.add("⇤}\n⇤)")

        classBuilder.addProperty(
            PropertySpec.builder("recordBuilder", recordBuilderType, KModifier.INTERNAL)
                .initializer(recordBuilderInit.build())
                .build()
        )

        // childBlocks property
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
        if (hasChildren) {
            val fkGroups = tableIR.inboundFKs.groupBy { it.builderFunctionName }

            for ((_, fkGroup) in fkGroups) {
                val fk = fkGroup.first()
                val childBuilderClass = ClassName(outputPackage, toPascalCase(fk.childTableName) + "Builder")
                val childResultClass = ClassName(outputPackage, fk.childResultClassName)
                val childRecordClass = ClassName(fk.childRecordSourcePackage, fk.childRecordClassName)
                val blockType = LambdaTypeName.get(
                    receiver = childBuilderClass,
                    returnType = UNIT
                )

                val isMultiFkGroup = fkGroup.size > 1 || fk.isMultiFk

                if (isMultiFkGroup) {
                    emitMultiFkChildFunction(
                        classBuilder, fkGroup, childBuilderClass, childResultClass,
                        childRecordClass, blockType, listOfTableFieldStarType
                    )
                } else {
                    emitSingleFkChildFunction(
                        classBuilder, fk, childBuilderClass, childResultClass,
                        blockType
                    )
                }
            }
        }

        // buildWithChildren()
        classBuilder.addFunction(
            FunSpec.builder("buildWithChildren")
                .returns(recordNodeType)
                .addStatement("val node = recordBuilder.build()")
                .addStatement("childBlocks.forEach { it(node) }")
                .addStatement("return node")
                .build()
        )

        return classBuilder.build()
    }

    private fun emitSingleFkChildFunction(
        classBuilder: TypeSpec.Builder,
        fk: ForeignKeyIR,
        childBuilderClass: ClassName,
        childResultClass: ClassName,
        blockType: LambdaTypeName
    ) {
        val fkFieldsExpr = listOfFieldExprs(fk.childFieldExpressions)
        val refFieldsExpr = listOfFieldExprs(fk.parentFieldExpressions)

        val childFunBody = CodeBlock.builder()
        if (fk.isSelfReferential) {
            childFunBody.addStatement(
                "val builder = %T(recordGraph = recordBuilder.recordGraph, parentNode = null, parentFkFields = $fkFieldsExpr, parentRefFields = $refFieldsExpr, isSelfReferential = true)",
                childBuilderClass
            )
        } else {
            childFunBody.addStatement(
                "val builder = %T(recordGraph = recordBuilder.recordGraph, parentNode = null, parentFkFields = $fkFieldsExpr, parentRefFields = $refFieldsExpr)",
                childBuilderClass
            )
        }
        childFunBody.addStatement("builder.block()")
        childFunBody.addStatement("val placeholderRecord = builder.recordBuilder.getOrBuildRecord()")
        childFunBody.beginControlFlow("childBlocks.add { parentNode ->")
        childFunBody.addStatement("builder.recordBuilder.parentNode = parentNode")
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

    private fun emitMultiFkChildFunction(
        classBuilder: TypeSpec.Builder,
        fkGroup: List<ForeignKeyIR>,
        childBuilderClass: ClassName,
        childResultClass: ClassName,
        childRecordClass: ClassName,
        blockType: LambdaTypeName,
        listOfTableFieldStarType: com.squareup.kotlinpoet.TypeName
    ) {
        val fk = fkGroup.first()
        val tableFieldType = ClassName("org.jooq", "TableField")
            .parameterizedBy(childRecordClass, com.squareup.kotlinpoet.STAR)

        val childFunBody = CodeBlock.builder()
        childFunBody.addStatement("val fkNames = fkFields.map { it.name }.toSet()")
        childFunBody.addStatement("val parentFkFields: %T", listOfTableFieldStarType)
        childFunBody.addStatement("val parentRefFields: %T", listOfTableFieldStarType)

        // if/else-if chain matching FK column name sets to full field mappings
        val first = fkGroup[0]
        val firstNamesSet = first.fkColumnNames.joinToString(", ") { "\"$it\"" }
        childFunBody.beginControlFlow("if (fkNames == setOf($firstNamesSet))")
        childFunBody.addStatement("parentFkFields = ${listOfFieldExprs(first.childFieldExpressions)}")
        childFunBody.addStatement("parentRefFields = ${listOfFieldExprs(first.parentFieldExpressions)}")

        for (i in 1 until fkGroup.size) {
            val groupFk = fkGroup[i]
            val namesSet = groupFk.fkColumnNames.joinToString(", ") { "\"$it\"" }
            childFunBody.nextControlFlow("else if (fkNames == setOf($namesSet))")
            childFunBody.addStatement("parentFkFields = ${listOfFieldExprs(groupFk.childFieldExpressions)}")
            childFunBody.addStatement("parentRefFields = ${listOfFieldExprs(groupFk.parentFieldExpressions)}")
        }

        childFunBody.nextControlFlow("else")
        childFunBody.addStatement("error(%S)", "No FK matching field names: \$fkNames")
        childFunBody.endControlFlow()

        childFunBody.addStatement(
            "val builder = %T(recordGraph = recordBuilder.recordGraph, parentNode = null, parentFkFields = parentFkFields, parentRefFields = parentRefFields)",
            childBuilderClass
        )
        childFunBody.addStatement("builder.block()")
        childFunBody.addStatement("val placeholderRecord = builder.recordBuilder.getOrBuildRecord()")
        childFunBody.beginControlFlow("childBlocks.add { parentNode ->")
        childFunBody.addStatement("builder.recordBuilder.parentNode = parentNode")
        childFunBody.addStatement("builder.buildWithChildren()")
        childFunBody.endControlFlow()
        childFunBody.addStatement("return %T(placeholderRecord)", childResultClass)

        classBuilder.addFunction(
            FunSpec.builder(fk.builderFunctionName)
                .addParameter(
                    ParameterSpec.builder("fkFields", tableFieldType)
                        .addModifiers(KModifier.VARARG)
                        .build()
                )
                .addParameter("block", blockType)
                .returns(childResultClass)
                .addCode(childFunBody.build())
                .build()
        )
    }

    private fun listOfFieldExprs(exprs: List<String>): String {
        return "listOf(${exprs.joinToString(", ") { "$it as TableField<*, *>" }})"
    }

    private fun toPascalCase(input: String): String = NamingConventions.toPascalCase(input)
}
