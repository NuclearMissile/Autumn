package org.example.autumn.orm

import jakarta.persistence.Entity
import org.example.autumn.jdbc.JdbcTemplate
import org.example.autumn.utils.ClassUtils.findAnnotation
import org.example.autumn.utils.ClassUtils.scanClassNames
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigInteger

interface EntityMixin {
    companion object {
        /**
         * Default big decimal storage type: DECIMAL(PRECISION, SCALE)
         *
         * Range = +/-999999999999999999.999999999999999999
         */
        const val PRECISION = 36

        /**
         * Default big decimal storage scale. Minimum is 0.000000000000000001.
         */
        const val SCALE = 18
        const val VAR_ENUM = 32
        const val VAR_CHAR_50 = 50
        const val VAR_CHAR_100 = 100
        const val VAR_CHAR_200 = 200
        const val VAR_CHAR_1000 = 1000
        const val VAR_CHAR_10000 = 10000
    }
}

class DbTemplate(val jdbcTemplate: JdbcTemplate, private val entityPackagePath: String) {
    val logger: Logger = LoggerFactory.getLogger(javaClass)

    private val classMapping by lazy {
        scanClassNames(listOf(entityPackagePath)).map { Class.forName(it) }.filter {
            findAnnotation(it, Entity::class.java) != null
        }.associateWith { Mapper(it) }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : EntityMixin> mapperOf(clazz: Class<T>): Mapper<T> {
        return classMapping[clazz] as? Mapper<T>
            ?: throw IllegalArgumentException("Target class is not a registered entity: ${clazz.name}")
    }

    fun exportDDL(): String {
        return classMapping.values.sortedBy { it.tableName }.joinToString("\n\n") { it.ddl }
    }

    inline fun <reified T : EntityMixin> selectById(id: Any): T? {
        val mapper = mapperOf(T::class.java)
        if (logger.isDebugEnabled) {
            logger.debug("selectById SQL: {}, args: {}", mapper.selectSQL, id)
        }
        return jdbcTemplate.query(mapper.selectSQL, mapper.resultSetExtractor, id)?.firstOrNull()
    }

    inline fun <reified T : EntityMixin> selectFrom(distinct: Boolean = false): SelectFrom<T> {
        return SelectFrom(Criteria(this, mapperOf(T::class.java)), distinct)
    }

    fun <T : EntityMixin> delete(clazz: Class<T>, entity: T) {
        val mapper = mapperOf(clazz)
        val id = mapper.idOf(entity)
        if (logger.isDebugEnabled) {
            logger.debug("delete SQL: {}, args: {}", mapper.deleteSQL, id)
        }
        jdbcTemplate.update(mapper.deleteSQL, id)
    }

    fun <T : EntityMixin> deleteById(clazz: Class<T>, id: Any) {
        val mapper = mapperOf(clazz)
        if (logger.isDebugEnabled) {
            logger.debug("deleteById SQL: {}, args: {}", mapper.deleteSQL, id)
        }
        jdbcTemplate.update(mapper.deleteSQL, id)
    }

    fun <T : EntityMixin> update(clazz: Class<T>, entity: T) {
        val mapper = mapperOf(clazz)
        require(mapper.updatableProperties.isNotEmpty()) { "entity must have at least 1 updatable property" }
        val args = mapper.updatableProperties.map { it[entity] } + mapper.id[entity]
        if (logger.isDebugEnabled) {
            logger.debug("update SQL: {}, args: {}", mapper.updateSQL, args)
        }
        jdbcTemplate.update(mapper.updateSQL, *args.toTypedArray())
    }


    fun <T : EntityMixin> batchInsert(clazz: Class<T>, entities: List<T>, ignore: Boolean = false) {
        entities.forEach { insert(clazz, it, ignore) }
    }

    fun <T : EntityMixin> insert(clazz: Class<T>, entity: T, ignore: Boolean = false) {
        val mapper = mapperOf(clazz)
        val args = mapper.insertableProperties.map { it[entity] }.toTypedArray()
        val sql = if (ignore) mapper.insertIgnoreSQL else mapper.insertSQL
        if (logger.isDebugEnabled) {
            logger.debug("insert SQL: {}, args: {}", sql, args)
        }
        if (mapper.id.isGeneratedId) {
            val key = jdbcTemplate.updateWithGeneratedKey(sql, *args)
            mapper.id[entity] = if (key is BigInteger) key.longValueExact() else key
        } else {
            jdbcTemplate.update(sql, *args)
        }
    }
}