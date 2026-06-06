package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.Executors

@Dao
interface AppDao {
    // --- Service Providers ---
    @Query("SELECT * FROM service_providers ORDER BY isPinned DESC, rating DESC")
    fun getAllProvidersFlow(): Flow<List<ServiceProvider>>

    @Query("SELECT * FROM service_providers WHERE id = :id")
    suspend fun getProviderById(id: Int): ServiceProvider?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProvider(provider: ServiceProvider)

    @Query("DELETE FROM service_providers WHERE id = :id")
    suspend fun deleteProviderById(id: Int)

    // --- Categories ---
    @Query("SELECT * FROM categories ORDER BY `order` ASC")
    fun getAllCategoriesFlow(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryById(id: Int)

    // --- Pending Submissions ---
    @Query("SELECT * FROM pending_providers ORDER BY timestamp DESC")
    fun getAllPendingProvidersFlow(): Flow<List<PendingProvider>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingProvider(pending: PendingProvider)

    @Query("UPDATE pending_providers SET status = :status, rejectionReason = :reason WHERE id = :id")
    suspend fun updatePendingStatus(id: Int, status: String, reason: String?)

    @Query("DELETE FROM pending_providers WHERE id = :id")
    suspend fun deletePendingProvider(id: Int)

    // --- Reviews ---
    @Query("SELECT * FROM reviews WHERE providerId = :providerId ORDER BY timestamp DESC")
    fun getReviewsForProviderFlow(providerId: Int): Flow<List<Review>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: Review)

    @Query("DELETE FROM reviews WHERE id = :id")
    suspend fun deleteReviewById(id: Int)

    // --- Banners ---
    @Query("SELECT * FROM banners WHERE isActive = 1")
    fun getActiveBannersFlow(): Flow<List<Banner>>

    @Query("SELECT * FROM banners")
    fun getAllBannersFlow(): Flow<List<Banner>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBanner(banner: Banner)

    @Query("DELETE FROM banners WHERE id = :id")
    suspend fun deleteBannerById(id: Int)

    // --- Loyalty Points ---
    @Query("SELECT * FROM loyalty_points WHERE id = 1")
    fun getLoyaltyPointsFlow(): Flow<LoyaltyPoints?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoyaltyPoints(loyalty: LoyaltyPoints)

    // --- Chat Messages ---
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllChatMessagesFlow(): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(msg: ChatMessage)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllChatMessages()

    // --- App Settings ---
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getAppSettings(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: AppSettings)

    // --- Activity Logs ---
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    fun getActivityLogsFlow(): Flow<List<ActivityLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityLog(log: ActivityLog)
}

@Database(
    entities = [
        ServiceProvider::class,
        Category::class,
        PendingProvider::class,
        Review::class,
        Banner::class,
        LoyaltyPoints::class,
        ChatMessage::class,
        AppSettings::class,
        ActivityLog::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract val appDao: AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "yemen_services_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Populate database in a background thread
                            Executors.newSingleThreadExecutor().execute {
                                val database = getInstance(context)
                                val dao = database.appDao
                                
                                // Insert pre-populated data safely
                                val preloadedCategories = listOf(
                                    Category(name = "الكل", icon = "🏠", order = 0, isPinned = true),
                                    Category(name = "سباكة وصيانة الأنابيب", icon = "🔧", order = 1),
                                    Category(name = "كهرباء وتوصيلات", icon = "⚡", order = 2),
                                    Category(name = "دهان وديكور", icon = "🎨", order = 3),
                                    Category(name = "نجارة وتأثيث", icon = "🔨", order = 4),
                                    Category(name = "حدادة ومعادن", icon = "⚙️", order = 5)
                                )
                                
                                val preloadedProviders = listOf(
                                    ServiceProvider(
                                        name = "الكهربائي محمد القدسي",
                                        phone = "777644670",
                                        category = "كهرباء وتوصيلات",
                                        address = "صنعاء - الحصبة",
                                        district = "صنعاء - الحصبة",
                                        isVerified = true,
                                        isRecommended = true,
                                        isPinned = true,
                                        isAvailable = true,
                                        rating = 4.8f,
                                        reviewCount = 10,
                                        fee = 3000
                                    ),
                                    ServiceProvider(
                                        name = "المهندس ماهر الشرعبي",
                                        phone = "777123456",
                                        category = "سباكة وصيانة الأنابيب",
                                        address = "صنعاء - شارع حدة",
                                        district = "صنعاء - شارع حدة",
                                        isVerified = true,
                                        isRecommended = true,
                                        isPinned = false,
                                        isAvailable = true,
                                        rating = 4.5f,
                                        reviewCount = 10,
                                        fee = 3000
                                    ),
                                    ServiceProvider(
                                        name = "سليم الحطامي - دهان محترف",
                                        phone = "777111222",
                                        category = "دهان وديكور",
                                        address = "صنعاء - الدائري",
                                        district = "صنعاء - الدائري",
                                        isVerified = true,
                                        isRecommended = false,
                                        isPinned = false,
                                        isAvailable = true,
                                        rating = 4.3f,
                                        reviewCount = 4,
                                        fee = 2000
                                    ),
                                    ServiceProvider(
                                        name = "أبو بكر النجار",
                                        phone = "777999888",
                                        category = "نجارة وتأثيث",
                                        address = "صنعاء - الستين",
                                        district = "صنعاء - الستين",
                                        isVerified = false,
                                        isRecommended = false,
                                        isPinned = false,
                                        isAvailable = false,
                                        rating = 4.0f,
                                        reviewCount = 2,
                                        fee = 1500
                                    )
                                )

                                val preloadedBanner = Banner(
                                    title = "إعلان ممول",
                                    text = "تخفيضات صيفية 30% على صيانة المكيفات المركزية",
                                    actionUrl = "https://yemenservices.com/summer-sale",
                                    isActive = true
                                )

                                val defaultSettings = AppSettings()
                                val defaultLoyalty = LoyaltyPoints(points = 100)

                                kotlinx.coroutines.runBlocking {
                                    for (cat in preloadedCategories) {
                                        dao.insertCategory(cat)
                                    }
                                    for (prov in preloadedProviders) {
                                        dao.insertProvider(prov)
                                    }
                                    dao.insertBanner(preloadedBanner)
                                    dao.insertSettings(defaultSettings)
                                    dao.insertLoyaltyPoints(defaultLoyalty)
                                    dao.insertActivityLog(ActivityLog(adminName = "System", action = "ملء البيانات الافتراضية للتطبيق بنجاح."))
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
