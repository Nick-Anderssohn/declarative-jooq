package com.nickanderssohn.declarativejooq

import org.jooq.DSLContext

/**
 * Inserts all records from a [RecordGraph] into the database in FK-dependency order.
 * Uses [TopologicalSorter] to determine table-level insert order, resolves parent FK
 * values before each insert, and performs a second pass for self-referential FKs.
 */
class TopologicalInserter(private val dslContext: DSLContext) {
    fun insertAll(graph: RecordGraph): DslResult {
        val allNodes = graph.allNodes()
        if (allNodes.isEmpty()) {
            return DslResult(LinkedHashMap())
        }

        // Build table dependency graph from the actual nodes
        val tableGraph = buildTableGraph(allNodes, graph)

        // Get topological order
        val sortedTables = TopologicalSorter.sort(tableGraph)

        // Group nodes by table name, preserving declaration order within each group
        val nodesByTable = LinkedHashMap<String, MutableList<RecordNode>>()
        for (tableName in sortedTables) {
            nodesByTable[tableName] = mutableListOf()
        }
        for (node in allNodes.sortedBy { it.declarationIndex }) {
            nodesByTable[node.table.name]?.add(node)
        }

        // Insert in topological order
        for ((_, nodes) in nodesByTable) {
            for (node in nodes) {
                // Resolve FK from parent if applicable (skip self-referential on first pass)
                if (node.parentNode != null && node.parentFkFields.isNotEmpty() && !node.isSelfReferential) {
                    resolveParentFk(node)
                }

                // Resolve placeholder FK references (overrides parent-context FK if same field)
                for (ref in node.placeholderRefs) {
                    for (i in ref.fkFields.indices) {
                        val targetValue = ref.targetNode.record.get(ref.refFields[i])
                            ?: throw IllegalStateException(
                                "Placeholder target field is null — target must be inserted before referencing node. " +
                                "Table: ${ref.targetNode.table.name}, FK field: ${ref.fkFields[i].name}"
                            )
                        @Suppress("UNCHECKED_CAST")
                        (node.record as org.jooq.Record).set(
                            ref.fkFields[i] as org.jooq.Field<Any?>,
                            targetValue
                        )
                    }
                }

                // Attach to DSLContext and insert
                node.record.attach(dslContext.configuration())
                node.record.store()
            }
        }

        // Second pass: update self-referential FK values now that all PKs are known
        val selfRefNodes = allNodes.filter { it.isSelfReferential && it.parentNode != null }
        for (node in selfRefNodes) {
            resolveParentFk(node)
            node.record.store()
        }

        return ResultAssembler.assemble(allNodes)
    }

    private fun resolveParentFk(node: RecordNode) {
        val parent = node.parentNode
            ?: throw IllegalStateException("resolveParentFk called on node without parent")
        for (i in node.parentFkFields.indices) {
            val parentValue = parent.record.get(node.parentRefFields[i])
                ?: throw IllegalStateException(
                    "Parent record field ${node.parentRefFields[i].name} is null — parent must be inserted before child"
                )
            @Suppress("UNCHECKED_CAST")
            (node.record as org.jooq.Record).set(
                node.parentFkFields[i] as org.jooq.Field<Any?>,
                parentValue
            )
        }
    }

    private fun buildTableGraph(nodes: List<RecordNode>, graph: RecordGraph): Map<String, Set<String>> {
        val tableGraph = mutableMapOf<String, MutableSet<String>>()
        for (node in nodes) {
            tableGraph.getOrPut(node.table.name) { mutableSetOf() }
            if (node.parentNode != null) {
                tableGraph.getOrPut(node.table.name) { mutableSetOf() }
                    .add(node.parentNode.table.name)
            }
        }
        // Add cross-tree placeholder edges
        for ((sourceNode, ref) in graph.placeholderRefs) {
            tableGraph.getOrPut(sourceNode.table.name) { mutableSetOf() }
                .add(ref.targetNode.table.name)
        }
        return tableGraph
    }
}
