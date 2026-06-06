package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "service_providers")
data class ServiceProvider(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String,
    val category: String, // plumbing, electricity, painter, carpentry, blacksmith
    val address: String,
    val district: String, // e.g. صنعاء - الحصبة, صنعاء - شارع حدة
    val avatarUrl: String = "", // Base64 or local drawable reference
    val isVerified: Boolean = false,
    val isRecommended: Boolean = false, // VIP status
    val isPinned: Boolean = false, // Always on top
    val isAvailable: Boolean = true,
    val rating: Float = 5.0f,
    val reviewCount: Int = 1,
    val fee: Int = 3000, // Initial inspection fee in YER
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val icon: String, // e.g., "🔧", "⚡", "🎨", "🔨", "⚙️"
    val order: Int = 0,
    val isPinned: Boolean = false
)

@Entity(tableName = "pending_providers")
data class PendingProvider(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String,
    val category: String,
    val address: String,
    val district: String,
    val avatarData: String = "", // Personal picture or profession-themed avatar
    val idCardData: String = "", // Optional ID Card image
    val status: String = "pending", // pending, accepted, rejected
    val rejectionReason: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "reviews")
data class Review(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val providerId: Int,
    val userName: String,
    val rating: Int,
    val comment: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "banners")
data class Banner(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val text: String,
    val actionUrl: String = "",
    val imagePath: String = "",
    val duration: Int = 5, // Display length in seconds
    val isActive: Boolean = true
)

@Entity(tableName = "loyalty_points")
data class LoyaltyPoints(
    @PrimaryKey val id: Int = 1,
    val points: Int = 100 // Defaults 100 for VIP demo
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val senderName: String,
    val receiverName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val appName: String = "كل خدمات اليمن",
    val supportPhone: String = "777644670",
    val supportEmail: String = "support@yemenservices.com",
    val supportWhatsApp: String = "777644670",
    val footerText: String = "MAW 777644670",
    val welcomeMessage: String = "أهلاً ومرحباً بكم مع تطبيق كل خدمات اليمن",
    val marqueeSpeed: Int = 5, // Custom scroll speed
    val primaryColorHex: String = "#00695C", // Default emerald green
    val secondaryColorHex: String = "#FFB300", // Default gold/amber accent
    val footerFontSizeFactor: Float = 0.5f, // Font size is 50% smaller by default
    val footerOpacity: Float = 1.0f,
    val chatbotIconSize: Int = 40, // 40dp (less than standard size by default)
    val chatIconSize: Int = 40, // 40dp (less than standard size by default)
    val isVoiceSearchEnabled: Boolean = true,
    val isLoyaltyPointsVisible: Boolean = true,
    val isMaintenanceMode: Boolean = false,
    val isTwoFactorAuthEnabled: Boolean = false,
    val isDataSavingMode: Boolean = false,
    val currentThemeName: String = "emerald" // emerald, midnight, gold, custom
)

@Entity(tableName = "activity_logs")
data class ActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val adminName: String,
    val action: String,
    val timestamp: Long = System.currentTimeMillis()
)
