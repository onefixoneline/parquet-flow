package com.onefixoneline.parquetflow

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

object ParquetFileType : FileType {

    const val DEFAULT_EXTENSION: String = "parquet"

    private val ICON: Icon = com.intellij.openapi.util.IconLoader.getIcon(
        "/icons/parquetFile.svg",
        ParquetFileType::class.java,
    )

    override fun getName(): String = "Parquet"

    override fun getDescription(): String = "Apache Parquet columnar data file"

    override fun getDisplayName(): String = "Parquet"

    override fun getDefaultExtension(): String = DEFAULT_EXTENSION

    override fun getIcon(): Icon = ICON

    override fun isBinary(): Boolean = true

    override fun isReadOnly(): Boolean = true

    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null
}