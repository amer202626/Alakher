package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AppRepository(private val dao: AppDao) {
    val allProviders: Flow<List<ServiceProvider>> = dao.getAllProvidersFlow()
    val allCategories: Flow<List<Category>> = dao.getAllCategoriesFlow()
    val pendingProviders: Flow<List<PendingProvider>> = dao.getAllPendingProvidersFlow()
    val loyaltyPoints: Flow<LoyaltyPoints?> = dao.getLoyaltyPointsFlow()
    val chatMessages: Flow<List<ChatMessage>> = dao.getAllChatMessagesFlow()
    val settings: Flow<AppSettings?> = dao.getSettingsFlow()
    val banners: Flow<List<Banner>> = dao.getAllBannersFlow()
    val activeBanners: Flow<List<Banner>> = dao.getActiveBannersFlow()
    val activityLogs: Flow<List<ActivityLog>> = dao.getActivityLogsFlow()

    suspend fun getAppSettings(): AppSettings {
        return dao.getAppSettings() ?: AppSettings()
    }

    suspend fun saveSettings(appSettings: AppSettings) {
        dao.insertSettings(appSettings)
    }

    suspend fun addProvider(provider: ServiceProvider) {
        dao.insertProvider(provider)
    }

    suspend fun deleteProvider(id: Int) {
        dao.deleteProviderById(id)
    }

    suspend fun submitPendingProvider(pending: PendingProvider) {
        dao.insertPendingProvider(pending)
    }

    suspend fun resolvePendingProvider(id: Int, accept: Boolean, reason: String? = null) {
        if (accept) {
            dao.updatePendingStatus(id, "accepted", null)
        } else {
            dao.updatePendingStatus(id, "rejected", reason)
        }
    }

    suspend fun addCategory(cat: Category) {
        dao.insertCategory(cat)
    }

    suspend fun deleteCategory(id: Int) {
        dao.deleteCategoryById(id)
    }

    suspend fun addBanner(banner: Banner) {
        dao.insertBanner(banner)
    }

    suspend fun deleteBanner(id: Int) {
        dao.deleteBannerById(id)
    }

    suspend fun updatePoints(points: Int) {
        dao.insertLoyaltyPoints(LoyaltyPoints(points = points))
    }

    suspend fun sendChatMessage(msg: ChatMessage) {
        dao.insertMessage(msg)
    }

    suspend fun clearChatHistory() {
        dao.clearAllChatMessages()
    }

    suspend fun addReview(review: Review) {
        dao.insertReview(review)
        // Adjust average rating
        val provider = dao.getProviderById(review.providerId)
        if (provider != null) {
            val newReviewCount = provider.reviewCount + 1
            val newRating = ((provider.rating * provider.reviewCount) + review.rating) / newReviewCount
            dao.insertProvider(
                provider.copy(
                    rating = newRating,
                    reviewCount = newReviewCount
                )
            )
        }
    }

    suspend fun addLog(adminName: String, action: String) {
        dao.insertActivityLog(ActivityLog(adminName = adminName, action = action))
    }
}
