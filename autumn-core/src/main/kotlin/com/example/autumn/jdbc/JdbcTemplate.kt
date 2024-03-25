package com.example.autumn.jdbc

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

class JdbcTemplate(dataSource: DataSource) {
    fun <T> queryForObject(sql: String, rowMapper: RowMapper<T>, vararg args: Any): T {
        TODO()
    }

    fun queryForNumber(sql: String, vararg args: Any): Number {
        return queryForObject(sql, NumberRowMapper.instance, args)
    }
}

fun interface ConnectionCallback<T> {
    fun onConnection(con: Connection): T?
}

fun interface PreparedStatementCallback<T> {
    fun onPreparedStatement(ps: PreparedStatement): T?
}

fun interface PreparedStatementCreator {
    fun createPreparedStatement(con: Connection): PreparedStatement
}

fun interface ResultSetExtractor<T> {
    fun extractData(rs: ResultSet): T?
}

fun interface RowMapper<T> {
    fun map(rs: ResultSet, rowNum: Int): T?
}
