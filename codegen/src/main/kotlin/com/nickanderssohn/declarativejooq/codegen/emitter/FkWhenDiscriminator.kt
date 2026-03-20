package com.nickanderssohn.declarativejooq.codegen.emitter

import com.nickanderssohn.declarativejooq.codegen.ir.ForeignKeyIR

/**
 * For a multi-FK `when (fkField) { ... }`, each branch must use a distinct [TableField] expression.
 * Picks one child column per FK: the first in key order whose expression is not yet used as a branch.
 * This handles composite FKs that share leading columns (e.g. both start with `org_id`).
 */
internal fun assignFkWhenDiscriminators(fkGroup: List<ForeignKeyIR>): List<Pair<ForeignKeyIR, String>> {
    require(fkGroup.isNotEmpty())
    val duplicateSigs = fkGroup
        .groupBy { it.childFieldExpressions }
        .filter { it.value.size > 1 }
    check(duplicateSigs.isEmpty()) {
        "Cannot disambiguate inbound FK group: duplicate child column sets for FKs " +
            duplicateSigs.values.flatten().joinToString { it.fkName }
    }
    val sorted = fkGroup.sortedBy { it.fkName }
    val used = mutableSetOf<String>()
    return sorted.map { fk ->
        val exprs = fk.childFieldExpressions
        check(exprs.isNotEmpty()) { "FK ${fk.fkName} has no child fields" }
        val pick = exprs.firstOrNull { it !in used }
        check(pick != null) {
            "Cannot emit unique when-branch discriminators for inbound FK group targeting the same parent " +
                "(${fkGroup.joinToString { it.fkName }}) — every child column expression is already used by " +
                "another FK in this group."
        }
        used.add(pick)
        fk to pick
    }
}
