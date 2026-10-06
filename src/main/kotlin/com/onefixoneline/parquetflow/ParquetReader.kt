package com.onefixoneline.parquetflow

import org.duckdb.DuckDBDriver
import java.math.BigDecimal
import java.nio.file.Path
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.util.Properties

object ParquetReader {

    const val DEFAULT_ROW_LIMIT: Int = 500

    private const val CONNECTION_URL = "jdbc:duckdb:"

    /**
     * 必须直接实例化 DuckDBDriver，不能走 DriverManager：
     * DriverManager 由 bootstrap 类加载器持有，只扫描系统 classpath 上的 JDBC SPI，
     * 无法感知 IntelliJ PluginClassLoader 内的驱动，运行期必然抛
     * "No suitable driver found for jdbc:duckdb:"。
     */
    private fun connect(): Connection {
        val connection = DuckDBDriver().connect(CONNECTION_URL, Properties())
        return connection ?: throw SQLException("DuckDB driver returned a null connection for $CONNECTION_URL")
    }

    fun load(path: Path, rowLimit: Int): PreviewData {
        val sqlPath = escape(path.toAbsolutePath().toString())
        val source = "read_parquet('$sqlPath')"

        connect().use { connection ->
            val columns = readSchema(connection, source)
            require(columns.isNotEmpty()) { "Parquet file has no columns: ${path.fileName}" }
            val totalRows = readRowCount(connection, source)
            val rows = readRows(connection, source, rowLimit, columns.size)
            return PreviewData(
                columns = columns,
                rows = rows,
                totalRows = totalRows,
                truncated = totalRows != null && totalRows > rows.size,
            )
        }
    }

    private fun readSchema(connection: Connection, source: String): List<ColumnInfo> {
        connection.createStatement().use { statement ->
            statement.executeQuery("DESCRIBE SELECT * FROM $source").use { rs ->
                val columns = ArrayList<ColumnInfo>()
                while (rs.next()) {
                    columns += ColumnInfo(
                        name = rs.getString("column_name"),
                        type = rs.getString("column_type") ?: "UNKNOWN",
                        nullable = rs.getString("null")?.trim().equals("YES", ignoreCase = true),
                    )
                }
                return columns
            }
        }
    }

    private fun readRowCount(connection: Connection, source: String): Long? = try {
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT count(*) FROM $source").use { rs ->
                if (rs.next()) rs.getLong(1) else null
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun readRows(
        connection: Connection,
        source: String,
        rowLimit: Int,
        columnCount: Int,
    ): List<List<String?>> {
        connection.createStatement().use { statement ->
            statement.queryTimeout = QUERY_TIMEOUT_SECONDS
            statement.executeQuery("SELECT * FROM $source LIMIT $rowLimit").use { rs ->
                val rows = ArrayList<List<String?>>(rowLimit)
                while (rs.next()) {
                    val row = ArrayList<String?>(columnCount)
                    for (i in 1..columnCount) {
                        row += readCell(rs, i)
                    }
                    rows += row
                }
                return rows
            }
        }
    }

    private fun readCell(rs: ResultSet, index: Int): String? = try {
        val typeName = rs.getMetaData().getColumnTypeName(index)
        if (typeName.equals("BLOB", ignoreCase = true)) format(rs.getBytes(index)) else format(rs.getObject(index))
    } catch (e: Exception) {
        val typeName = runCatching { rs.getMetaData().getColumnTypeName(index) }.getOrNull()
        "<${typeName ?: "unknown"} read failed: ${e.message ?: e.javaClass.simpleName}>"
    }

    private fun format(value: Any?): String? = when (value) {
        null -> null
        is BigDecimal -> if (value.scale() <= 0) value.toLong().toString() else value.toPlainString()
        is ByteArray -> value.joinToString("") { byte -> "%02x".format(byte) }
        is Map<*, *> -> value.entries.joinToString(", ", "{", "}") { "${it.key}=${render(it.value)}" }
        is Collection<*> -> value.joinToString(", ", "[", "]") { render(it) }
        is Array<*> -> value.joinToString(", ", "[", "]") { render(it) }
        else -> value.toString()
    }

    private fun render(value: Any?): String = when (value) {
        null -> "null"
        is ByteArray -> value.joinToString("") { byte -> "%02x".format(byte) }
        else -> value.toString()
    }

    private fun escape(path: String): String = path.replace("\\", "/").replace("'", "''")

    private const val QUERY_TIMEOUT_SECONDS = 30
}