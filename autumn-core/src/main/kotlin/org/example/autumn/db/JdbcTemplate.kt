package org.example.autumn.db

import org.example.autumn.db.RowMapper.Companion.getResultSetExtractor
import org.example.autumn.exception.DataAccessException
import java.sql.*
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

class JdbcTemplate(private val dataSource: DataSource) {
    inline fun <reified T> query(sql: String, vararg args: Any?): T? {
        return query(sql, T::class.java.getResultSetExtractor(), *args)
    }

    fun <T> query(sql: String, rse: ResultSetExtractor<T>, vararg args: Any?): T? {
        return execute(preparedStatementCreator(sql, *args)) { ps ->
            ps.executeQuery().use { rs -> rse.extract(rs) }
        }
    }

    fun update(sql: String, vararg args: Any?): Int {
        return execute(preparedStatementCreator(sql, *args), PreparedStatement::executeUpdate)!!
    }

    fun batchInsert(sql: String, count: Int, vararg args: Any?): List<Number> {
        return execute({ conn ->
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
                        add(keys.getObject(1) as Number)
                    }
                }
            }
        }!!
    }

    fun updateWithGeneratedKey(sql: String, vararg args: Any?): Number {
        return execute(preparedStatementCreator(sql, *args)) { ps: PreparedStatement ->
            val n = ps.executeUpdate()
            if (n == 0) {
                throw DataAccessException("0 rows inserted.")
            }
            if (n > 1) {
                throw DataAccessException("Multiple rows inserted.")
            }
            ps.generatedKeys.use { keys ->
                while (keys.next()) {
                    return@execute keys.getObject(1) as Number
                }
            }
            throw DataAccessException("Should not reach here.")
        }!!
    }

    inline fun <reified T> queryRequired(sql: String, vararg args: Any?): T {
        return queryRequired(sql, T::class.java.getResultSetExtractor(), *args)
    }

    fun <T> queryRequired(sql: String, rse: ResultSetExtractor<T>, vararg args: Any?): T {
        return execute(preparedStatementCreator(sql, *args)) { ps ->
            var ret: T? = null
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    if (ret == null) {
                        ret = rse.extract(rs)
                    } else {
                        throw DataAccessException("Multiple rows found.")
                    }
                }
            }
            if (ret == null) {
                throw DataAccessException("Empty result set.")
            }
            return@execute ret
        }!!
    }

    inline fun <reified T> queryList(sql: String, vararg args: Any?): List<T> {
        return queryList(sql, T::class.java.getResultSetExtractor(), *args)
    }

    fun <T> queryList(sql: String, rse: ResultSetExtractor<T>, vararg args: Any?): List<T> {
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
        return executeWithTx { conn ->
            psc.createPreparedStatement(conn).use { ps -> callback.doInPreparedStatement(ps) }
        }
    }

    fun <T> executeWithTx(callback: ConnectionCallback<T>): T? {
        val txConn = DataSourceTransactionManager.connection
        return try {
            if (txConn != null)
                callback.doInConnection(txConn)
            else dataSource.connection.use { newConn ->
                val autoCommit = newConn.autoCommit
                if (!autoCommit) newConn.autoCommit = true
                val result = callback.doInConnection(newConn)
                if (!autoCommit) newConn.autoCommit = false
                result
            }
        } catch (e: SQLException) {
            throw DataAccessException("Exception thrown while execute sql.", e)
        }
    }

    private fun preparedStatementCreator(sql: String, vararg args: Any?): PreparedStatementCreator {
        return PreparedStatementCreator { conn ->
            conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).apply {
                for (i in args.indices)
                    setObject(i + 1, args[i])
            }
        }
    }
}

fun interface ConnectionCallback<T> {
    fun doInConnection(conn: Connection): T?
}

