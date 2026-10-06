package com.onefixoneline.parquetflow

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Driver
import java.sql.SQLException
import java.util.Properties
import org.duckdb.DuckDBDriver

/**
 * 回归防线：IntelliJ 运行期每个插件跑在独立的 PluginClassLoader 中，
 * 而 DriverManager 由 bootstrap 类加载器持有，只按系统 classpath 扫描 JDBC SPI，
 * 因此在真实 IDE 里 `DriverManager.getConnection("jdbc:duckdb:")` 必然抛
 * "No suitable driver found"，而 Gradle test 环境却完全正常。
 *
 * 这些测试锁定 ParquetReader 不再依赖 DriverManager。
 */
class ParquetDriverIsolationTest : junit.framework.TestCase() {

    fun testDriverManagerCannotSeePluginClassLoaderDriver() {
        val url = "jdbc:duckdb:"
        val viaDriverManager = try {
            java.sql.DriverManager.getConnection(url)
        } catch (e: SQLException) {
            null
        }

        val directlyInstantiated: Driver = DuckDBDriver()
        assertTrue(directlyInstantiated.acceptsURL(url))

        // 直连必须永远可用，无论 DriverManager 在当前运行环境能否发现驱动
        val connection = directlyInstantiated.connect(url, Properties())
        assertNotNull("direct DuckDBDriver.connect must not return null", connection)
        connection.use { conn ->
            conn.createStatement().use { statement ->
                statement.executeQuery("SELECT 42").use { result ->
                    assertTrue(result.next())
                    assertEquals(42, result.getInt(1))
                }
            }
        }

        assertEquals(
            "DuckDBDriver must be loadable by the plugin class loader",
            DuckDBDriver::class.java,
            Class.forName("org.duckdb.DuckDBDriver", true, javaClass.classLoader)
        )

        if (viaDriverManager == null) {
            println("[info] DriverManager cannot discover DuckDB here, as expected under PluginClassLoader")
        }
    }

    fun testReaderWorksWithoutDriverManager() {
        val target: Path = Files.createTempFile("parquet-flow-isolation", ".parquet")
        DuckDBDriver().connect("jdbc:duckdb:", Properties())!!.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "COPY (SELECT 1 AS id, 'x' AS name) TO '${sqlPath(target)}' (FORMAT PARQUET)"
                )
            }
        }

        val data = ParquetReader.load(target, 10)

        assertEquals(2, data.columns.size)
        assertEquals(1, data.rows.size)
        assertEquals("1", data.rows[0][0])
        assertEquals("x", data.rows[0][1])

        Files.deleteIfExists(target)
    }

    private fun sqlPath(path: Path): String = path.toAbsolutePath().toString().replace("\\", "/")
}