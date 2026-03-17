package com.nickanderssohn.declarativejooq.codegen.scanner

import com.nickanderssohn.declarativejooq.codegen.ir.ColumnIR
import com.nickanderssohn.declarativejooq.codegen.ir.ForeignKeyIR
import com.nickanderssohn.declarativejooq.codegen.ir.TableIR
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import org.jooq.impl.TableImpl
import java.io.File
import java.lang.reflect.Modifier
import java.net.URLClassLoader

class MetadataExtractor {

    fun extract(classDir: File, tableClassNames: List<String>): List<TableIR> {
        val classLoader = URLClassLoader(
            arrayOf(classDir.toURI().toURL()),
            Thread.currentThread().contextClassLoader
        )

        // First pass: build all TableIR objects (without cross-linked inboundFKs)
        val tables = tableClassNames.map { className ->
            val klass = classLoader.loadClass(className)
            @Suppress("UNCHECKED_CAST")
            val tableClass = klass as Class<*>
            val (tableInstance, tableConstantName) = loadTableInstance(tableClass)

            val tableName = tableInstance.name
            val tableClassName = klass.simpleName
            val recordClassName = tableInstance.recordType.simpleName
            val sourcePackage = klass.packageName

            val builderClassName = toPascalCase(tableName) + "Builder"
            val resultClassName = toPascalCase(tableName) + "Result"
            val dslFunctionName = toCamelCase(tableName)

            // Build the field-to-declared-field-name map for tableFieldRefExpression
            val fieldRefMap = buildFieldRefMap(tableClass, tableInstance)

            // Extract columns
            val identityField = tableInstance.identity?.field
            val columns = tableInstance.fields().map { field ->
                val refFieldName = fieldRefMap[field.name] ?: field.name.uppercase()
                val tableFieldRef = "$tableClassName.$tableConstantName.$refFieldName"
                ColumnIR(
                    columnName = field.name,
                    propertyName = toCamelCase(field.name),
                    kotlinTypeName = mapJavaTypeToKotlinPoet(field.dataType.type),
                    isIdentity = field == identityField,
                    isNullable = field.dataType.nullable(),
                    tableFieldRefExpression = tableFieldRef
                )
            }

            // Extract outbound FKs (single-column only) using two-pass naming algorithm

            // Helper to hold raw FK data before final name resolution
            data class RawFk(
                val fkName: String,
                val fkColumnName: String,
                val childFieldExpr: String,
                val parentTableName: String,
                val parentBuilderClassName: String,
                val isSelfRef: Boolean,
                val candidateName: String,
                val placeholderPropertyName: String
            )

            // Pass 1: Collect FK data and compute candidate names per NAME-01/02/04 rules
            val rawFks = tableInstance.references
                .filter { fk -> fk.fields.size == 1 }
                .map { fk ->
                    val fkColumnName = fk.fields[0].name
                    val childFieldExpr = columns.find { it.columnName == fkColumnName }
                        ?.tableFieldRefExpression
                        ?: "$tableClassName.$tableConstantName.${fkColumnName.uppercase()}"
                    val parentTableName = fk.key.table.name
                    val parentBuilderClassName = toPascalCase(parentTableName) + "Builder"
                    val isSelfRef = parentTableName == tableName
                    val strippedFkCol = fkColumnName.removeSuffix("_id")

                    val candidateName = if (isSelfRef) {
                        toCamelCase(tableName)          // NAME-04: self-ref uses table name
                    } else if (strippedFkCol == parentTableName) {
                        toCamelCase(tableName)          // NAME-01: stripped col matches parent -> use child table name
                    } else {
                        toCamelCase(strippedFkCol)      // NAME-02: no match -> use FK column name
                    }

                    RawFk(fk.name, fkColumnName, childFieldExpr, parentTableName, parentBuilderClassName, isSelfRef, candidateName,
                        placeholderPropertyName = toCamelCase(fkColumnName.removeSuffix("_id"))
                    )
                }

            // Pass 2: Collision detection per NAME-03 — if two FKs produce the same candidate, both fall back to FK col name
            val nameCounts = rawFks.groupingBy { it.candidateName }.eachCount()
            val collidingNames = nameCounts.filter { it.value > 1 }.keys

            val outboundFKs = rawFks.map { raw ->
                val finalName = if (raw.candidateName in collidingNames) {
                    toCamelCase(raw.fkColumnName.removeSuffix("_id"))
                } else {
                    raw.candidateName
                }
                ForeignKeyIR(
                    fkName = raw.fkName,
                    childTableName = tableName,
                    childFieldExpression = raw.childFieldExpr,
                    parentTableName = raw.parentTableName,
                    parentBuilderClassName = raw.parentBuilderClassName,
                    parentResultClassName = toPascalCase(raw.parentTableName) + "Result",
                    builderFunctionName = finalName,
                    placeholderPropertyName = raw.placeholderPropertyName,
                    childResultClassName = resultClassName,
                    childRecordClassName = recordClassName,
                    childSourcePackage = sourcePackage,
                    isSelfReferential = raw.isSelfRef
                )
            }

            TableIR(
                tableName = tableName,
                tableClassName = tableClassName,
                tableConstantName = tableConstantName,
                recordClassName = recordClassName,
                builderClassName = builderClassName,
                resultClassName = resultClassName,
                dslFunctionName = dslFunctionName,
                sourcePackage = sourcePackage,
                columns = columns,
                outboundFKs = outboundFKs,
                inboundFKs = mutableListOf(),
                isRoot = outboundFKs.none { !it.isSelfReferential }
            )
        }

        // Second pass: cross-link inboundFKs
        val tableByName = tables.associateBy { it.tableName }
        for (table in tables) {
            for (fk in table.outboundFKs) {
                tableByName[fk.parentTableName]?.inboundFKs?.add(fk)
            }
        }

        return tables
    }

