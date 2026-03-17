package com.nickanderssohn.declarativejooq

import org.jooq.DSLContext

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

                // Resolve placeholder FK references (overrides parent-context FK if same field)
                for (ref in node.placeholderRefs) {
                    val targetPk = ref.targetNode.table.primaryKey
                        ?: throw IllegalStateException(
                            "Placeholder target table ${ref.targetNode.table.name} has no primary key"
                        )
                    val targetPkValue = ref.targetNode.record.get(targetPk.fields[0])
                        ?: throw IllegalStateException(
                            "Placeholder target PK is null — target must be inserted before referencing node. " +
                            "Table: ${ref.targetNode.table.name}, FK field: ${ref.fkField.name}"
                        )
                    @Suppress("UNCHECKED_CAST")
                    (node.record as org.jooq.Record).set(
                        ref.fkField as org.jooq.Field<Any?>,
                        targetPkValue
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
