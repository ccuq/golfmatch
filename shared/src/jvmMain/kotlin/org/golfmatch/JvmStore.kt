package org.golfmatch

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.golfmatch.db.GolfDatabase
import java.util.Properties

/** Base SQLite sur la JVM : fichier si `path` est donné, sinon en mémoire (tests). */
fun createJvmStore(path: String? = null, clock: () -> Long = System::currentTimeMillis): GolfStore {
    val url = if (path == null) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:$path"
    return GolfStore(JdbcSqliteDriver(url, Properties(), GolfDatabase.Schema), clock)
}
