import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    id("org.jetbrains.intellij.platform") version "2.0.1"
}

group = property("pluginGroup").toString()
version = property("pluginVersion").toString()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 目标开发测试基准：IntelliJ IDEA 社区版
        intellijIdeaCommunity("2024.1.4")
        bundledPlugin("com.intellij.java")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        instrumentationTools()
        pluginVerifier()
    }

    // 本地轻量级列式分析引擎（用于秒级读取 Parquet 与未来就地 SQL 查询）
    implementation("org.duckdb:duckdb_jdbc:1.1.0")

    // kotlin-stdlib 由 IntelliJ 平台提供，仅编译期可见，避免与平台版本冲突
    compileOnly(kotlin("stdlib"))

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        id = "com.onefixoneline.parquetflow"
        name = "Parquet Flow - Fast Data Viewer"
        vendor {
            name = "Morris Everley"
            email = "morriseverley@gmail.com"
        }
        description = """
            Fast in-IDE viewer for Apache Parquet files. Open any .parquet or .pq file to inspect its schema and sample rows without leaving your IDE.
            <ul>
                <li><b>Schema overview:</b> column names, inferred types, and nullability.</li>
                <li><b>Data preview:</b> virtualized table with an adjustable row limit.</li>
                <li><b>Nested types:</b> LIST, MAP, STRUCT, TIMESTAMP_NS, BLOB, DECIMAL.</li>
                <li><b>Local only:</b> an embedded in-process DuckDB engine. No server, no network requests, no data leaves your machine.</li>
            </ul>
        """.trimIndent()

        changeNotes = """
            <ul>
              <li>Initial release of Parquet Flow.</li>
              <li>Open any .parquet or .pq file to see its schema and a data preview without leaving the IDE.</li>
              <li>Schema overview lists column names, inferred types, and nullability.</li>
              <li>Data preview uses a virtualized table; the row limit is adjustable in the toolbar.</li>
              <li>Renders nested and high-precision types: LIST, MAP, STRUCT, TIMESTAMP_NS, BLOB, DECIMAL.</li>
              <li>SQL NULL is displayed as &lt;null&gt; so it is never confused with an empty string.</li>
              <li>Reads files in-process with an embedded DuckDB engine. No server, no network requests, no data leaves your machine.</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    // 兼容性终审：检查是否使用了目标 IDE 版本中不存在或已废弃的 API
    pluginVerification {
        ides {
            ide(IntelliJPlatformType.IntellijIdeaCommunity, property("pluginVerifierIdeVersion").toString())
        }

        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
            VerifyPluginTask.FailureLevel.NOT_DYNAMIC,
        )
        freeArgs = listOf("-mute", "TemplateWordInPluginId,ForbiddenPluginIdPrefix")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks {
    // 显式锁定 UTF-8：中文 Windows 默认 GBK(936)，源码里的非 ASCII 字符
    // （含中文注释）若按平台默认编码读取会产生乱码
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // 本插件全部使用代码构建 Swing UI，不使用 .form 可视化表单，
    // 因此跳过 Form/NOT_NULL 字节码插桩（可避免额外下载 JBR 编译器）。
    instrumentCode {
        enabled = false
    }
}