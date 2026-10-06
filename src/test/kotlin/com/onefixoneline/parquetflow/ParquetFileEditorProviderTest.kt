package com.onefixoneline.parquetflow

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Container
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JTable

class ParquetFileEditorProviderTest : BasePlatformTestCase() {

    fun testProviderAcceptsParquetAndRejectsOthers() {
        val provider = ParquetFileEditorProvider()

        assertTrue(provider.accept(project, createFile("accept.parquet", writeSampleParquet())))
        assertEquals(FileEditorPolicy.HIDE_DEFAULT_EDITOR, provider.policy)

        val textFile = createFile("notes.txt", "plain text".toByteArray())
        assertFalse(provider.accept(project, textFile))
    }

    fun testEditorRendersSchemaAndRows() {
        val parquetFile = createFile("render.parquet", writeSampleParquet())

        val editor = openEditor(parquetFile)
        assertNotNull("parquet file must be accepted by our FileEditorProvider", editor)

        val component = editor!!.component
        assertTrue(component is javax.swing.JComponent)

        val tables = findTables(component)
        assertEquals("schema table + data table", 2, tables.size)

        val schemaTable = tables[0]
        val dataTable = tables[1]

        awaitRowCount(dataTable, 100)

        assertEquals(100, dataTable.rowCount)
        assertEquals(4, dataTable.columnCount)
        assertEquals("id", dataTable.columnModel.getColumn(0).headerValue)
        assertEquals("user_1", dataTable.getValueAt(0, 1))
        assertEquals("1.5", dataTable.getValueAt(0, 2))
        assertEquals(
            "SQL NULL must be rendered explicitly, never left blank",
            ParquetFile.NULL_PLACEHOLDER,
            dataTable.getValueAt(9, 2)
        )
        assertEquals("1", dataTable.getValueAt(0, 0))
        assertEquals("false", dataTable.getValueAt(0, 3))
        assertEquals("true", dataTable.getValueAt(1, 3))

        assertEquals(4, schemaTable.rowCount)
        assertEquals("id", schemaTable.getValueAt(0, 0))

        val typeCell = schemaTable.getValueAt(0, 1).toString()
        assertTrue("schema must expose the inferred DuckDB type", typeCell.startsWith("BIGINT"))
        assertTrue(
            "schema type cell must be pure ASCII with an explicit nullability marker, got: $typeCell",
            typeCell.all { it.code in 32..126 }
        )
        assertTrue("nullability marker must be present: $typeCell", typeCell.endsWith("| NULL"))

        for (row in 0 until schemaTable.rowCount) {
            val cell = schemaTable.getValueAt(row, 1).toString()
            assertTrue(
                "no mojibake allowed in schema cells, row $row = $cell",
                cell.all { it.code in 32..126 }
            )
        }
    }

    fun testNullAndEmptyStringAreDistinguishable() {
        val target: Path = Files.createTempFile("parquet-flow-null", ".parquet")
        org.duckdb.DuckDBDriver().connect("jdbc:duckdb:", java.util.Properties())!!.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "COPY (SELECT 'x' AS real_text, NULL::VARCHAR AS nothing, '' AS blank) " +
                        "TO '${sqlPath(target)}' (FORMAT PARQUET)"
                )
            }
        }

        val parquetFile = createFile("nulls.parquet", Files.readAllBytes(target))
        Files.deleteIfExists(target)

        val provider = ParquetFileEditorProvider()
        val component = provider.createEditor(project, parquetFile).component
        val dataTable = findTables(component)[1]

        awaitRowCount(dataTable, 1)

        assertEquals("x", dataTable.getValueAt(0, 0))
        assertEquals(
            "SQL NULL must show a placeholder",
            ParquetFile.NULL_PLACEHOLDER,
            dataTable.getValueAt(0, 1)
        )
        assertEquals(
            "empty string must stay blank so it differs from NULL",
            "",
            dataTable.getValueAt(0, 2)
        )
    }

    private fun sqlPath(path: Path): String = path.toAbsolutePath().toString().replace("\\", "/")

    private fun openEditor(file: VirtualFile): FileEditor? {
        val provider = ParquetFileEditorProvider()
        if (!provider.accept(project, file)) return null
        return provider.createEditor(project, file)
    }

    private fun awaitRowCount(table: JTable, expected: Int) {
        val deadline = System.currentTimeMillis() + 30_000
        while (table.rowCount < expected && System.currentTimeMillis() < deadline) {
            UIUtil.dispatchAllInvocationEvents()
            Thread.sleep(50)
        }
        UIUtil.dispatchAllInvocationEvents()
    }

    private fun findTables(root: Component): List<JTable> {
        val found = mutableListOf<JTable>()
        fun walk(component: Component) {
            if (component is JTable) found += component
            if (component is Container) component.components.forEach(::walk)
        }
        walk(root)
        return found
    }

    private fun createFile(name: String, content: ByteArray): VirtualFile {
        val ioFile = Files.createTempDirectory("parquet-flow-vfs").resolve(name).toFile()
        ioFile.writeBytes(content)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile)
            ?: error("VFS cannot see freshly written test file: $ioFile")
        return file
    }

    private fun writeSampleParquet(): ByteArray {
        val target: Path = Files.createTempFile("parquet-flow-test", ".parquet")
        org.duckdb.DuckDBDriver().connect("jdbc:duckdb:", java.util.Properties())!!.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    COPY (
                        SELECT i AS id,
                               'user_' || i AS name,
                               CASE WHEN i % 10 = 0 THEN NULL ELSE i * 1.5 END AS score,
                               i % 2 = 0 AS active
                        FROM range(1, 101) t(i)
                    ) TO '${target.toString().replace("\\", "/")}' (FORMAT PARQUET)
                    """.trimIndent()
                )
            }
        }
        return Files.readAllBytes(target).also { Files.deleteIfExists(target) }
    }
}