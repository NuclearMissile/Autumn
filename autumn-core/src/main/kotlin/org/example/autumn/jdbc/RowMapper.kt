package org.example.autumn.jdbc

import org.example.autumn.exception.DataAccessException
import org.slf4j.LoggerFactory
import java.sql.ResultSet

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