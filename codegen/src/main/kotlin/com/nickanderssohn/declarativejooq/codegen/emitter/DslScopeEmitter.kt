package com.nickanderssohn.declarativejooq.codegen.emitter

import com.nickanderssohn.declarativejooq.codegen.ir.TableIR
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.UNIT

class DslScopeEmitter {

    fun emit(tableIR: TableIR, outputPackage: String): FunSpec {
        val dslScopeType = ClassName("com.nickanderssohn.declarativejooq", "DslScope")
        val builderClass = ClassName(outputPackage, tableIR.builderClassName)
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
            .addStatement(builderConstruction, builderClass)
            .addStatement("builder.block()")
            .addStatement("val node = builder.buildWithChildren()")
            .addStatement("recordGraph.addRootNode(node)")
            .build()
    }
}
