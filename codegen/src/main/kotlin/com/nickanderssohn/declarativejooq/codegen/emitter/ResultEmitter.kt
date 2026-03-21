package com.nickanderssohn.declarativejooq.codegen.emitter

import com.nickanderssohn.declarativejooq.codegen.ir.TableIR
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

/**
 * Emits a per-table result class (e.g., `OrganizationResult`) from a [TableIR].
 * Result classes wrap a jOOQ record and expose read-only properties for each column,
 * and are returned from builder blocks to serve as placeholder references.
 */
class ResultEmitter {

    fun emit(tableIR: TableIR, outputPackage: String): TypeSpec {
        val recordType = ClassName(
            tableIR.recordSourcePackage,
            tableIR.recordClassName
        )

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
            PropertySpec.builder("record", recordType, KModifier.INTERNAL)
                .initializer("record")
                .build()
        )

        // Read-only val properties for EACH column (including identity)
        for (col in tableIR.columns) {
            val getter = FunSpec.getterBuilder()
                .addStatement("return record.get(%L)", col.tableFieldRefExpression)
                .build()

            classBuilder.addProperty(
                PropertySpec
                    .builder(
                        col.propertyName,
                        col.kotlinTypeName.copy(nullable = true)
                    )
                    .getter(getter)
                    .build()
            )
        }

        return classBuilder.build()
    }
}
