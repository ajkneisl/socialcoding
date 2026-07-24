package com.socialcoding.db

import com.socialcoding.Environment
import com.socialcoding.Initializable
import com.socialcoding.board.Settings
import com.socialcoding.common.NotFound
import com.socialcoding.events.EventAttendance
import com.socialcoding.events.Events
import com.socialcoding.projects.ProjectLikes
import com.socialcoding.projects.ProjectMembers
import com.socialcoding.projects.ProjectTasks
import com.socialcoding.projects.Projects
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory

private val LOGGER = LoggerFactory.getLogger("Database")

object Database : Initializable {
    override suspend fun initialize() {
        Database.connect(
            Environment.getVariable("DB_URL"),
            user = Environment.getVariable("DB_USER"),
            password = Environment.getVariable("DB_PASS"),
        )

        query {
            SchemaUtils.createMissingTablesAndColumns(
                Users,
                Projects,
                ProjectMembers,
                ProjectTasks,
                ProjectLikes,
                Events,
                EventAttendance,
                Settings,
            )
        }
    }

}

suspend fun <T> query(block: JdbcTransaction.() -> T): T =
    withContext(Dispatchers.IO) { transaction { block() } }

private val dbScope =
    CoroutineScope(
        Dispatchers.IO +
            SupervisorJob() +
            CoroutineExceptionHandler { _, e ->
                LOGGER.error("Async DB write failed", e)
            }
    )

val tableAnnotationCache = ConcurrentHashMap<KClass<*>, Table>()

fun resolveTable(kClass: KClass<*>): Table =
    tableAnnotationCache.getOrPut(kClass) {
        val annotation =
            kClass.findAnnotation<MappedTable>()
                ?: error(
                    "${kClass.simpleName} has no @MappedTable annotation and no table was provided"
                )
        annotation.table.objectInstance
            ?: error(
                "@MappedTable table for ${kClass.simpleName} must be an object"
            )
    }

data class EntityMapping(
    val params: List<ParamMapping>,
    val constructor: KFunction<Any>,
)

data class ParamMapping(
    val param: KParameter,
    val column: Column<*>,
    val isJsonCollection: Boolean,
)

val entityMappingCache =
    ConcurrentHashMap<Pair<KClass<*>, Table>, EntityMapping>()

fun buildEntityMapping(kClass: KClass<*>, table: Table): EntityMapping {
    val constructor =
        kClass.primaryConstructor
            ?: error("${kClass.simpleName} has no primary constructor")

    val columnsByCamel =
        table.columns.associateBy { it.name.snakeToCamel().lowercase() }
    val columnsByRawName = table.columns.associateBy { it.name.lowercase() }

    val params =
        constructor.parameters.mapNotNull { param ->
            val mapsTo = param.findAnnotation<MapsTo>()?.column
            val column =
                if (mapsTo != null) {
                    columnsByRawName[mapsTo.lowercase()]
                        ?: error(
                            "@MapsTo column '$mapsTo' not found in table '${table.tableName}'"
                        )
                } else {
                    columnsByCamel[param.name!!.lowercase()]
                }
            if (column == null) {
                // Parameters with defaults (computed/joined fields) simply
                // aren't populated.
                if (param.isOptional) return@mapNotNull null
                error(
                    "No column found for parameter '${param.name}' in table '${table.tableName}'"
                )
            }
            val isJsonCollection =
                param.type.classifier in
                    listOf(List::class, Set::class, Map::class, HashMap::class)
            ParamMapping(param, column, isJsonCollection)
        }

    return EntityMapping(params, constructor)
}

inline fun <reified T : Any> ResultRow.toEntity(table: Table? = null): T {
    val resolvedTable = table ?: resolveTable(T::class)
    val mapping =
        entityMappingCache.getOrPut(T::class to resolvedTable) {
            buildEntityMapping(T::class, resolvedTable)
        }

    val args = HashMap<KParameter, Any?>(mapping.params.size)
    for (pm in mapping.params) {
        val raw = this[pm.column]
        args[pm.param] =
            when {
                raw == null -> null
                pm.isJsonCollection && raw is String ->
                    Json.decodeFromString(serializer(pm.param.type), raw)
                // e.g. a Uuid column feeding a `String` id property.
                pm.param.type.classifier == String::class && raw !is String ->
                    raw.toString()
                else -> raw
            }
    }

    @Suppress("UNCHECKED_CAST")
    return mapping.constructor.callBy(args) as T
}

