package com.nickanderssohn.declarativejooq

import org.jooq.UpdatableRecord

class RecordGraph {
    private val _rootNodes: MutableList<RecordNode> = mutableListOf()
    val rootNodes: List<RecordNode> get() = _rootNodes

    private var nextIndex = 0

    private val _placeholderRefs: MutableList<Pair<RecordNode, PlaceholderRef>> = mutableListOf()
    val placeholderRefs: List<Pair<RecordNode, PlaceholderRef>> get() = _placeholderRefs

    private val _nodeByRecord: MutableMap<UpdatableRecord<*>, RecordNode> = mutableMapOf()

    fun nextDeclarationIndex(): Int = nextIndex++

    fun addRootNode(node: RecordNode) {
        _rootNodes.add(node)
    }

    fun addPlaceholderRef(sourceNode: RecordNode, ref: PlaceholderRef) {
        sourceNode.placeholderRefs.add(ref)
        _placeholderRefs.add(Pair(sourceNode, ref))
    }

    fun registerNode(node: RecordNode) {
        _nodeByRecord[node.record] = node
    }

    fun nodeForRecord(record: UpdatableRecord<*>): RecordNode? = _nodeByRecord[record]

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
