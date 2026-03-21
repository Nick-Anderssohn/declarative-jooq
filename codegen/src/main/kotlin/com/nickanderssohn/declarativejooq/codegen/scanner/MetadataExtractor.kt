package com.nickanderssohn.declarativejooq.codegen.scanner

import com.nickanderssohn.declarativejooq.codegen.ir.ColumnIR
import com.nickanderssohn.declarativejooq.codegen.ir.ForeignKeyIR
import com.nickanderssohn.declarativejooq.codegen.ir.TableIR
import com.nickanderssohn.declarativejooq.codegen.scanner.NamingConventions
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import org.jooq.impl.TableImpl
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.math.BigDecimal
import java.net.URLClassLoader
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Reflectively inspects compiled jOOQ table classes to extract schema metadata (columns,
 * foreign keys, identity fields) into [TableIR] intermediate representations. Handles
 * FK naming conventions, multi-FK disambiguation, and cross-links inbound FKs between tables.
 */
class MetadataExtractor {

    private data class TableMeta(
        val tableName: String,
        val tableClassName: String,
        val tableConstantName: String,
        val tableInstance: TableImpl<*>,
        val fieldRefMap: Map<String, String>,
        val klass: Class<*>
    )

    fun extract(classDir: File, tableClassNames: List<String>): List<TableIR> {
        val classLoader = URLClassLoader(
            arrayOf(classDir.toURI().toURL()),
            Thread
                .currentThread()
                .contextClassLoader
        )

        // Phase 1: pre-load all table instances and field ref maps so parent field
        // expressions can be resolved when extracting FKs.
        val tableMetas = tableClassNames.map { className ->
            val klass = classLoader.loadClass(className)
            val tableClass = klass as Class<*>
            val (tableInstance, tableConstantName) = loadTableInstance(tableClass)
            TableMeta(
                tableName = tableInstance.name,
                tableClassName = klass.simpleName,
                tableConstantName = tableConstantName,
                tableInstance = tableInstance,
                fieldRefMap = buildFieldRefMap(tableClass, tableInstance),
                klass = klass
            )
        }
        val metaByTableName = tableMetas.associateBy { it.tableName }

        // Phase 2: build all TableIR objects (without cross-linked inboundFKs)
        val tables = tableMetas.map { meta ->
            val tableName = meta.tableName
            val tableClassName = meta.tableClassName
            val tableConstantName = meta.tableConstantName
            val tableInstance = meta.tableInstance
            val fieldRefMap = meta.fieldRefMap
            val klass = meta.klass

            val recordClassName = tableInstance.recordType.simpleName
            val sourcePackage = klass.packageName
            val recordSourcePackage = tableInstance.recordType.packageName

            val builderClassName = toPascalCase(tableName) + "Builder"
            val resultClassName = toPascalCase(tableName) + "Result"
            val dslFunctionName = toCamelCase(tableName)

            // Extract columns
            val identityField = tableInstance.identity?.field
            val columns = tableInstance
                .fields()
                .map { field ->
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

            // Extract outbound FKs using two-pass naming algorithm

            data class RawFk(
                val fkName: String,
                val fkColumnNames: List<String>,
                val childFieldExprs: List<String>,
                val parentFieldExprs: List<String>,
                val parentTableName: String,
                val parentBuilderClassName: String,
                val isSelfRef: Boolean,
                val isComposite: Boolean,
                val candidateName: String,
                val placeholderPropertyName: String
            )

            // Pass 1: Collect FK data and compute candidate names per NAME-01/02/04 rules
            val rawFks = tableInstance
                .references
                .map { fk ->
                    val fkColumnNames = fk.fields.map { it.name }
                val isComposite = fkColumnNames.size > 1

                val childFieldExprs = fkColumnNames
                    .map { colName ->
                        columns
                            .find { it.columnName == colName }
                            ?.tableFieldRefExpression
                            ?: "$tableClassName.$tableConstantName.${colName.uppercase()}"
                    }

                val parentTableName = fk.key.table.name
                val parentMeta = metaByTableName[parentTableName]
                val parentFieldExprs = fk
                    .key
                    .fields
                    .map { parentField ->
                        if (parentMeta != null) {
                            val refName = parentMeta.fieldRefMap[parentField.name]
                                ?: parentField.name.uppercase()
                            "${parentMeta.tableClassName}.${parentMeta.tableConstantName}.$refName"
                        } else {
                            parentField.name.uppercase()
                        }
                    }

                val parentBuilderClassName = toPascalCase(parentTableName) + "Builder"
                val isSelfRef = parentTableName == tableName

                val candidateName: String
                val placeholderPropertyName: String

                if (isComposite) {
                    candidateName = toCamelCase(tableName)
                    placeholderPropertyName = toCamelCase(parentTableName)
                } else {
                    val fkColumnName = fkColumnNames[0]
                    val strippedFkCol = NamingConventions.stripIdSuffix(fkColumnName)
                    candidateName = if (isSelfRef) {
                        toCamelCase(tableName)
                    } else if (NamingConventions.normalizedEquals(strippedFkCol, parentTableName)) {
                        toCamelCase(tableName)
                    } else {
                        toCamelCase(strippedFkCol)
                    }
                    placeholderPropertyName = toCamelCase(NamingConventions.stripIdSuffix(fkColumnName))
                }

                RawFk(
                    fkName = fk.name,
                    fkColumnNames = fkColumnNames,
                    childFieldExprs = childFieldExprs,
                    parentFieldExprs = parentFieldExprs,
                    parentTableName = parentTableName,
                    parentBuilderClassName = parentBuilderClassName,
                    isSelfRef = isSelfRef,
                    isComposite = isComposite,
                    candidateName = candidateName,
                    placeholderPropertyName = placeholderPropertyName
                )
            }

            // Detect multi-FK-to-same-parent groups
            val parentGroupCounts = rawFks
                .groupingBy { it.parentTableName }
                .eachCount()
            val multiFkParents = parentGroupCounts
                .filter { it.value > 1 }
                .keys

            // Pass 2: Collision detection per NAME-03
            val nonMultiFkRaws = rawFks
                .filter { it.parentTableName !in multiFkParents }
            val nameCounts = nonMultiFkRaws
                .groupingBy { it.candidateName }
                .eachCount()
            val collidingNames = nameCounts
                .filter { it.value > 1 }
                .keys

            val outboundFKs = rawFks
                .map { raw ->
                val isMultiFk = raw.parentTableName in multiFkParents
                val finalName = when {
                    isMultiFk -> toCamelCase(tableName)
                    raw.candidateName in collidingNames && !raw.isComposite ->
                        toCamelCase(NamingConventions.stripIdSuffix(raw.fkColumnNames[0]))
                    else -> raw.candidateName
                }
                ForeignKeyIR(
                    fkName = raw.fkName,
                    childTableName = tableName,
                    childFieldExpressions = raw.childFieldExprs,
                    parentTableName = raw.parentTableName,
                    parentFieldExpressions = raw.parentFieldExprs,
                    parentBuilderClassName = raw.parentBuilderClassName,
                    parentResultClassName = toPascalCase(raw.parentTableName) + "Result",
                    builderFunctionName = finalName,
                    placeholderPropertyName = raw.placeholderPropertyName,
                    childResultClassName = resultClassName,
                    childRecordClassName = recordClassName,
                    childSourcePackage = sourcePackage,
                    childRecordSourcePackage = recordSourcePackage,
                    isSelfReferential = raw.isSelfRef,
                    isMultiFk = isMultiFk,
                    fkColumnNames = raw.fkColumnNames
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
                recordSourcePackage = recordSourcePackage,
                columns = columns,
                outboundFKs = outboundFKs,
                inboundFKs = mutableListOf(),
                isRoot = outboundFKs.none { !it.isSelfReferential }
            )
        }

        // Phase 3: cross-link inboundFKs
        val tableByName = tables.associateBy { it.tableName }
        tables
            .flatMap { it.outboundFKs }
            .forEach { fk ->
                tableByName[fk.parentTableName]
                    ?.inboundFKs
                    ?.add(fk)
            }

        return tables
    }

    private fun loadTableInstance(klass: Class<*>): Pair<TableImpl<*>, String> {
        // Find a static field whose type equals the class — this is the companion object singleton
        val staticField = klass
            .declaredFields
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
        val tableFields = tableInstance.fields()

        return tableClass
            .declaredFields
            .asSequence()
            .filterNot { Modifier.isStatic(it.modifiers) }
            .onEach { it.isAccessible = true }
            .mapNotNull { declaredField ->
                declaredField
                    .getOrNull(tableInstance)
                    ?.let { value -> tableFields.find { it === value } }
                    ?.let { matchingField -> matchingField.name to declaredField.name }
            }
            .toMap()
    }

    private fun Field.getOrNull(obj: Any): Any? =
        try { get(obj) } catch (_: Exception) { null }

    private fun mapJavaTypeToKotlinPoet(javaType: Class<*>): TypeName {
        return when (javaType) {
            java.lang.Long::class.java, Long::class.java -> LONG
            Integer::class.java, Int::class.java -> INT
            java.lang.String::class.java -> STRING
            java.lang.Boolean::class.java, Boolean::class.java -> BOOLEAN
            BigDecimal::class.java -> ClassName("java.math", "BigDecimal")
            LocalDate::class.java -> ClassName("java.time", "LocalDate")
            LocalDateTime::class.java -> ClassName("java.time", "LocalDateTime")
            else -> javaType.asTypeName()
        }
    }

    /**
     * Converts any naming convention to camelCase: "organization_id" -> "organizationId",
     * "OrganizationId" -> "organizationId", "organizationId" -> "organizationId"
     */
    fun toCamelCase(input: String): String = NamingConventions.toCamelCase(input)

    /**
     * Converts any naming convention to PascalCase: "organization" -> "Organization",
     * "UserProfile" -> "UserProfile", "userProfile" -> "UserProfile"
     */
    fun toPascalCase(input: String): String = NamingConventions.toPascalCase(input)
}
