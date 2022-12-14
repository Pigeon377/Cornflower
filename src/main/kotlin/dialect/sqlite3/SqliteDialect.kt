package dialect.sqlite3

import java.sql.Types.*
import core.table.TableColumn
import dialect.Dialect
import engine.Database
import operator.*
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlin.math.min


object SqliteDialect : Dialect {

    override fun generateCreateSQL(op: CreateOperator): PreparedStatement {

        val columnList = op.columnList
        val tableName = op.tableName

        val columnBody: String = columnList
            .map { column -> stringifyTableColumn(column) }
            .fold("") { str, s -> "$str $s" }

        // only find first element in list which has primary key annotation
        val primaryKeyName = columnList
            .first { it.isPrimaryKey }
            .name

        val primaryKeyBody = "PRIMARY KEY(`$primaryKeyName`)"


        val temp = columnList
            .filter { it.isUnique }
            .fold("") { str, s -> "$str, `${s.name}`" }

        val uniqueKeyName = temp.substring(min(2, temp.length))

        val uniqueKeyBody =
            if (uniqueKeyName.isNotBlank()) "UNIQUE($uniqueKeyName)"
            else ""

        // warning! can't support normal index!
        // only support unique now

        val sql = "CREATE TABLE `$tableName`($columnBody $primaryKeyBody $uniqueKeyBody);"
        return Database.preparedStatement(sql)
    }

    override fun generateQuerySQL(op: QueryOperator<*>): PreparedStatement {

        val whereSubSentence =
            if (op.whereCondition.isNotBlank()) {
                "WHERE ${op.whereCondition}"
            } else {
                ""
            }

        val groupBySubSentence =
            if (op.groupByColumnName.isNotBlank()) {
                "GROUP BY `${op.tableName}`.`${op.groupByColumnName}`"
            } else {
                ""
            }

        val orderBySubSentence =
            if (op.orderByColumnName.isNotBlank()) {
                "ORDER BY `${op.tableName}`.`${op.orderByColumnName}`"
            } else {
                ""
            }

        val ascOrDesc = op.ascOrDesc

        val sql = "SELECT * FROM `${op.tableName}` " +
                "$whereSubSentence " +
                "$groupBySubSentence " +
                "$orderBySubSentence " +
                "$ascOrDesc "
        return Database.preparedStatement(sql)
    }

    override fun <T : Any> generateInsertSQL(op: InsertOperator<T>): PreparedStatement {
        val tupleString = List(op.columnList.size) { "?" }.toString()
        val tempListString = List(op.list.size) { tupleString }
            .toString()
            .replace("[", "(")
            .replace("]", ")")
        val tupleListString = tempListString.substring(1, tempListString.length - 1)
        val sqlTemplate = "INSERT INTO `${op.tableName}` VALUES $tupleListString"
        val statement = Database.preparedStatement(sqlTemplate)
        var count = 1
        op.dataList.forEach { list ->
            list.forEach { data ->
                when (val typeName = data.javaClass.simpleName) {
                    "Integer" -> statement.setInt(count, data as Int)
                    "Long" -> statement.setLong(count, data as Long)
                    "Double" -> statement.setDouble(count, data as Double)
                    "Float" -> statement.setFloat(count, data as Float)
                    "Boolean" -> statement.setBoolean(count, data as Boolean)
                    "String" -> statement.setString(count, data as String)
                    else -> throw TypeNotPresentException(typeName, null)
                }
                ++count
            }
        }
        return statement
    }

    override fun generateUpdateSQL(op: UpdateOperator): PreparedStatement {

        val whereSubSentence =
            if (op.whereCondition.isNotBlank()) {
                "WHERE ${op.whereCondition}"
            } else {
                ""
            }

        val sql = "UPDATE `${op.tableName}` SET ${op.replaceCondition}  $whereSubSentence"
        return Database.preparedStatement(sql)
    }

