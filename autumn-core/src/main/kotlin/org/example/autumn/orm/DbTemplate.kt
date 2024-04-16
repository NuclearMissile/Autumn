package org.example.autumn.orm

import jakarta.persistence.Entity
import org.example.autumn.jdbc.JdbcTemplate
import org.example.autumn.utils.ClassUtils.findAnnotation
import org.example.autumn.utils.ClassUtils.scanClassNames
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigInteger

class DbTemplate(val jdbcTemplate: JdbcTemplate, private val entityPackagePath: String) {
    val logger: Logger = LoggerFactory.getLogger(javaClass)

    private val classMapping by lazy {
        scanClassNames(listOf(entityPackagePath)).map { Class.forName(it) }.filter {
            findAnnotation(it, Entity::class.java) != null
        }.associateWith { Mapper(it) }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> mapperOf(clazz: Class<T>): Mapper<T> {
        return classMapping[clazz] as? Mapper<T>
            ?: throw IllegalArgumentException("Target class is not a registered entity: ${clazz.name}")
    }

    fun exportDDL(): String {
        return classMapping.values.sortedBy { it.tableName }.joinToString("\n\n") { it.ddl }
    }

    inline fun <reified T> selectById(id: Any): T? {
        val mapper = mapperOf(T::class.java)
        if (logger.isDebugEnabled) {
            logger.debug("selectById SQL: {}, args: {}", mapper.selectSQL, id)
        }
        return jdbcTemplate.query(mapper.selectSQL, mapper.resultSetExtractor, id)?.firstOrNull()
    }

    inline fun <reified T> selectFrom(distinct: Boolean = false): SelectFrom<T> {
        return SelectFrom(Criteria(this, mapperOf(T::class.java)), distinct)
    }

    fun <T> delete(clazz: Class<T>, entity: T) {
        val mapper = mapperOf(clazz)
        val id = mapper.idOf(entity as Any)
        if (logger.isDebugEnabled) {
            logger.debug("delete SQL: {}, args: {}", mapper.deleteSQL, id)
        }
        jdbcTemplate.update(mapper.deleteSQL, id)
    }

    fun <T> deleteById(clazz: Class<T>, id: Any) {
        val mapper = mapperOf(clazz)
        if (logger.isDebugEnabled) {
            logger.debug("deleteById SQL: {}, args: {}", mapper.deleteSQL, id)
        }
        jdbcTemplate.update(mapper.deleteSQL, id)
    }

    fun <T> update(clazz: Class<T>, entity: T) {
        val mapper = mapperOf(clazz)
        require(mapper.updatableProperties.isNotEmpty()) { "entity must have at least 1 updatable property" }
        val args = mapper.updatableProperties.map { it[entity as Any] } + mapper.id[entity as Any]
        if (logger.isDebugEnabled) {
            logger.debug("update SQL: {}, args: {}", mapper.updateSQL, args)
        }
        jdbcTemplate.update(mapper.updateSQL, *args.toTypedArray())
    }


    fun <T> batchInsert(clazz: Class<T>, entities: List<T>, ignore: Boolean = false) {
        val mapper = mapperOf(clazz)
        val sql = if (ignore) mapper.insertIgnoreSQL else mapper.insertSQL
        logger.atDebug().log("batch insert SQL: {}, count: {}", sql, entities.size)
        entities.forEach { insert(clazz, it, ignore, false) }

//        val mapper = mapperOf(clazz)
//        val sql = if (ignore) mapper.insertIgnoreSQL else mapper.insertSQL
//        val args = entities.flatMap { entity -> mapper.insertableProperties.map { it[entity as Any] } }.toTypedArray()
//        logger.atDebug().log("batch insert SQL: {}, count: {}", sql, entities.size)
//
//        val keys = jdbcTemplate.batchInsert(sql, entities.size, *args)
//        if (mapper.id.isGeneratedId) {
//            keys.forEachIndexed { index, key ->
//                mapper.id[entities[index] as Any] = if (key is BigInteger) key.longValueExact() else key
//            }
//        }
    }

    fun <T> insert(clazz: Class<T>, entity: T, ignore: Boolean = false, logging: Boolean = true) {
        val mapper = mapperOf(clazz)
        val args = mapper.insertableProperties.map { it[entity as Any] }.toTypedArray()
        val sql = if (ignore) mapper.insertIgnoreSQL else mapper.insertSQL

        if (logging) {
            logger.atDebug().log("insert SQL: {}, args: {}", sql, args)
        }
        if (mapper.id.isGeneratedId) {
            val key = jdbcTemplate.updateWithGeneratedKey(sql, *args)
            mapper.id[entity as Any] = if (key is BigInteger) key.longValueExact() else key
        } else {
            jdbcTemplate.update(sql, *args)
        }
    }
}