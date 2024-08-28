package org.example.autumn.db

import org.example.autumn.db.RowExtractor.Companion.getRowExtractor
import org.example.autumn.exception.DataAccessException
import java.sql.*
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

class JdbcTemplate(private val dataSource: DataSource) {
    inline fun <reified T> query(sql: String, vararg args: Any?): T? {
        return query(T::class.java.getRowExtractor(), sql, *args)
    }

    fun <T> query(rse: ResultSetExtractor<T>, sql: String, vararg args: Any?): T? {
        return execute(preparedStatementCreator(sql, *args)) { ps ->
            ps.executeQuery().use { rs -> if (!rs.next()) null else rse.extract(rs) }
        }
    }

    fun update(sql: String, vararg args: Any?): Int {
        return execute(preparedStatementCreator(sql, *args), PreparedStatement::executeUpdate)!!
    }

    fun batchInsert(sql: String, count: Int, vararg args: Any?): List<Long> {
        return execute({ conn ->
            @Suppress("SqlSourceToSinkFlow")
            conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).apply {
                val batchCount = args.size / count
                for (i in args.indices) {
                    val refIndex = i % batchCount
                    if (refIndex == 0 && i != 0) {
                        addBatch()
                    }
                    setObject(refIndex + 1, args[i])
                }
                addBatch()
            }
        }) { ps ->
            ps.executeBatch()
            buildList {
                ps.generatedKeys.use { keys ->
                    while (keys.next()) {
                        add(keys.getLong(1))
                    }
                }
            }
        }!!
    }

    fun insert(sql: String, vararg args: Any?): Long {
        return execute(preparedStatementCreator(sql, *args)) { ps ->
            val n = ps.executeUpdate()
            if (n == 0) {
                throw DataAccessException("0 rows inserted.")
            }
            if (n > 1) {
                throw DataAccessException("Multiple rows inserted.")
            }
            ps.generatedKeys.use { keys ->
                while (keys.next()) {
                    return@execute keys.getLong(1)
                }
            }
            throw DataAccessException("Should not reach here.")
        }!!
    }

    inline fun <reified T> querySingle(sql: String, vararg args: Any?): T {
        return querySingle(T::class.java.getRowExtractor(), sql, *args)
    }

    fun <T> querySingle(rse: ResultSetExtractor<T>, sql: String, vararg args: Any?): T {
        return execute(preparedStatementCreator(sql, *args)) { ps ->
            var ret: T?
            ps.executeQuery().use { rs ->
                if (!rs.next())
                    throw DataAccessException("Empty result set.")
                ret = rse.extract(rs)
                if (rs.next())
                    throw DataAccessException("Multiple rows found.")
            }
            ret
        }!!
    }

    inline fun <reified T> queryList(sql: String, vararg args: Any?): List<T> {
        return queryList(T::class.java.getRowExtractor(), sql, *args)
    }

    fun <T> queryList(rse: ResultSetExtractor<T>, sql: String, vararg args: Any?): List<T> {
        return execute(preparedStatementCreator(sql, *args)) { ps ->
            buildList {
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        add(rse.extract(rs)!!)
                    }
                }
            }
        }!!
    }

    fun <T> execute(psc: PreparedStatementCreator, callback: PreparedStatementCallback<T>): T? {
        val txConn = DataSourceTransactionManager.connection
        return try {
            if (txConn != null)
                psc.createPreparedStatement(txConn).use { ps -> callback.doWithPreparedStatement(ps) }
            else dataSource.connection.use { newConn ->
                val autoCommit = newConn.autoCommit
                if (!autoCommit) newConn.autoCommit = true
                val result = psc.createPreparedStatement(newConn).use { ps -> callback.doWithPreparedStatement(ps) }
                if (!autoCommit) newConn.autoCommit = false
                result
            }
        } catch (e: SQLException) {
            throw DataAccessException("Exception thrown while execute sql.", e)
        }
    }

    fun preparedStatementCreator(sql: String, vararg args: Any?): PreparedStatementCreator {
        return PreparedStatementCreator { conn ->
            conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).apply {
                for (i in args.indices)
                    setObject(i + 1, args[i])
            }
        }
    }
}

fun interface PreparedStatementCallback<T> {
    fun doWithPreparedStatement(ps: PreparedStatement): T?
}

fun interface PreparedStatementCreator {
    fun createPreparedStatement(con: Connection): PreparedStatement
}

fun interface ResultSetExtractor<T> {
    fun extract(rs: ResultSet): T?
}