    private fun loadTableInstance(klass: Class<*>): Pair<TableImpl<*>, String> {
        // Find a static field whose type equals the class — this is the companion object singleton
        val staticField = klass.declaredFields
            .firstOrNull { field ->
                Modifier.isStatic(field.modifiers) && field.type == klass
            }
            ?: throw IllegalStateException(
                "No static singleton field found on ${klass.name}. " +
                "Expected a field of type ${klass.simpleName} (e.g., companion object val TABLE = TableClass())"
            )
        staticField.isAccessible = true
        val instance = staticField.get(null) as TableImpl<*>
        return Pair(instance, staticField.name)
    }

    /**
     * Build a map from SQL column name -> declared field name on the table class.
     * e.g., "organization_id" -> "ORGANIZATION_ID"
     * This handles cases where the field name differs from the column name.
     */
    private fun buildFieldRefMap(tableClass: Class<*>, tableInstance: TableImpl<*>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val tableFields = tableInstance.fields()
        for (declaredField in tableClass.declaredFields) {
            if (Modifier.isStatic(declaredField.modifiers)) continue
            declaredField.isAccessible = true
            try {
                val value = declaredField.get(tableInstance)
                if (value != null) {
                    val matchingField = tableFields.find { it === value }
                    if (matchingField != null) {
                        result[matchingField.name] = declaredField.name
                    }
                }
            } catch (_: Exception) {
                // Skip fields that can't be accessed
            }
        }
        return result
    }

    private fun mapJavaTypeToKotlinPoet(javaType: Class<*>): TypeName {
        return when (javaType) {
            java.lang.Long::class.java, Long::class.java -> LONG
            java.lang.Integer::class.java, Int::class.java -> INT
            java.lang.String::class.java -> STRING
            java.lang.Boolean::class.java, Boolean::class.java -> BOOLEAN
            java.math.BigDecimal::class.java -> ClassName("java.math", "BigDecimal")
            java.time.LocalDate::class.java -> ClassName("java.time", "LocalDate")
            java.time.LocalDateTime::class.java -> ClassName("java.time", "LocalDateTime")
            else -> javaType.asTypeName()
        }
    }

    /**
     * Converts snake_case to camelCase: "organization_id" -> "organizationId", "name" -> "name"
     */
    fun toCamelCase(input: String): String {
        val parts = input.split("_")
        if (parts.isEmpty()) return input
        return parts[0].lowercase() + parts.drop(1).joinToString("") { it.replaceFirstChar { c -> c.uppercaseChar() } }
    }

    /**
     * Converts snake_case to PascalCase: "organization" -> "Organization", "app_user" -> "AppUser"
     */
    fun toPascalCase(input: String): String {
        return input.split("_").joinToString("") { it.replaceFirstChar { c -> c.uppercaseChar() } }
    }
}
