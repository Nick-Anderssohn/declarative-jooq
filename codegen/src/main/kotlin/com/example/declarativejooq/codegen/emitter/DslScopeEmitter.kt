package com.example.declarativejooq.codegen.emitter

import com.example.declarativejooq.codegen.ir.TableIR
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.UNIT

class DslScopeEmitter {

    fun emit(tableIR: TableIR, outputPackage: String): FunSpec {
        val dslScopeType = ClassName("com.example.declarativejooq", "DslScope")
        val builderClass = ClassName(outputPackage, tableIR.builderClassName)
        val blockType = LambdaTypeName.get(receiver = builderClass, returnType = UNIT)

        return FunSpec.builder(tableIR.dslFunctionName)
            .receiver(dslScopeType)
            .addParameter("block", blockType)
            .addStatement("val builder = %T(recordGraph)", builderClass)
            .addStatement("builder.block()")
            .addStatement("val node = builder.buildWithChildren()")
            .addStatement("recordGraph.addRootNode(node)")
            .build()
    }
}