fun interface ColumnExtractor<T> {
    fun extract(rs: ResultSet, label: String): T?
}

val COLUMN_EXTRACTORS = mapOf<Class<*>, ColumnExtractor<*>>(
    Boolean::class.java to ColumnExtractor { rs, label ->
        if (rs.getObject(label) == null) null else rs.getBoolean(label)
    },
    java.lang.Boolean::class.java to ColumnExtractor { rs, label ->
        if (rs.getObject(label) == null) null else rs.getBoolean(label)
    },
    Long::class.java to ColumnExtractor { rs, label ->
        if (rs.getObject(label) == null) null else rs.getLong(label)
    },
    java.lang.Long::class.java to ColumnExtractor { rs, label ->
        if (rs.getObject(label) == null) null else rs.getLong(label)
    },
    Int::class.java to ColumnExtractor { rs, label ->
        if (rs.getObject(label) == null) null else rs.getInt(label)
    },
    java.lang.Integer::class.java to ColumnExtractor { rs, label ->
        if (rs.getObject(label) == null) null else rs.getInt(label)
    },
    Short::class.java to ColumnExtractor { rs, label ->
        if (rs.getObject(label) == null) null else rs.getShort(label)
    },
    java.lang.Short::class.java to ColumnExtractor { rs, label ->
        if (rs.getObject(label) == null) null else rs.getShort(label)
    },
    Byte::class.java to ColumnExtractor { rs, label ->
        if (rs.getObject(label) == null) null else rs.getByte(label)
    },
    java.lang.Byte::class.java to ColumnExtractor { rs, label ->
        if (rs.getObject(label) == null) null else rs.getByte(label)
    },
    Float::class.java to ColumnExtractor { rs, label ->
        if (rs.getObject(label) == null) null else rs.getFloat(label)
    },
    java.lang.Float::class.java to ColumnExtractor { rs, label ->
        if (rs.getObject(label) == null) null else rs.getFloat(label)
    },
    Double::class.java to ColumnExtractor { rs, label ->
        if (rs.getObject(label) == null) null else rs.getDouble(label)
    },
    java.lang.Double::class.java to ColumnExtractor { rs, label ->
        if (rs.getObject(label) == null) null else rs.getDouble(label)
    },

    java.lang.String::class.java to ColumnExtractor { rs, label -> rs.getString(label) },
    String::class.java to ColumnExtractor { rs, label -> rs.getString(label) },
    Number::class.java to ColumnExtractor { rs, label -> rs.getObject(label) as Number? },
    ByteArray::class.java to ColumnExtractor { rs, label -> rs.getBytes(label) },
    Date::class.java to ColumnExtractor { rs, label -> rs.getDate(label) },
    Time::class.java to ColumnExtractor { rs, label -> rs.getTime(label) },
    Timestamp::class.java to ColumnExtractor { rs, label -> rs.getTimestamp(label) },
)

