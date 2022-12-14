package operator

import core.table.TableColumn
import dialect.GlobalDialect
import engine.Database
import logging.GlobalLogInstance
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

class QueryOperator<T : Any>(
    internal val tableName: String,
    internal val columnList: List<TableColumn>,
    internal val dataClass: KClass<T>
) : Operator {

    internal var whereCondition: String = ""
    internal var ascOrDesc: String = ""
    internal var orderByColumnName: String = ""
    internal var groupByColumnName: String = ""

    fun where(condition: String): QueryOperator<T> {
        whereCondition = condition
        return this
    }

    fun groupBy(column: String): QueryOperator<T> {
        groupByColumnName = column
        return this
    }

    fun orderBy(column: String): QueryOperator<T> {
        orderByColumnName = column
        return this
    }


    fun asc(): QueryOperator<T> {
        ascOrDesc = "ASC"
        return this
    }

    fun desc(): QueryOperator<T> {
        ascOrDesc = "DESC"
        return this
    }

    fun res(): List<T> {

        val statement = GlobalDialect.dialect.generateQuerySQL(this)
        val resultSet = Database.executePrepareStatementQuery(statement)
        GlobalLogInstance.log.infoPrepareStatement(statement)
        val list = GlobalDialect.dialect.readResultSet(resultSet, columnList).map {
            dataClass.primaryConstructor!!.call(*it.toTypedArray())
        }
        return list
    }

    override fun end() {
        val statement = GlobalDialect.dialect.generateQuerySQL(this)
        GlobalLogInstance.log.infoPrepareStatement(statement)
        Database.executePrepareStatement(statement)
    }


}