fun interface PreparedStatementCallback<T> {
    fun doInPreparedStatement(ps: PreparedStatement): T?
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

class RowMapper<T> private constructor(private val clazz: Class<T>) : ResultSetExtractor<T> {
    companion object {
        private val rseCache = ConcurrentHashMap<Class<*>, ResultSetExtractor<*>>().apply {
            put(Boolean::class.java, ResultSetExtractor { it.getBoolean(1) })
            put(java.lang.Boolean::class.java, ResultSetExtractor { it.getBoolean(1) })
            put(Long::class.java, ResultSetExtractor { it.getLong(1) })
            put(java.lang.Long::class.java, ResultSetExtractor { it.getLong(1) })
            put(Int::class.java, ResultSetExtractor { it.getInt(1) })
            put(java.lang.Integer::class.java, ResultSetExtractor { it.getInt(1) })
            put(Short::class.java, ResultSetExtractor { it.getShort(1) })
            put(java.lang.Short::class.java, ResultSetExtractor { it.getShort(1) })
            put(Byte::class.java, ResultSetExtractor { it.getByte(1) })
            put(java.lang.Byte::class.java, ResultSetExtractor { it.getByte(1) })
            put(Float::class.java, ResultSetExtractor { it.getFloat(1) })
            put(java.lang.Float::class.java, ResultSetExtractor { it.getFloat(1) })
            put(Double::class.java, ResultSetExtractor { it.getDouble(1) })
            put(java.lang.Double::class.java, ResultSetExtractor { it.getDouble(1) })

            put(String::class.java, ResultSetExtractor { it.getString(1) })
            put(Number::class.java, ResultSetExtractor { it.getObject(1) as? Number })
            put(ByteArray::class.java, ResultSetExtractor { it.getBytes(1) })
            put(Date::class.java, ResultSetExtractor { it.getDate(1) })
            put(Time::class.java, ResultSetExtractor { it.getTime(1) })
            put(Timestamp::class.java, ResultSetExtractor { it.getTimestamp(1) })
            put(Blob::class.java, ResultSetExtractor { it.getBlob(1) })
            put(RowId::class.java, ResultSetExtractor { it.getRowId(1) })
        }

        private val ceCache = mapOf<Class<*>, ColumnExtractor<*>>(
            Boolean::class.java to ColumnExtractor { rs, label -> rs.getBoolean(label) },
            java.lang.Boolean::class.java to ColumnExtractor { rs, label -> rs.getBoolean(label) },
            Long::class.java to ColumnExtractor { rs, label -> rs.getLong(label) },
            java.lang.Long::class.java to ColumnExtractor { rs, label -> rs.getLong(label) },
            Int::class.java to ColumnExtractor { rs, label -> rs.getInt(label) },
            java.lang.Integer::class.java to ColumnExtractor { rs, label -> rs.getInt(label) },
            Short::class.java to ColumnExtractor { rs, label -> rs.getShort(label) },
            java.lang.Short::class.java to ColumnExtractor { rs, label -> rs.getShort(label) },
            Byte::class.java to ColumnExtractor { rs, label -> rs.getByte(label) },
            java.lang.Byte::class.java to ColumnExtractor { rs, label -> rs.getByte(label) },
            Float::class.java to ColumnExtractor { rs, label -> rs.getFloat(label) },
            java.lang.Float::class.java to ColumnExtractor { rs, label -> rs.getFloat(label) },
            Double::class.java to ColumnExtractor { rs, label -> rs.getDouble(label) },
            java.lang.Double::class.java to ColumnExtractor { rs, label -> rs.getDouble(label) },

            String::class.java to ColumnExtractor { rs, label -> rs.getString(label) },
            Number::class.java to ColumnExtractor { rs, label -> rs.getObject(label) as? Number },
            ByteArray::class.java to ColumnExtractor { rs, label -> rs.getBytes(label) },
            Date::class.java to ColumnExtractor { rs, label -> rs.getDate(label) },
            Time::class.java to ColumnExtractor { rs, label -> rs.getTime(label) },
            Timestamp::class.java to ColumnExtractor { rs, label -> rs.getTimestamp(label) },
            Blob::class.java to ColumnExtractor { rs, label -> rs.getBlob(label) },
            RowId::class.java to ColumnExtractor { rs, label -> rs.getRowId(label) },
        )

        fun <T> Class<T>.getResultSetExtractor(): ResultSetExtractor<T> {
            return rseCache.getOrPut(this) { RowMapper(this) } as ResultSetExtractor<T>
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
                            setter.invoke(
                                bean, ceCache[setter.parameterTypes.first()]?.extract(rs, label) ?: rs.getObject(label)
                            )
                        }

                        field != null -> {
                            field.isAccessible = true
                            field.set(
                                bean, ceCache[field.type]?.extract(rs, label) ?: rs.getObject(label)
                            )
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