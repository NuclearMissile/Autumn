package org.example.autumn.db.orm

import jakarta.persistence.Entity
import org.example.autumn.context.ApplicationContextHolder
import org.example.autumn.db.JdbcTemplate
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class NaiveOrm(val jdbcTemplate: JdbcTemplate) {
    val logger: Logger = LoggerFactory.getLogger(javaClass)

    private val classMapping by lazy {
        ApplicationContextHolder.required.managedClassNames
            .map { Class.forName(it, true, Thread.currentThread().contextClassLoader) }
            .filter { it.isAnnotationPresent(Entity::class.java) }
            .associateWith { EntityMapper(it) }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> mapperOf(clazz: Class<T>): EntityMapper<T> {
        return classMapping[clazz] as? EntityMapper<T>
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
        return jdbcTemplate.queryForOrm(mapper.listExtractor, mapper.selectSQL, id)!!.firstOrNull()
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

    inline fun <reified T> delete(entity: T) {
        delete(T::class.java, entity)
    }

    fun <T> deleteById(clazz: Class<T>, id: Any) {
        val mapper = mapperOf(clazz)
        if (logger.isDebugEnabled) {
            logger.debug("deleteById SQL: {}, args: {}", mapper.deleteSQL, id)
        }
        jdbcTemplate.update(mapper.deleteSQL, id)
    }

    inline fun <reified T> deleteById(id: Any) {
        deleteById(T::class.java, id)
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

    inline fun <reified T> update(entity: T) {
        update(T::class.java, entity)
    }

    fun <T> batchInsert(clazz: Class<T>, entities: List<T>) {
        val mapper = mapperOf(clazz)
        val sql = mapper.insertSQL
        logger.atDebug().log("batch insert SQL: {}, count: {}", sql, entities.size)
        entities.forEach { entity ->
            val args = mapper.insertableProperties.map { it[entity as Any] }.toTypedArray()
            if (mapper.id.isGeneratedId) {
                mapper.id[entity as Any] = jdbcTemplate.insert(sql, *args)
            } else {
                jdbcTemplate.update(sql, *args)
            }
        }

//        val mapper = mapperOf(clazz)
//        val sql = mapper.insertSQL
//        val args = entities.flatMap { entity -> mapper.insertableProperties.map { it[entity as Any] } }.toTypedArray()
//        logger.atDebug().log("batch insert SQL: {}, count: {}", sql, entities.size)
//
//        val keys = jdbcTemplate.batchInsert(sql, entities.size, *args)
//        if (mapper.id.isGeneratedId) {
//            keys.forEachIndexed { index, key ->
//                mapper.id[entities[index] as Any] = key
//            }
//        }
    }

    inline fun <reified T> batchInsert(entities: List<T>) {
        batchInsert(T::class.java, entities)
    }

    fun <T> insert(clazz: Class<T>, entity: T) {
        val mapper = mapperOf(clazz)
        val args = mapper.insertableProperties.map { it[entity as Any] }.toTypedArray()
        val sql = mapper.insertSQL
        logger.atDebug().log("insert SQL: {}, args: {}", sql, args)
        if (mapper.id.isGeneratedId) {
            mapper.id[entity as Any] = jdbcTemplate.insert(sql, *args)
        } else {
            jdbcTemplate.update(sql, *args)
        }
    }

    inline fun <reified T> insert(entity: T) {
        insert(T::class.java, entity)
    }
}