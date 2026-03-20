package com.nickanderssohn.declarativejooq

import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.TableField
import org.jooq.UpdatableRecord

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
                    copyPrimaryKeyToFkFields(
                        sourceRecord = node.parentNode.record,
                        sourceTableLabel = node.parentNode.table.name,
                        targetRecord = node.record,
                        fkFields = node.parentFkFields,
                        nullKeyMessage = { "Parent record PK is null — parent must be inserted before child" }
                    )
                }

                // Resolve placeholder FK references (overrides parent-context FK if same field)
                for (ref in node.placeholderRefs) {
                    copyPrimaryKeyToFkFields(
                        sourceRecord = ref.targetNode.record,
                        sourceTableLabel = ref.targetNode.table.name,
                        targetRecord = node.record,
                        fkFields = ref.fkFields,
                        nullKeyMessage = {
                            "Placeholder target PK is null — target must be inserted before referencing node. " +
                                "Table: ${ref.targetNode.table.name}, FK fields: ${ref.fkFields.joinToString { it.name }}"
                        }
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
            copyPrimaryKeyToFkFields(
                sourceRecord = node.parentNode!!.record,
                sourceTableLabel = node.parentNode!!.table.name,
                targetRecord = node.record,
                fkFields = node.parentFkFields,
                nullKeyMessage = { "Parent record PK is null after insert" }
            )
            node.record.store()  // UPDATE with the FK value
        }

        return ResultAssembler.assemble(allNodes)
    }

    /**
     * Copies the source row's primary key values into the target row's FK columns, in order.
     * Assumes the FK references the parent's primary key (same as single-column behaviour).
     */
    private fun copyPrimaryKeyToFkFields(
        sourceRecord: UpdatableRecord<*>,
        sourceTableLabel: String,
        targetRecord: UpdatableRecord<*>,
        fkFields: List<TableField<*, *>>,
        nullKeyMessage: () -> String
    ) {
        val pk = sourceRecord.table.primaryKey
            ?: throw IllegalStateException("Table $sourceTableLabel has no primary key")
        val pkFields = pk.fields
        require(pkFields.size == fkFields.size) {
            "PK/FK width mismatch for $sourceTableLabel -> ${targetRecord.table.name}: " +
                "${pkFields.size} PK columns vs ${fkFields.size} FK columns"
        }
        for (i in pkFields.indices) {
            val value = sourceRecord.get(pkFields[i])
                ?: throw IllegalStateException(nullKeyMessage())
            @Suppress("UNCHECKED_CAST")
            (targetRecord as Record).set(
                fkFields[i] as Field<Any?>,
                value
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
