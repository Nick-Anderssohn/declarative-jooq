package com.example.declarativejooq

import org.jooq.DSLContext

class TopologicalInserter(private val dslContext: DSLContext) {
    fun insertAll(graph: RecordGraph): DslResult {
        val allNodes = graph.allNodes()
        if (allNodes.isEmpty()) {
            return DslResult(LinkedHashMap())
        }

        // Build table dependency graph from the actual nodes
        val tableGraph = buildTableGraph(allNodes)

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
                if (node.parentNode != null && node.parentFkField != null && !node.isSelfReferential) {
                    val parentPk = node.parentNode.table.primaryKey
                        ?: throw IllegalStateException(
                            "Parent table ${node.parentNode.table.name} has no primary key"
                        )
                    val parentPkValue = node.parentNode.record.get(parentPk.fields[0])
                        ?: throw IllegalStateException(
                            "Parent record PK is null — parent must be inserted before child"
                        )
                    @Suppress("UNCHECKED_CAST")
                    (node.record as org.jooq.Record).set(
                        node.parentFkField as org.jooq.Field<Any?>,
                        parentPkValue
                    )
                }

                // Attach to DSLContext and insert
                node.record.attach(dslContext.configuration())
                node.record.store()
                // After store(), jOOQ auto-populates generated PK via getGeneratedKeys()
            }
        }

        // Second pass: update self-referential FK values now that all PKs are known
        val selfRefNodes = allNodes.filter { it.isSelfReferential && it.parentNode != null }
        for (node in selfRefNodes) {
            val parentPk = node.parentNode!!.table.primaryKey
                ?: throw IllegalStateException("Parent table ${node.parentNode.table.name} has no primary key")
            val parentPkValue = node.parentNode.record.get(parentPk.fields[0])
                ?: throw IllegalStateException("Parent record PK is null after insert")
            @Suppress("UNCHECKED_CAST")
            (node.record as org.jooq.Record).set(
                node.parentFkField as org.jooq.Field<Any?>,
                parentPkValue
            )
            node.record.store()  // UPDATE with the FK value
        }

        return ResultAssembler.assemble(allNodes)
    }

    private fun buildTableGraph(nodes: List<RecordNode>): Map<String, Set<String>> {
        val graph = mutableMapOf<String, MutableSet<String>>()
        for (node in nodes) {
            graph.getOrPut(node.table.name) { mutableSetOf() }
            if (node.parentNode != null) {
                graph.getOrPut(node.table.name) { mutableSetOf() }
                    .add(node.parentNode.table.name)
            }
        }
        return graph
    }
}
