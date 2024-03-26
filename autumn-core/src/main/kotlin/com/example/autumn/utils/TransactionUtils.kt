package com.example.autumn.utils

import com.example.autumn.jdbc.tx.DataSourceTransactionManager
import java.sql.Connection

object TransactionUtils {
    val currentTransaction: Connection?
        get() = DataSourceTransactionManager.transactionStatus.get()?.conn
}