class RowExtractor<T> private constructor(private val clazz: Class<T>) : ResultSetExtractor<T> {
    companion object {
        // cache single elem row extractors
        private val ROW_EXTRACTOR_CACHE = ConcurrentHashMap<Class<*>, ResultSetExtractor<*>>().apply {
            put(Boolean::class.java, ResultSetExtractor {
                if (it.getObject(1) == null) null else it.getBoolean(1)
            })
            put(java.lang.Boolean::class.java, ResultSetExtractor {
                if (it.getObject(1) == null) null else it.getBoolean(1)
            })
            put(Long::class.java, ResultSetExtractor {
                if (it.getObject(1) == null) null else it.getLong(1)
            })
            put(java.lang.Long::class.java, ResultSetExtractor {
                if (it.getObject(1) == null) null else it.getLong(1)
            })
            put(Int::class.java, ResultSetExtractor {
                if (it.getObject(1) == null) null else it.getInt(1)
            })
            put(java.lang.Integer::class.java, ResultSetExtractor {
                if (it.getObject(1) == null) null else it.getInt(1)
            })
            put(Short::class.java, ResultSetExtractor {
                if (it.getObject(1) == null) null else it.getShort(1)
            })
            put(java.lang.Short::class.java, ResultSetExtractor {
                if (it.getObject(1) == null) null else it.getShort(1)
            })
            put(Byte::class.java, ResultSetExtractor {
                if (it.getObject(1) == null) null else it.getByte(1)
            })
            put(java.lang.Byte::class.java, ResultSetExtractor {
                if (it.getObject(1) == null) null else it.getByte(1)
            })
            put(Float::class.java, ResultSetExtractor {
                if (it.getObject(1) == null) null else it.getFloat(1)
            })
            put(java.lang.Float::class.java, ResultSetExtractor {
                if (it.getObject(1) == null) null else it.getFloat(1)
            })
            put(Double::class.java, ResultSetExtractor {
                if (it.getObject(1) == null) null else it.getDouble(1)
            })
            put(java.lang.Double::class.java, ResultSetExtractor {
                if (it.getObject(1) == null) null else it.getDouble(1)
            })

            put(java.lang.String::class.java, ResultSetExtractor { it.getString(1) })
            put(String::class.java, ResultSetExtractor { it.getString(1) })
            put(Number::class.java, ResultSetExtractor { it.getObject(1) as Number? })
            put(ByteArray::class.java, ResultSetExtractor { it.getBytes(1) })
            put(Date::class.java, ResultSetExtractor { it.getDate(1) })
            put(Time::class.java, ResultSetExtractor { it.getTime(1) })
            put(Timestamp::class.java, ResultSetExtractor { it.getTimestamp(1) })
        }

        @Suppress("UNCHECKED_CAST")
        fun <T> Class<T>.getRowExtractor(): ResultSetExtractor<T> {
            return when {
                Map::class.java.isAssignableFrom(this) -> {
                    require(this == Map::class.java) {
                        "query for plain map only support (Mutable)Map<*, *> return type"
                    }
                    ResultSetExtractor { rs ->
                        val md = rs.metaData
                        (1..md.columnCount).associate {
                            val colName = md.getColumnName(it)
                            colName to rs.getObject(colName)
                        }
                    } as ResultSetExtractor<T>
                }

                List::class.java.isAssignableFrom(this) -> {
                    require(this == List::class.java) {
                        "query for plain list only support (Mutable)List<*> return type"
                    }
                    ResultSetExtractor { rs ->
                        (1..rs.metaData.columnCount).map { rs.getObject(it) }
                    } as ResultSetExtractor<T>
                }

                else -> {
                    ROW_EXTRACTOR_CACHE.getOrPut(this) {
                        if (this.isEnum) ResultSetExtractor { rs ->
                            val value = rs.getString(1) ?: return@ResultSetExtractor null
                            this.enumConstants.first { (it as Enum<*>).name == value }
                        } else RowExtractor(this)
                    } as ResultSetExtractor<T>
                }
            }
        }
    }

    private val fields = clazz.declaredFields.associateBy { it.name }
    private val setters = clazz.methods
        .filter { it.parameters.size == 1 && it.name.length >= 4 && it.name.startsWith("set") }
        .associateBy { setter ->
            setter.name.substring(3).replaceFirstChar { it.lowercase() }
        }
    private val ctor = try {
        clazz.getConstructor()
    } catch (e: ReflectiveOperationException) {
        throw DataAccessException(
            "No public default constructor found for class ${clazz.name} when build BeanRowMapper.", e
        )
    }

    override fun extract(rs: ResultSet): T? {
        return ctor.newInstance().also { bean ->
            try {
                val meta = rs.metaData
                for (i in 1..meta.columnCount) {
                    val label = meta.getColumnLabel(i)
                    val setter = setters[label]
                    val field = fields[label]
                    when {
                        setter != null -> {
                            val type = setter.parameterTypes.first()
                            if (type.isEnum) {
                                val value = rs.getString(label)
                                setter.invoke(
                                    bean, if (value == null) null else
                                        type.enumConstants.first { (it as Enum<*>).name == value }
                                )
                            } else {
                                val extractor = COLUMN_EXTRACTORS[type]
                                setter.invoke(
                                    bean, if (extractor != null) extractor.extract(rs, label) else rs.getObject(label)
                                )
                            }
                        }

                        field != null -> {
                            val type = field.type
                            field.isAccessible = true
                            if (type.isEnum) {
                                val value = rs.getString(label)
                                field.set(bean, if (value == null) null else
                                    type.enumConstants.first { (it as Enum<*>).name == value })
                            } else {
                                val extractor = COLUMN_EXTRACTORS[type]
                                field.set(
                                    bean, if (extractor != null) extractor.extract(rs, label) else rs.getObject(label)
                                )
                            }
                        }

                        else -> throw IllegalArgumentException("cannot find setter or field on ${clazz.name} for label $label")
                    }
                }
            } catch (e: ReflectiveOperationException) {
                throw DataAccessException("Could not map result set to class ${clazz.name}", e)
            }
        }
    }
}