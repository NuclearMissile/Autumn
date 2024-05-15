package org.example.autumn.db

import org.example.autumn.exception.DataAccessException
import org.slf4j.LoggerFactory
import java.sql.*
import javax.sql.DataSource

class JdbcTemplate(private val dataSource: DataSource) {
    fun <T> query(sql: String, rse: ResultSetExtractor<T>, vararg args: Any?): T? {
        return execute(preparedStatementCreator(sql, *args)) { ps ->
            ps.executeQuery().use { rs -> rse.extractData(rs) }
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

    fun <T> queryRequiredObject(sql: String, clazz: Class<T>, vararg args: Any?): T {
        return when {
            String::class.java.isAssignableFrom(clazz) -> queryRequiredObject(sql, StringRowMapper.instance, *args) as T
            Boolean::class.java.isAssignableFrom(clazz) || clazz == Boolean::class.javaPrimitiveType ->
                queryRequiredObject(sql, BooleanRowMapper.instance, *args) as T

            Number::class.java.isAssignableFrom(clazz) || clazz.isPrimitive ->
                queryRequiredObject(sql, NumberRowMapper.instance, *args) as T

            else -> queryRequiredObject(sql, BeanRowMapper(clazz), *args)
        }
    }

    fun <T> queryRequiredObject(sql: String, rowMapper: ResultSetExtractor<T>, vararg args: Any?): T {
        return execute(preparedStatementCreator(sql, *args)) { ps ->
            var ret: T? = null
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    if (ret == null) {
                        ret = rowMapper.extractData(rs)
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

    fun <T> queryList(sql: String, clazz: Class<T>, vararg args: Any?): List<T> {
        return queryList(sql, BeanRowMapper(clazz), *args)
    }

    fun <T> queryList(sql: String, rowMapper: ResultSetExtractor<T>, vararg args: Any?): List<T> {
        return execute(preparedStatementCreator(sql, *args)) { ps ->
            buildList {
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        add(rowMapper.extractData(rs)!!)
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
    fun extractData(rs: ResultSet): T?
}

class BeanRowMapper<T>(private val clazz: Class<T>) : ResultSetExtractor<T> {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val fields = clazz.fields.associateBy {
        logger.atDebug().log("Add row mapping for {}", it.name)
        it.name
    }
    private val setters = clazz.methods
        .filter { it.parameters.size == 1 && it.name.length >= 4 && it.name.startsWith("set") }
        .associateBy {
            val name = it.name
            val prop = name[3].lowercaseChar() + name.substring(4)
            logger.atDebug()
                .log("Add row mapping: {} to {}({})", prop, name, it.parameters.single().type.simpleName)
            prop
        }
    private val ctor = try {
        clazz.getConstructor()
    } catch (e: ReflectiveOperationException) {
        throw DataAccessException(
            "No public default constructor found for class ${clazz.name} when build BeanRowMapper.", e
        )
    }

    override fun extractData(rs: ResultSet): T? {
        return ctor.newInstance().also { bean ->
            try {
                val meta = rs.metaData
                for (i in 1..meta.columnCount) {
                    val label = meta.getColumnLabel(i)
                    val setter = setters[label]
                    if (setter != null) {
                        setter.invoke(bean, rs.getObject(label))
                    } else {
                        val field = fields[label]
                        field?.set(bean, rs.getObject(label))
                    }
                }
            } catch (e: ReflectiveOperationException) {
                throw DataAccessException("Could not map result set to class ${clazz.name}", e)
            }
        }
    }
}

class StringRowMapper : ResultSetExtractor<String> {
    companion object {
        val instance = StringRowMapper()
    }

    override fun extractData(rs: ResultSet): String? {
        return rs.getString(1)
    }
}

class BooleanRowMapper : ResultSetExtractor<Boolean> {
    companion object {
        val instance = BooleanRowMapper()
    }

    override fun extractData(rs: ResultSet): Boolean {
        return rs.getBoolean(1)
    }
}

class NumberRowMapper : ResultSetExtractor<Number> {
    companion object {
        val instance = NumberRowMapper()
    }

    override fun extractData(rs: ResultSet): Number? {
        return rs.getObject(1) as? Number
    }
}