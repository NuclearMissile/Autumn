package com.example.autumn.jdbc

import com.example.autumn.exception.DataAccessException
import org.slf4j.LoggerFactory
import java.sql.ResultSet

fun interface RowMapper<T> {
    fun mapRow(rs: ResultSet, rowNum: Int): T?
}

class BeanRowMapper<T>(private val clazz: Class<T>) : RowMapper<T> {
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

    override fun mapRow(rs: ResultSet, rowNum: Int): T {
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

class StringRowMapper : RowMapper<String> {
    override fun mapRow(rs: ResultSet, rowNum: Int): String? {
        return rs.getString(1)
    }

    companion object {
        val instance = StringRowMapper()
    }
}

class BooleanRowMapper : RowMapper<Boolean> {
    override fun mapRow(rs: ResultSet, rowNum: Int): Boolean {
        return rs.getBoolean(1)
    }

    companion object {
        val instance = BooleanRowMapper()
    }
}

class NumberRowMapper : RowMapper<Number> {
    override fun mapRow(rs: ResultSet, rowNum: Int): Number? {
        return rs.getObject(1) as? Number
    }

    companion object {
        val instance = NumberRowMapper()
    }
}