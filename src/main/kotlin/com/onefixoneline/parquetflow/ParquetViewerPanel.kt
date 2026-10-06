package com.onefixoneline.parquetflow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel

class ParquetViewerPanel(
    private val project: Project,
    private val file: VirtualFile,
) : JBPanel<ParquetViewerPanel>(BorderLayout()), Disposable {

    @Volatile
    private var disposed: Boolean = false

    private val pathLabel = JBLabel(file.presentableUrl).apply {
        foreground = JBColor.GRAY
        border = JBUI.Borders.empty(4, 8)
    }

    private val statusLabel = JBLabel(" ")
    private val rowLimitField = com.intellij.ui.JBIntSpinner(
        ParquetReader.DEFAULT_ROW_LIMIT, 10, 1_000_000, 100
    )
    private val schemaModel = SchemaTableModel()
    private val schemaTable = JBTable(schemaModel)

    private val dataModel = DataTableModel()
    private val dataTable = JBTable(dataModel).apply {
        autoResizeMode = javax.swing.JTable.AUTO_RESIZE_OFF
        setShowGrid(false)
        tableHeader.reorderingAllowed = false
    }

    private val splitter = com.intellij.ui.JBSplitter(true, 0.35f).apply {
        firstComponent = JBScrollPane(schemaTable)
        secondComponent = JBScrollPane(dataTable)
    }

    init {
        border = JBUI.Borders.empty()
        add(buildToolbar(), BorderLayout.NORTH)
        add(splitter, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
        load()
    }

    private fun buildToolbar(): JPanel {
        rowLimitField.addChangeListener { load() }
        val reload = JButton("Reload").apply { addActionListener { load() } }
        val controls = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 4)).apply {
            isOpaque = false
            add(rowLimitField)
            add(reload)
        }
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 0)
            add(pathLabel, BorderLayout.CENTER)
            add(controls, BorderLayout.EAST)
        }
    }

    private fun load() {
        val limit = rowLimitField.number.coerceAtLeast(1)
        statusLabel.text = "Loading ${file.name} ..."
        dataModel.clear()
        val path: Path = ParquetFile.localPath(file)

        val task = object : Task.Backgroundable(project, "Reading ${file.name}", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val data = try {
                    ParquetReader.load(path, limit)
                } catch (e: Throwable) {
                    ApplicationManager.getApplication().invokeLater {
                        if (!disposed) {
                            statusLabel.text = "Failed to read: ${e.message ?: e.javaClass.simpleName}"
                        }
                    }
                    return
                }
                indicator.isIndeterminate = false
                ApplicationManager.getApplication().invokeLater {
                    if (disposed) return@invokeLater
                    schemaModel.setColumns(data.columns)
                    dataModel.setData(data.columns, data.rows)
                    applyColumnWidths(data.columns.size)
                    statusLabel.text = buildStatusText(data, limit)
                }
            }
        }
        ProgressManager.getInstance().run(task)
    }

    private fun applyColumnWidths(columnCount: Int) {
        if (columnCount == 0) return
        val width = JBUI.scale(220)
        for (index in 0 until columnCount) {
            dataTable.columnModel.getColumn(index).preferredWidth = width
        }
    }

    private fun buildStatusText(data: PreviewData, limit: Int): String {
        val total = data.totalRows?.let { String.format("%,d", it) } ?: "unknown"
        val shown = String.format("%,d", data.rows.size)
        return "Showing $shown of $total rows x ${data.columns.size} columns " +
            "(limit $limit${if (data.truncated) ", truncated" else ""})"
    }

    override fun dispose() {
        disposed = true
    }

    private class SchemaTableModel : AbstractTableModel() {
        private var columns: List<ColumnInfo> = emptyList()

        override fun getRowCount(): Int = columns.size

        override fun getColumnCount(): Int = 2

        override fun getColumnName(column: Int): String = if (column == 0) "Column" else "Type"

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

        override fun getValueAt(rowIndex: Int, columnIndex: Int): String {
            val info = columns[rowIndex]
            if (columnIndex == 0) return info.name
            return "${info.type} | ${if (info.nullable) "NULL" else "NOT NULL"}"
        }

        fun setColumns(value: List<ColumnInfo>) {
            columns = value
            fireTableDataChanged()
        }
    }

    private class DataTableModel : AbstractTableModel() {
        private var columns: List<ColumnInfo> = emptyList()
        private var rows: List<List<String?>> = emptyList()

        override fun getRowCount(): Int = rows.size

        override fun getColumnCount(): Int = columns.size

        override fun getColumnName(column: Int): String = columns[column].name

        override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

        override fun getValueAt(rowIndex: Int, columnIndex: Int): String =
            rows[rowIndex].getOrNull(columnIndex) ?: ParquetFile.NULL_PLACEHOLDER

        fun setData(newColumns: List<ColumnInfo>, newRows: List<List<String?>>) {
            columns = newColumns
            rows = newRows
            fireTableStructureChanged()
        }

        fun clear() {
            columns = emptyList()
            rows = emptyList()
            fireTableStructureChanged()
        }
    }
}