fun <T : Any, V> T.update(property: KProperty1<T, V>, value: V): Job {
    val entity = this
    return dbScope.launch {
        query {
            val table = resolveTable(entity::class)
            val mapping =
                entityMappingCache.getOrPut(entity::class to table) {
                    buildEntityMapping(entity::class, table)
                }
            val pm =
                mapping.params.firstOrNull { it.param.name == property.name }
                    ?: error(
                        "No column mapped for '${property.name}' in table '${table.tableName}'"
                    )
            val encoded = coerceToColumn(pm.column, value)

            @Suppress("UNCHECKED_CAST")
            table.update({ entity.primaryKeyPredicate(table) }) {
                it[pm.column as Column<Any?>] = encoded
            }
        }
    }
}

suspend fun <T : Any> T.exists(): Boolean {
    val entity = this
    return query {
        val table = resolveTable(entity::class)
        table
            .selectAll()
            .where { entity.primaryKeyPredicate(table) }
            .limit(1)
            .any()
    }
}

suspend fun Table.exists(pk: Any): Boolean {
    val table = this
    return query {
        table.selectAll().where { table.singleKeyPredicate(pk) }.limit(1).any()
    }
}

suspend inline fun <reified T : Any> Table.find(pk: Any): T =
    primaryKeyRow(pk)?.toEntity(this)
        ?: throw NotFound(this::class.simpleName ?: "")

suspend fun Table.primaryKeyRow(pk: Any): ResultRow? {
    val table = this
    return query {
        table
            .selectAll()
            .where { table.singleKeyPredicate(pk) }
            .limit(1)
            .firstOrNull()
    }
}

private fun Table.singleKeyPredicate(pk: Any): Op<Boolean> {
    val pkColumns =
        primaryKey?.columns?.takeIf { it.isNotEmpty() }
            ?: error("Table '$tableName' has no primary key to match against")
    require(pkColumns.size == 1) {
        "a by-key lookup needs a single-column primary key, but '$tableName' has ${pkColumns.size}"
    }
    val pkColumn = pkColumns.single()

    @Suppress("UNCHECKED_CAST")
    return (pkColumn as Column<Any?>) eq coerceToColumn(pkColumn, pk)
}

private fun Any.primaryKeyPredicate(table: Table): Op<Boolean> {
    val pkColumns =
        table.primaryKey?.columns?.takeIf { it.isNotEmpty() }
            ?: error(
                "Table '${table.tableName}' has no primary key to match against"
            )

    val propsByName =
        this::class.memberProperties.associateBy { it.name.lowercase() }

    return pkColumns
        .map { column ->
            val prop =
                propsByName[column.name.snakeToCamel().lowercase()]
                    ?: error(
                        "${this::class.simpleName} has no property for primary key '${column.name}'"
                    )
            @Suppress("UNCHECKED_CAST")
            (column as Column<Any?>) eq
                coerceToColumn(column, (prop as KProperty1<Any, *>).get(this))
        }
        .reduce { acc, op -> acc and op }
}

private fun coerceToColumn(column: Column<*>, value: Any?): Any? {
    if (value !is String) return value
    val typeName = column.columnType::class.simpleName?.lowercase() ?: ""
    return when {
        "uuid" in typeName -> Uuid.parse(value)
        "long" in typeName -> value.toLong()
        "integer" in typeName -> value.toInt()
        else -> value
    }
}

private fun String.snakeToCamel(): String =
    split("_")
        .mapIndexed { i, s ->
            if (i == 0) s else s.replaceFirstChar { it.uppercase() }
        }
        .joinToString("")
