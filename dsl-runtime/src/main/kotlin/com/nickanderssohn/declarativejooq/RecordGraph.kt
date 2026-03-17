package com.example.declarativejooq

class RecordGraph {
    private val _rootNodes: MutableList<RecordNode> = mutableListOf()
    val rootNodes: List<RecordNode> get() = _rootNodes

    private var nextIndex = 0

    fun nextDeclarationIndex(): Int = nextIndex++

    fun addRootNode(node: RecordNode) {
        _rootNodes.add(node)
    }

    fun allNodes(): List<RecordNode> {
        val result = mutableListOf<RecordNode>()
        fun collect(node: RecordNode) {
            result.add(node)
            node.children.forEach { collect(it) }
        }
        _rootNodes.forEach { collect(it) }
        return result
    }
}
