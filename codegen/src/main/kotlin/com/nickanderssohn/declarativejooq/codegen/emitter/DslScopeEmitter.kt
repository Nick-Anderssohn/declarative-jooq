package com.nickanderssohn.declarativejooq.codegen.emitter

import com.nickanderssohn.declarativejooq.codegen.ir.TableIR
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.UNIT

/**
 * Emits a [DslScope][com.nickanderssohn.declarativejooq.DslScope] extension function for
 * each root table (e.g., `fun DslScope.organization(block: OrganizationBuilder.() -> Unit)`).
 * These are the top-level entry points in the DSL block passed to [DecDsl.execute][com.nickanderssohn.declarativejooq.DecDsl.execute].
 */
class DslScopeEmitter {

    fun emit(tableIR: TableIR, outputPackage: String): FunSpec {
        val dslScopeType = ClassName("com.nickanderssohn.declarativejooq", "DslScope")
        val builderClass = ClassName(outputPackage, tableIR.builderClassName)
        val resultClass = ClassName(outputPackage, tableIR.resultClassName)
        val recordType = ClassName(tableIR.sourcePackage, tableIR.recordClassName)
        val blockType = LambdaTypeName.get(receiver = builderClass, returnType = UNIT)

        val hasSelfRefInbound = tableIR.inboundFKs.any { it.isSelfReferential }

        val builderConstruction = if (hasSelfRefInbound) {
            // Self-ref root: uses child-style constructor with null parent
            "val builder = %T(recordGraph = recordGraph, parentNode = null, parentFkField = null)"
        } else {
            // Regular root: uses graph-only constructor
            "val builder = %T(recordGraph)"
        }

        return FunSpec.builder(tableIR.dslFunctionName)
            .receiver(dslScopeType)
            .addParameter("block", blockType)
            .returns(resultClass)
            .addStatement(builderConstruction, builderClass)
            .addStatement("builder.block()")
            .addStatement("val node = builder.buildWithChildren()")
            .addStatement("recordGraph.addRootNode(node)")
            .addStatement("val result = %T(node.record as %T)", resultClass, recordType)
            .addStatement("return result")
            .build()
    }
}
