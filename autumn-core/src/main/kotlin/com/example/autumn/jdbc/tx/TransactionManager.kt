package com.example.autumn.jdbc.tx

import javax.sql.DataSource

interface TransactionManager

class DataSourceTransactionManager(dataSource: DataSource) : TransactionManager {

}

