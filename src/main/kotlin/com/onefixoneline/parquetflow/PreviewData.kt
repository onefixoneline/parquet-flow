package com.onefixoneline.parquetflow

data class ColumnInfo(
    val name: String,
    val type: String,
    val nullable: Boolean,
)

data class PreviewData(
    val columns: List<ColumnInfo>,
    val rows: List<List<String?>>,
    val totalRows: Long?,
    val truncated: Boolean,
)