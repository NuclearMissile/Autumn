package org.example.autumn.db

import jakarta.persistence.*
import java.lang.reflect.Field
import java.math.BigDecimal
import java.sql.Date
import java.sql.ResultSet
import java.sql.Time
import java.sql.Timestamp

/**
 * Represent an entity's field with @Column annotation.
 */
class EntityProperty(val field: Field) {
    private val colAnno = field.getAnnotation(Column::class.java)!!

    // db col name
    val colName: String = colAnno.name.ifEmpty { field.name }
    val isId = field.getAnnotation(Id::class.java) != null
    val isGeneratedId = if (!isId) false else
        field.getAnnotation(GeneratedValue::class.java)?.strategy == GenerationType.IDENTITY
    val insertable = if (isGeneratedId) false else colAnno.insertable
    val updatable = if (isId) false else colAnno.updatable

    // table column definition:
    val columnDefinition by lazy {
        StringBuilder(128).also {
            if (colAnno.columnDefinition.isEmpty()) {
                it.append(getColumnType(field.type, colAnno))
            } else {
                it.append(colAnno.columnDefinition.uppercase())
            }

            it.append(if (colAnno.nullable) " NULL" else " NOT NULL")
            if (isGeneratedId) {
                it.append(" AUTO_INCREMENT")
            }
            if (!isId && colAnno.unique) {
                it.append(" UNIQUE")
            }
        }.toString()
    }

    init {
        field.isAccessible = true
    }

    operator fun get(entity: Any): Any? {
        val value = field[entity] ?: return null
        return if (field.type.isEnum) (value as Enum<*>).name else value
    }

    operator fun set(entity: Any, value: Any?) {
        field[entity] = when {
            value == null -> null
            field.type.isEnum -> field.type.enumConstants.first { (it as Enum<*>).name == value as String }
            else -> value
        }
    }

    override fun toString(): String {
        return "EntityProperty(field=$field, type=${field.type}, fieldName='${field.name}', colName='$colName')"
    }

    companion object {
        private fun getColumnType(type: Class<*>, col: Column?): String {
            if (type.isEnum) return "VARCHAR(32)"
            var ddl = DEFAULT_COLUMN_TYPES[type]!!
            if (ddl == "VARCHAR($1)") {
                ddl = ddl.replace("$1", (col?.length ?: 255).toString())
            }
            if (ddl == "DECIMAL($1,$2)") {
                val precision = col?.precision ?: 10
                val scale = col?.scale ?: 2
                ddl = ddl.replace("$1", precision.toString()).replace("$2", scale.toString())
            }
            return ddl
        }

        private val DEFAULT_COLUMN_TYPES = mapOf(
            String::class.java to "VARCHAR($1)",
            java.lang.String::class.java to "VARCHAR($1)",
            Boolean::class.java to "BIT",
            java.lang.Boolean::class.java to "BIT",
            Byte::class.java to "BIT",
            java.lang.Byte::class.java to "BIT",
            Short::class.java to "TINYINT",
            java.lang.Short::class.java to "TINYINT",
            Int::class.java to "INTEGER",
            java.lang.Integer::class.java to "INTEGER",
            Long::class.java to "BIGINT",
            java.lang.Long::class.java to "BIGINT",
            Float::class.java to "REAL",
            java.lang.Float::class.java to "REAL",
            Double::class.java to "DOUBLE",
            java.lang.Double::class.java to "DOUBLE",
            BigDecimal::class.java to "DECIMAL($1,$2)",
            ByteArray::class.java to "BLOB",
            Timestamp::class.java to "TIMESTAMP",
            Time::class.java to "TIMESTAMP",
            Date::class.java to "DATE",
        )
    }
}

class EntityMapper<T>(private val entityClass: Class<T>) : ResultSetExtractor<T> {
    private val properties = scanProperties(entityClass)

    // db col name -> EntityProperty
    private val propertiesMap by lazy { properties.associateBy { it.colName } }

