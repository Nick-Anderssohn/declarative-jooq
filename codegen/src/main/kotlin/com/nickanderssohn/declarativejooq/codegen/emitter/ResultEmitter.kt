package com.nickanderssohn.declarativejooq.codegen.emitter

import com.nickanderssohn.declarativejooq.codegen.ir.TableIR
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

class ResultEmitter {

    fun emit(tableIR: TableIR, outputPackage: String): TypeSpec {
        val recordType = ClassName(tableIR.sourcePackage, tableIR.recordClassName)

        val classBuilder = TypeSpec.classBuilder(tableIR.resultClassName)

        // Constructor: record: RecordClass (private val)
        classBuilder.primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(
                    ParameterSpec.builder("record", recordType)
                        .build()
                )
                .build()
        )
        classBuilder.addProperty(
            PropertySpec.builder("record", recordType, KModifier.PRIVATE)
                .initializer("record")
                .build()
        )

        // Read-only val properties for EACH column (including identity)
        for (col in tableIR.columns) {
            val getter = FunSpec.getterBuilder()
                .addStatement("return record.get(%L)", col.tableFieldRefExpression)
                .build()

            classBuilder.addProperty(
                PropertySpec.builder(col.propertyName, col.kotlinTypeName.copy(nullable = true))
                    .getter(getter)
                    .build()
            )
        }

        return classBuilder.build()
    }
}
