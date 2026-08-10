package com.socialcoding.api.db

import com.socialcoding.api.Environment
import com.socialcoding.api.Initializable
import com.socialcoding.api.Initialize
import com.socialcoding.api.db.Database.buildEntityMapping
import com.socialcoding.api.db.Database.coerceToColumn
import com.socialcoding.api.db.Database.entityMappingCache
import com.socialcoding.api.db.Database.resolveTable
import com.socialcoding.api.db.Database.scope
import com.socialcoding.camelCase
import com.socialcoding.common.NotFound
import com.socialcoding.projects.docs.backfillInitialDesignDocs
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
import org.reflections.Reflections
import org.slf4j.LoggerFactory

/** Handles the database connection. */
@Initialize
object Database : Initializable {
    private val LOGGER = LoggerFactory.getLogger("Database")

    data class EntityMapping(
        val params: List<ParamMapping>,
        val constructor: KFunction<Any>,
    )

    data class ParamMapping(
        val param: KParameter,
        val column: Column<*>,
        val isJsonCollection: Boolean,
    )

    val tableAnnotationCache = ConcurrentHashMap<KClass<*>, Table>()
    val entityMappingCache =
        ConcurrentHashMap<Pair<KClass<*>, Table>, EntityMapping>()
    /**
     * Every [SqlTable]-annotated table, ordered so each follows the ones it references — the order
     * schema creation needs. Reverse it for FK-safe deletion, as the test schema does.
     *
     * Scanning the classpath isn't cheap and the answer can't change at runtime, so it's done once.
     */
    val tables: List<Table> by lazy {
        val found =
            Reflections("com.socialcoding")
                .getSubTypesOf(Table::class.java)
                .filter { it.isAnnotationPresent(SqlTable::class.java) }
                .map { clazz ->
                    clazz.kotlin.objectInstance
                        ?: error(
                            "@SqlTable ${clazz.simpleName} must be an object"
                        )
                }
                // Stable input order, so the sort below is reproducible.
                .sortedBy { it.tableName }

        // Finding nothing would look like a healthy boot and leave the database empty.
        check(found.isNotEmpty()) {
            "No @SqlTable tables found under com.socialcoding — the schema would be left empty"
        }

        SchemaUtils.sortTablesByReferences(found)
    }

    fun coerceToColumn(column: Column<*>, value: Any?): Any? {
        if (value !is String) return value
        val typeName = column.columnType::class.simpleName?.lowercase() ?: ""
        return when {
            "uuid" in typeName -> Uuid.parse(value)
            "long" in typeName -> value.toLong()
            "integer" in typeName -> value.toInt()
            else -> value
        }
    }

    fun resolveTable(kClass: KClass<*>): Table =
        tableAnnotationCache.getOrPut(kClass) {
            val annotation =
                kClass.findAnnotation<MappedTable>()
                    ?: error("${kClass.simpleName} is not properly mapped.")

            annotation.table.objectInstance
                ?: error(
                    "@MappedTable table for ${kClass.simpleName} must be an object"
                )
        }

    val scope =
        CoroutineScope(
            Dispatchers.IO +
                SupervisorJob() +
                CoroutineExceptionHandler { _, e ->
                    LOGGER.error("Async DB write failed", e)
                }
        )

    fun buildEntityMapping(kClass: KClass<*>, table: Table): EntityMapping {
        val constructor =
            kClass.primaryConstructor
                ?: error("${kClass.simpleName} has no primary constructor")

        val columnsByCamel =
            table.columns.associateBy { it.name.camelCase.lowercase() }
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
                    if (param.isOptional) return@mapNotNull null
                    error(
                        "No column found for parameter '${param.name}' in table '${table.tableName}'"
                    )
                }
                val isJsonCollection =
                    param.type.classifier in
                        listOf(
                            List::class,
                            Set::class,
                            Map::class,
                            HashMap::class,
                        )
                ParamMapping(param, column, isJsonCollection)
            }

        return EntityMapping(params, constructor)
    }

    override suspend fun initialize() {
        Database.connect(
            Environment.getVariable("DB_URL"),
            user = Environment.getVariable("DB_USER"),
            password = Environment.getVariable("DB_PASS"),
        )

        query {
            SchemaUtils.createMissingTablesAndColumns(*tables.toTypedArray())
            // The schema comes from the table objects rather than migrations, so the one data
            // migration that couldn't ride along with it runs here, right after the tables exist.
            backfillInitialDesignDocs()
        }

        LOGGER.info(
            "Schema ready for {} tables: {}",
            tables.size,
            tables.joinToString { it.tableName },
        )
    }
}

/** Run [block] in a coroutine. */
suspend fun <T> query(block: JdbcTransaction.() -> T): T =
    withContext(Dispatchers.IO) { transaction { block() } }

/** Turn a row into [T]. */
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

    return scope.launch {
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

/** Check if [T] exists. */
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

/** Check if [pk] exists. */
suspend fun Table.exists(pk: Any): Boolean {
    val table = this

    return query {
        table.selectAll().where { table.singleKeyPredicate(pk) }.limit(1).any()
    }
}

/** Find [pk]. */
suspend inline fun <reified T : Any> Table.find(pk: Any): T =
    primaryKeyRow(pk)?.toEntity(this)
        ?: throw NotFound(this::class.simpleName ?: "")

/** Find the primary key row. */
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
        "a by-key lookup needs a single-column primary key."
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
                propsByName[column.name.camelCase.lowercase()]
                    ?: error(
                        "${this::class.simpleName} has no property for primary key '${column.name}'"
                    )
            @Suppress("UNCHECKED_CAST")
            (column as Column<Any?>) eq
                coerceToColumn(column, (prop as KProperty1<Any, *>).get(this))
        }
        .reduce { acc, op -> acc and op }
}