    val tableName = entityClass.getAnnotation(Table::class.java).name
    val id = properties.filter(EntityProperty::isId).also {
        require(it.count() == 1) { throw RuntimeException("Require exact one @Id for class ${entityClass.name}") }
    }.first()
    val insertableProperties by lazy { properties.filter(EntityProperty::insertable) }
    val updatableProperties by lazy { properties.filter(EntityProperty::updatable) }

    val selectSQL by lazy { "SELECT * FROM $tableName WHERE ${id.colName} = ?" }
    val insertSQL by lazy {
        "INSERT INTO $tableName (${insertableProperties.joinToString { it.colName }}) VALUES (${
            List(insertableProperties.count()) { "?" }.joinToString()
        })"
    }
    val updateSQL by lazy {
        "UPDATE $tableName SET ${updatableProperties.joinToString { "${it.colName} = ?" }} WHERE ${id.colName} = ?"
    }
    val deleteSQL by lazy { "DELETE FROM $tableName WHERE ${id.colName} = ?" }
    val ddl by lazy {
        StringBuilder(256).also { sb ->
            sb.append("CREATE TABLE $tableName (\n")
            sb.append(properties.joinToString(separator = ",\n", postfix = ",\n") {
                "  ${it.colName} ${it.columnDefinition}"
            })
            sb.append(uniqueKey)
            sb.append(index)
            sb.append("  PRIMARY KEY(${id.colName})\n")
            sb.append(") CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci AUTO_INCREMENT = 1000;\n")
        }.toString()
    }

    val listExtractor = ResultSetExtractor { rs ->
        val colNames = properties.indices.map { rs.metaData.getColumnLabel(it + 1) }
        buildList {
            while (rs.next()) {
                val entity = entityClass.getConstructor().newInstance()
                for (i in properties.indices) {
                    val colName = colNames[i]
                    val prop = propertiesMap[colName]!!
                    val extractor = COLUMN_EXTRACTORS[prop.field.type]
                    prop[entity as Any] = if (extractor != null)
                        extractor.extract(rs, colName) else rs.getObject(colName)
                }
                add(entity)
            }
        }
    }

    override fun extract(rs: ResultSet): T? {
        return listExtractor.extract(rs)!!.firstOrNull()
    }

    fun idOf(entity: Any): Any {
        return id[entity]!!
    }

    private val uniqueKey by lazy {
        val table = entityClass.getAnnotation(Table::class.java) ?: return@lazy ""
        table.uniqueConstraints.map { c ->
            val name = c.name.ifEmpty { "UNI_${c.columnNames.joinToString("_")}" }
            return@map "  CONSTRAINT $name UNIQUE (${c.columnNames.joinToString()}),\n"
        }.fold("", String::plus)
    }

    private val index by lazy {
        val table = entityClass.getAnnotation(Table::class.java) ?: return@lazy ""
        table.indexes.map {
            if (it.unique) {
                val name = it.name.ifEmpty {
                    "UNI_${it.columnList.replace(" ", "").replace(",", "_")}"
                }
                "  CONSTRAINT $name UNIQUE(${it.columnList}),\n"
            } else {
                val name = it.name.ifEmpty {
                    "IDX_${it.columnList.replace(" ", "").replace(",", "_")}"
                }
                "  INDEX $name (${it.columnList}),\n"
            }
        }.fold("", String::plus)
    }

    private fun scanProperties(
        clazz: Class<*>, foundProps: MutableList<EntityProperty> = ArrayList(8),
    ): List<EntityProperty> {
        if (clazz == Any::class.java) {
            return foundProps.sortedWith { o1, o2 ->
                when {
                    o1.isId -> -1
                    o2.isId -> 1
                    else -> o1.colName.compareTo(o2.colName)
                }
            }
        }
        foundProps += clazz.declaredFields
            .filter { it.isAnnotationPresent(Column::class.java) }
            .map { EntityProperty(it) }

        return scanProperties(clazz.superclass, foundProps)
    }
}