    override fun generateDeleteSQL(op: DeleteOperator): PreparedStatement {
        val whereSubSentence =
            if (op.whereCondition.isNotBlank()) {
                "WHERE ${op.whereCondition}"
            } else {
                ""
            }

        val sql = "DELETE FROM `${op.tableName}` $whereSubSentence"
        return Database.preparedStatement(sql)
    }

    override fun generateJoinSQL(op: JoinOperator): PreparedStatement {

        val onSubSentence =
            if (op.on.isNotBlank()) {
                "ON ${op.on}"
            } else {
                ""
            }

        val whereSubSentence =
            if (op.whereCondition.isNotBlank()) {
                "WHERE ${op.whereCondition}"
            } else {
                ""
            }

        val groupBySubSentence =
            if (op.groupByColumnName.isNotBlank()) {
                "GROUP BY `${op.groupByColumnName}`"
            } else {
                ""
            }

        val orderBySubSentence =
            if (op.orderByColumnName.isNotBlank()) {
                "ORDER BY `${op.orderByColumnName}`"
            } else {
                ""
            }

        val ascOrDesc = op.ascOrDesc

        assert(0 != op.leftTableColumnList.size + op.rightColumnList.size)

        val leftSelectColumnStringList =
            op.leftColumnList.map {
                "`${op.leftTableName}`.`${it.name}`"
            }.fold("") { str, s -> "$str, $s" }

        val rightSelectColumnStringList =
            op.rightColumnList.map {
                "`${op.rightTableName}`.`${it.name}`"
            }.fold("") { str, s -> "$str, $s" }

        val selectColumnString =
            if (op.leftColumnList.isEmpty()) {
                rightSelectColumnStringList.substring(1)
            } else {
                leftSelectColumnStringList.substring(1) +
                        if (op.rightColumnList.isNotEmpty()) {
                            ", $rightSelectColumnStringList"
                        } else {
                            ""
                        }
            }

        val sql =
            "SELECT $selectColumnString" +
                    " FROM `${op.leftTableName}` ${op.join} `${op.rightTableName}` " +
                    "$onSubSentence " +
                    "$whereSubSentence " +
                    "$groupBySubSentence " +
                    "$orderBySubSentence " +
                    "$ascOrDesc "
        return Database.preparedStatement(sql)

    }

    override fun readResultSet(resultSet: ResultSet, columnList: List<TableColumn>): List<List<Any>> {

        val ans = mutableListOf<List<Any>>()

        while (resultSet.next()) {
            val list = mutableListOf<Any>()
            for (i in 1..columnList.size) {
                val obj =
                    when (columnList[i - 1].type) {
                        INTEGER -> resultSet.getInt(i)
                        BIGINT -> resultSet.getLong(i)
                        DOUBLE -> resultSet.getDouble(i)
                        FLOAT -> resultSet.getFloat(i)
                        BOOLEAN -> resultSet.getBoolean(i)
                        VARCHAR -> resultSet.getString(i)
                        else -> throw TypeNotPresentException(columnList[i - 1].type.toString(), null)
                    }
                list.add(obj)
            }
            ans.add(list)
        }
        return ans
    }

    private fun sqlTypeMappingToSqliteType(sqlType: Int): String = when (sqlType) {
        INTEGER -> "INT"
        BIGINT -> "BIGINT"
        DOUBLE -> "DOUBLE"
        FLOAT -> "FLOAT"
        BOOLEAN -> "BOOLEAN"
        VARCHAR -> "VARCHAR(255)"
        // TODO add annotation to ensure TEXT type can be used
        else -> throw TypeNotPresentException(sqlType.toString(), null)
    }

    private fun stringifyTableColumn(column: TableColumn): String {
        val name = column.name
        val type = sqlTypeMappingToSqliteType(column.type)
        val nullable = if (!column.isNullable) {
            "NOT NULL"
        } else {
            ""
        }
        return "`$name` $type $nullable,"
    }
}
