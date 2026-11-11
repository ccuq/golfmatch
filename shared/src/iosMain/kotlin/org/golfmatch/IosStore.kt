package org.golfmatch

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import org.golfmatch.db.GolfDatabase
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/** Base SQLite de l'appli iPhone (fichier golfmatch.db dans le dossier de l'appli). */
fun createIosStore(): GolfStore =
    GolfStore(
        NativeSqliteDriver(GolfDatabase.Schema, "golfmatch.db"),
        clock = { (NSDate().timeIntervalSince1970 * 1000).toLong() },
    )
