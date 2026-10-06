package com.onefixoneline.parquetflow

import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

object ParquetFile {

    /**
     * SQL NULL 与空字符串 "" 语义完全不同，表格里必须显式区分：
     * NULL 显示为 <null>，空字符串才真正留白（与 DataGrip / Snowflake / pandas 行为一致）。
     */
    const val NULL_PLACEHOLDER: String = "<null>"

    private val SUPPORTED_EXTENSIONS = setOf(ParquetFileType.DEFAULT_EXTENSION, "pq")

    fun isSupported(file: VirtualFile): Boolean =
        !file.isDirectory && file.extension?.lowercase() in SUPPORTED_EXTENSIONS

    fun localPath(file: VirtualFile): Path = Path.of(file.path)
}