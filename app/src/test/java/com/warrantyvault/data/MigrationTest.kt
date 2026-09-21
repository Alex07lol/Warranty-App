package com.warrantyvault.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migration tests that exercise the REAL production open path:
 * Room.databaseBuilder + AppDatabase.ALL_MIGRATIONS + Room's own schema validation.
 *
 * A database is first created on disk with the exact schema DDL exported to
 * app/schemas/com.warrantyvault.data.AppDatabase/3.json, seeded with rows in every key
 * table, and its user_version set to the version an old install would report.
 *
 * Historical note: every pre-overhaul install is at v2 (the app used
 * fallbackToDestructiveMigration at version 2 back then), and the v2 columns were
 * verified identical to the exported v3 schema, so simulating a v2 install as
 * "v3 DDL + user_version 2" is faithful.
 *
 * Room validates the resulting schema against the v4 entity definitions on open, so any
 * migration mistake fails the test loudly instead of corrupting user data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {

    private val dbName = "migration-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    // DDL below is generated from app/schemas/com.warrantyvault.data.AppDatabase/3.json.
    // If the entity definitions change, regenerate this block from a fresh schema export.
    private fun createV3Schema(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `users` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `email` TEXT NOT NULL, `passwordHash` TEXT NOT NULL, " +
                "`isActive` INTEGER NOT NULL, `isEmailVerified` INTEGER NOT NULL, " +
                "`profilePicture` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `products` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, `productName` TEXT NOT NULL, `brand` TEXT, `model` TEXT, " +
                "`category` TEXT, `purchaseDate` INTEGER, `purchasePrice` REAL, `currency` TEXT NOT NULL, " +
                "`purchaseStore` TEXT, `serialNumber` TEXT, `warrantyExpiryDate` INTEGER, " +
                "`warrantyPeriodMonths` INTEGER, `warrantyProvider` TEXT, `warrantyProviderType` TEXT, " +
                "`warrantyContact` TEXT, `warrantyWebsite` TEXT, `lifecycleStatus` TEXT NOT NULL, " +
                "`tags` TEXT NOT NULL, `notes` TEXT, `isDeleted` INTEGER NOT NULL, `thumbnailUrl` TEXT, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_userId` ON `products` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_userId_isDeleted` ON `products` (`userId`, `isDeleted`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_warrantyExpiryDate` ON `products` (`warrantyExpiryDate`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_products_userId_serialNumber` ON `products` (`userId`, `serialNumber`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `warranty_periods` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`productId` INTEGER NOT NULL, `type` TEXT, `provider` TEXT, `startDate` INTEGER, " +
                "`expiryDate` INTEGER, `coverage` TEXT, `notes` TEXT, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_warranty_periods_productId` ON `warranty_periods` (`productId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `documents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`productId` INTEGER, `userId` INTEGER NOT NULL, `documentType` TEXT NOT NULL, " +
                "`fileName` TEXT NOT NULL, `filePath` TEXT NOT NULL, `fileSize` INTEGER NOT NULL, " +
                "`mimeType` TEXT NOT NULL, `uploadedAt` INTEGER NOT NULL, `docState` TEXT NOT NULL, " +
                "`verified` INTEGER NOT NULL, `tags` TEXT NOT NULL, `notes` TEXT, `ocrStatus` TEXT NOT NULL, " +
                "`ocrText` TEXT, `parsedData` TEXT, `ocrError` TEXT, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                "FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_userId` ON `documents` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_productId` ON `documents` (`productId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_userId_documentType` ON `documents` (`userId`, `documentType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_tags` ON `documents` (`tags`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `service_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`productId` INTEGER NOT NULL, `userId` INTEGER NOT NULL, `serviceDate` INTEGER NOT NULL, " +
                "`serviceType` TEXT NOT NULL, `serviceProvider` TEXT, `cost` REAL, `currency` TEXT NOT NULL, " +
                "`description` TEXT, `documentId` INTEGER, `nextServiceDate` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_history_productId` ON `service_history` (`productId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_history_userId` ON `service_history` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_history_serviceDate` ON `service_history` (`serviceDate`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, `productId` INTEGER, `documentId` INTEGER, " +
                "`notificationType` TEXT NOT NULL, `title` TEXT NOT NULL, `message` TEXT NOT NULL, " +
                "`isRead` INTEGER NOT NULL, `isSent` INTEGER NOT NULL, `scheduledAt` INTEGER, " +
                "`sentAt` INTEGER, `createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                "FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_userId_isRead` ON `notifications` (`userId`, `isRead`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_notifications_productId_notificationType_scheduledAt` " +
                "ON `notifications` (`productId`, `notificationType`, `scheduledAt`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `repair_centers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `brand` TEXT, `address` TEXT NOT NULL, `city` TEXT, `state` TEXT, " +
                "`countryCode` TEXT, `postalCode` TEXT, `phone` TEXT, `website` TEXT, `lat` REAL NOT NULL, " +
                "`lng` REAL NOT NULL, `types` TEXT NOT NULL, `rating` REAL, `userRatingsTotal` INTEGER, " +
                "`openingHours` TEXT, `source` TEXT NOT NULL, `placeId` TEXT, `lastUpdated` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_repair_centers_brand_countryCode` ON `repair_centers` (`brand`, `countryCode`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_repair_centers_lat_lng` ON `repair_centers` (`lat`, `lng`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_repair_centers_source` ON `repair_centers` (`source`)")
    }

    private fun seed(db: SQLiteDatabase) {
        db.execSQL(
            "INSERT INTO users (id, name, email, passwordHash, isActive, isEmailVerified, createdAt, updatedAt) " +
                "VALUES (1, 'Tester', 't@example.com', 'hash', 1, 0, 1000, 1000)"
        )
        db.execSQL(
            "INSERT INTO products (id, userId, productName, purchaseDate, purchasePrice, currency, " +
                "serialNumber, warrantyExpiryDate, warrantyPeriodMonths, lifecycleStatus, tags, " +
                "isDeleted, createdAt, updatedAt) " +
                "VALUES (10, 1, 'Drill', 1700000000000, 99.5, 'USD', 'SN-1', 1800000000000, 24, " +
                "'owned', '[]', 0, 1000, 1000)"
        )
        db.execSQL(
            "INSERT INTO documents (id, productId, userId, documentType, fileName, filePath, " +
                "fileSize, mimeType, uploadedAt, docState, verified, tags, ocrStatus, ocrText, createdAt, updatedAt) " +
                "VALUES (20, 10, 1, 'receipt', 'r.jpg', '/docs/r.jpg', 555, 'image/jpeg', 1000, " +
                "'unreviewed', 0, '[]', 'done', 'RAW OCR TEXT', 1000, 1000)"
        )
        db.execSQL(
            "INSERT INTO notifications (id, userId, productId, notificationType, title, message, " +
                "isRead, isSent, createdAt) " +
                "VALUES (30, 1, 10, 'expiry', 'Warranty ending', 'Soon', 0, 0, 1000)"
        )
        db.execSQL(
            "INSERT INTO warranty_periods (id, productId, type, provider, startDate, expiryDate, " +
                "createdAt, updatedAt) " +
                "VALUES (40, 10, 'manufacturer', 'Acme', 1700000000000, 1800000000000, 1000, 1000)"
        )
    }

    /** Creates the v3 DDL database on disk, seeds it, and stamps the given user_version. */
    private fun createOldDatabase(version: Int) {
        val path = context.getDatabasePath(dbName)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            createV3Schema(db)
            seed(db)
            db.version = version
        }
    }

    private fun openWithRealBuilder(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
            .also { it.openHelper.writableDatabase } // forces open: runs migrations + schema validation

    @Test
    fun `migrate 2 to 4 preserves all rows and adds imei`() {
        createOldDatabase(version = 2)

        val db = openWithRealBuilder()
        try {
            val raw = db.openHelper.writableDatabase
            raw.query("SELECT productName, serialNumber, imei FROM products WHERE id = 10").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Drill", c.getString(0))
                assertEquals("SN-1", c.getString(1))
                // New column exists and is NULL for pre-existing rows.
                assertTrue(c.isNull(2))
            }
            raw.query("SELECT COUNT(*) FROM documents WHERE productId = 10").use { c ->
                c.moveToFirst(); assertEquals(1, c.getInt(0))
            }
            raw.query("SELECT COUNT(*) FROM notifications WHERE productId = 10").use { c ->
                c.moveToFirst(); assertEquals(1, c.getInt(0))
            }
            raw.query("SELECT COUNT(*) FROM warranty_periods WHERE productId = 10").use { c ->
                c.moveToFirst(); assertEquals(1, c.getInt(0))
            }
            raw.query("SELECT name, email FROM users WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Tester", c.getString(0))
            }
            // The new column is usable through the DAO layer immediately after migration.
            val withImei = runBlocking {
                val id = db.productDao().insertProduct(
                    Product(userId = 1, productName = "Phone", imei = "490154203237518")
                )
                db.productDao().getProductByIdImmediate(id)
            }
            assertEquals("490154203237518", withImei?.imei)
            // Old product untouched by the new insert.
            val old = runBlocking { db.productDao().getProductByIdImmediate(10) }
            assertEquals("Drill", old?.productName)
            assertNull(old?.imei)
        } finally {
            db.close()
        }
    }

    @Test
    fun `migrate 3 to 4 adds imei column`() {
        createOldDatabase(version = 3)

        val db = openWithRealBuilder()
        try {
            val raw = db.openHelper.writableDatabase
            raw.query("SELECT productName, imei FROM products WHERE id = 10").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Drill", c.getString(0))
                assertTrue(c.isNull(1))
            }
            val all = runBlocking { db.productDao().getAllForUser(1) }
            assertFalse(all.isEmpty())
            assertEquals("Drill", all.first { it.id == 10L }.productName)
        } finally {
            db.close()
        }
    }
}
