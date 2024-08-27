package org.example.autumn.db

import java.sql.Date
import java.sql.ResultSet
import java.sql.Time
import java.sql.Timestamp
import java.util.concurrent.ConcurrentHashMap

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
                } else EntityMapper(this)
            } as ResultSetExtractor<T>
        }
    }
}