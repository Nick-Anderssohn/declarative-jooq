package com.nickanderssohn.declarativejooq.codegen.emitter

import com.nickanderssohn.declarativejooq.codegen.ir.TableIR
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

class DslResultEmitter {

    fun emit(tables: List<TableIR>, outputPackage: String): TypeSpec {
        val dslResultType = ClassName("com.nickanderssohn.declarativejooq", "DslResult")
        val listType = ClassName("kotlin.collections", "List")

        val classBuilder = TypeSpec.classBuilder("GeneratedDslResult")

        // Constructor: result: DslResult (private val)
        classBuilder.primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(
                    ParameterSpec.builder("result", dslResultType)
                        .build()
                )
                .build()
        )
        classBuilder.addProperty(
            PropertySpec.builder("result", dslResultType, KModifier.PRIVATE)
                .initializer("result")
                .build()
        )

        // Accessor function for EVERY table (not just roots)
        for (table in tables) {
            val recordClass = ClassName(table.sourcePackage, table.recordClassName)
            val resultClass = ClassName(outputPackage, table.resultClassName)
            val returnType = listType.parameterizedBy(resultClass)

            // pluralize by appending "s"
            val functionName = table.dslFunctionName + "s"

            classBuilder.addFunction(
                FunSpec.builder(functionName)
                    .returns(returnType)
                    .addStatement(
                        "return result.records<%T>(%S).map { %T(it as %T) }",
                        recordClass,
                        table.tableName,
                        resultClass,
                        recordClass
                    )
                    .build()
            )
        }

        return classBuilder.build()
    }
}
