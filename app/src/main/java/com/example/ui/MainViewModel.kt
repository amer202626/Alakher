package com.example.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ActivityLog
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.AppSettings
import com.example.data.Banner
import com.example.data.Category
import com.example.data.ChatMessage
import com.example.data.GeminiService
import com.example.data.PendingProvider
import com.example.data.Review
import com.example.data.ServiceProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    val dao = database.appDao
    val repository = AppRepository(dao)

    // --- State Toggles & Views ---
    var currentView by mutableStateOf("home") // home, register, login, admin, about
    var adminActiveTab by mutableStateOf("dashboard") // dashboard, categories, providers, banners, logs, settings, admins, regions
    var isBackdoorUnlocked by mutableStateOf(false)
    var isLoyaltyModalOpen by mutableStateOf(false)
    var isReviewModalOpen by mutableStateOf(false)
    var activeReviewProviderId by mutableStateOf<Int?>(null)
    
    // Support info
    var searchPrompt by mutableStateOf("")
    var voiceSearchActive by mutableStateOf(false)
    var isLanguageArabic by mutableStateOf(true)

    // --- User login state ---
    var isLoggedIn by mutableStateOf(false)
    var loggedInUsername by mutableStateOf("")
    var rememberMeState by mutableStateOf(true)

    // --- Admin login fields ---
    var adminUsernameField by mutableStateOf("")
    var adminPasswordField by mutableStateOf("")
    var loginErrorMessage by mutableStateOf("")

    // --- Search & Filters State ---
    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("الكل")
    val selectedRegion = MutableStateFlow("الكل")
    val minRatingFilter = MutableStateFlow(0f)
    val distanceFilter = MutableStateFlow("الكل") // الكل, قريب, متوسط, بعيد
    val priceFilter = MutableStateFlow("الكل") // الكل, منخفض, متوسط, مرتفع

    // --- Flow-based Live Data Streams ---
    val allProviders: StateFlow<List<ServiceProvider>> = repository.allProviders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCategories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingProviders: StateFlow<List<PendingProvider>> = repository.pendingProviders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loyaltyPointsState = repository.loyaltyPoints
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chatMessagesStream = repository.chatMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appSettingsState = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val activeBanners = repository.activeBanners
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBanners = repository.banners
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activityLogs = repository.activityLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Combined and Filtered Providers List ---
    private data class FilterCriteria(
        val query: String,
        val cat: String,
        val region: String,
        val minRating: Float,
        val price: String
    )

    private val filterCriteriaFlow = combine(
        searchQuery,
        selectedCategory,
        selectedRegion,
        minRatingFilter,
        priceFilter
    ) { query, cat, region, minRating, price ->
        FilterCriteria(query, cat, region, minRating, price)
    }

    val filteredProviders: StateFlow<List<ServiceProvider>> = combine(
        allProviders,
        filterCriteriaFlow
    ) { providers, criteria ->
        providers.filter { provider ->
            // Search Query
            val matchesQuery = criteria.query.isEmpty() ||
                    provider.name.contains(criteria.query, ignoreCase = true) ||
                    provider.category.contains(criteria.query, ignoreCase = true) ||
                    provider.district.contains(criteria.query, ignoreCase = true) ||
                    provider.address.contains(criteria.query, ignoreCase = true)

            // Category Filter
            val matchesCategory = criteria.cat == "الكل" || provider.category == criteria.cat

            // Region/District Filter
            val matchesRegion = criteria.region == "الكل" || provider.district.contains(criteria.region, ignoreCase = true)

            // Rating Filter
            val matchesRating = provider.rating >= criteria.minRating

            // Price/Fee Filter (low < 2000, medium 2000-4000, high > 4000)
            val matchesPrice = when (criteria.price) {
                "منخفض" -> provider.fee < 2000
                "متوسط" -> provider.fee in 2000..4500
                "مرتفع" -> provider.fee > 4500
                else -> true
            }

            matchesQuery && matchesCategory && matchesRegion && matchesRating && matchesPrice
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Dynamic Form Submission ---
    var regName by mutableStateOf("")
    var regPhone by mutableStateOf("")
    var regCategory by mutableStateOf("كهرباء وتوصيلات")
    var regAddress by mutableStateOf("")
    var regDistrict by mutableStateOf("")
    var regAvatarUri by mutableStateOf("") // Simulate selected avatar
    var regIdUri by mutableStateOf("")
    var regGenderIsFemale by mutableStateOf(false)
    var registrationSuccessMsg by mutableStateOf("")

    init {
        // Load default sign-in from local memory state if required
        viewModelScope.launch {
            val settings = repository.getAppSettings()
            Log.d("MainViewModel", "Loaded current app configurations: ${settings.appName}")
        }
    }

    // --- Auth Management ---
    fun performAdminLogin() {
        if (adminUsernameField == "WAM2026" && adminPasswordField == "maher736462") {
            isLoggedIn = true
            loggedInUsername = "WAM2026 (المدير)"
            currentView = "admin"
            loginErrorMessage = ""
            viewModelScope.launch {
                repository.addLog("WAM2026", "تسجيل دخول ناجح إلى لوحة التحكم.")
            }
        } else {
            loginErrorMessage = "اسم المستخدم أو كلمة المرور غير صحيحة!"
        }
    }

    fun submitBackdoorPassword(pass: String): Boolean {
        if (pass == "maher--736462") {
            isBackdoorUnlocked = true
            isLoggedIn = true
            loggedInUsername = "المالك الفني"
            currentView = "admin"
            viewModelScope.launch {
                repository.addLog("Developer Backdoor", "تمت تصفية كلمة المرور الخلفية وولج المالك.")
            }
            return true
        }
        return false
    }

    fun logout() {
        isLoggedIn = false
        loggedInUsername = ""
        isBackdoorUnlocked = false
        currentView = "home"
    }

    // --- Provider Actions (Admin) ---
    fun toggleProviderPin(id: Int, pinned: Boolean) {
        viewModelScope.launch {
            val provider = dao.getProviderById(id)
            if (provider != null) {
                dao.insertProvider(provider.copy(isPinned = pinned))
                repository.addLog(loggedInUsername, "تعديل تثبيت الفني ${provider.name} إلى $pinned")
            }
        }
    }

    fun toggleProviderRecommendation(id: Int, recommended: Boolean) {
        viewModelScope.launch {
            val provider = dao.getProviderById(id)
            if (provider != null) {
                dao.insertProvider(provider.copy(isRecommended = recommended))
                repository.addLog(loggedInUsername, "تعديل توصية VIP للفني ${provider.name} إلى $recommended")
            }
        }
    }

    fun toggleProviderVerification(id: Int, verified: Boolean) {
        viewModelScope.launch {
            val provider = dao.getProviderById(id)
            if (provider != null) {
                dao.insertProvider(provider.copy(isVerified = verified))
                repository.addLog(loggedInUsername, "تعديل توثيق الحساب للفني ${provider.name} إلى $verified")
            }
        }
    }

    fun toggleAvailability(id: Int, available: Boolean) {
        viewModelScope.launch {
            val provider = dao.getProviderById(id)
            if (provider != null) {
                dao.insertProvider(provider.copy(isAvailable = available))
            }
        }
    }

    fun addProviderDirectly(name: String, phone: String, category: String, address: String, district: String, fee: Int) {
        viewModelScope.launch {
            val defaultAvatar = if (category.contains("كهرباء")) "⚡" else "🔧"
            val newProv = ServiceProvider(
                name = name,
                phone = phone,
                category = category,
                address = address,
                district = district,
                fee = fee,
                avatarUrl = defaultAvatar,
                isVerified = true,
                isRecommended = false,
                isPinned = false
            )
            repository.addProvider(newProv)
            repository.addLog(loggedInUsername, "تم إضافة مزود خدمة يدوياً: $name")
        }
    }

    // --- Pending Approvals Flows ---
    fun submitJoinRequest() {
        if (regName.isEmpty() || regPhone.isEmpty() || regAddress.isEmpty() || regDistrict.isEmpty()) {
            registrationSuccessMsg = "جميع الحقول المطلوبة إجبارية!"
            return
        }

        viewModelScope.launch {
            val avatar = if (regAvatarUri.isNotEmpty()) regAvatarUri else {
                if (regGenderIsFemale) "👩‍🔧" else "👨‍🔧"
            }
            val pending = PendingProvider(
                name = regName,
                phone = regPhone,
                category = regCategory,
                address = regAddress,
                district = regDistrict,
                avatarData = avatar,
                idCardData = regIdUri
            )
            repository.submitPendingProvider(pending)
            registrationSuccessMsg = "تم تقديم طلبك بنجاح للمراجعة المباشرة من الأدمن!"
            
            // Clear fields
            regName = ""
            regPhone = ""
            regAddress = ""
            regDistrict = ""
            regAvatarUri = ""
            regIdUri = ""
        }
    }

    fun acceptPendingRequest(pending: PendingProvider) {
        viewModelScope.launch {
            // Transfer to active list
            val provider = ServiceProvider(
                name = pending.name,
                phone = pending.phone,
                category = pending.category,
                address = pending.address,
                district = pending.district,
                avatarUrl = pending.avatarData,
                isVerified = true,
                isRecommended = false,
                isPinned = false
            )
            repository.addProvider(provider)
            repository.resolvePendingProvider(pending.id, accept = true)
            repository.addLog(loggedInUsername, "تم قبول انضمام مقدم الخدمة: ${pending.name}")
        }
    }

    fun rejectPendingRequest(id: Int, reason: String) {
        viewModelScope.launch {
            repository.resolvePendingProvider(id, accept = false, reason = reason)
            repository.addLog(loggedInUsername, "تم رفض الطلب رقم $id للسبب: $reason")
        }
    }

    // --- AppSettings Admin Control ---
    fun updateSystemName(name: String) {
        viewModelScope.launch {
            val current = repository.getAppSettings()
            repository.saveSettings(current.copy(appName = name))
            repository.addLog(loggedInUsername, "تعديل اسم التطبيق إلى: $name")
        }
    }

    fun updateSystemTheme(themeName: String, primaryHex: String, secondaryHex: String) {
        viewModelScope.launch {
            val current = repository.getAppSettings()
            repository.saveSettings(
                current.copy(
                    currentThemeName = themeName,
                    primaryColorHex = primaryHex,
                    secondaryColorHex = secondaryHex
                )
            )
            repository.addLog(loggedInUsername, "تعديل ألوان وثيم التطبيق إلى: $themeName")
        }
    }

    fun updateSystemWelcome(message: String) {
        viewModelScope.launch {
            val current = repository.getAppSettings()
            repository.saveSettings(current.copy(welcomeMessage = message))
            repository.addLog(loggedInUsername, "تعديل نص الترحيب شريط Marquee.")
        }
    }

    fun updateSupportContacts(phone: String, mail: String, whatsapp: String) {
        viewModelScope.launch {
            val current = repository.getAppSettings()
            repository.saveSettings(
                current.copy(
                    supportPhone = phone,
                    supportEmail = mail,
                    supportWhatsApp = whatsapp
                )
            )
            repository.addLog(loggedInUsername, "تعديل بيانات الدعم الفني والمساعدة.")
        }
    }

    fun updateFooterCustom(text: String, sizeFactor: Float, opacity: Float) {
        viewModelScope.launch {
            val current = repository.getAppSettings()
            repository.saveSettings(
                current.copy(
                    footerText = text,
                    footerFontSizeFactor = sizeFactor,
                    footerOpacity = opacity
                )
            )
            repository.addLog(loggedInUsername, "تعديل نص التذييل المطور والشفافية وحجم الخط.")
        }
    }

    fun changeMaintenanceMode(enabled: Boolean) {
        viewModelScope.launch {
            val current = repository.getAppSettings()
            repository.saveSettings(current.copy(isMaintenanceMode = enabled))
            repository.addLog(loggedInUsername, "تبديل وضع الصيانة والإغلاق التام: $enabled")
        }
    }

    // --- Reviews Management ---
    fun submitUserReview(rating: Int, comment: String) {
        val provId = activeReviewProviderId ?: return
        viewModelScope.launch {
            val review = Review(
                providerId = provId,
                userName = "مستخدم خدمات اليمن",
                rating = rating,
                comment = comment
            )
            repository.addReview(review)
            
            // Give extra loyalty points!
            val currentPoints = loyaltyPointsState.value?.points ?: 100
            repository.updatePoints(currentPoints + 15) // +15 points per evaluation as requested
            
            isReviewModalOpen = false
            activeReviewProviderId = null
        }
    }

    // --- Loyalty Reward logic ---
    fun redeemPointsNow() {
        val currentPoints = loyaltyPointsState.value?.points ?: 0
        if (currentPoints >= 100) {
            viewModelScope.launch {
                repository.updatePoints(currentPoints - 100)
                repository.addLog("Loyalty System", "استبدل المستخدم 100 نقطة بخصم معاينة 5000 ريال.")
            }
        }
    }

    // --- Smart Assistant (Chatbot Q&A) ---
    var assistantInput by mutableStateOf("")
    var isAssistantTyping by mutableStateOf(false)
    val assistantMessages = mutableStateOf<List<ChatMessage>>(
        listOf(
            ChatMessage(
                senderName = "الذكاء الاصطناعي 🤖",
                receiverName = "المستخدم",
                text = "مرحباً بكم مع تطبيق كل خدمات اليمن! أنا المساعد الذكي لمساعدتك في إيجاد أفضل المهن وحل أي إشكال بضغطة زر. كيف أخدمك اليوم؟"
            )
        )
    )

    fun sendAssistantMessage(text: String) {
        if (text.trim().isEmpty()) return
        
        // Add user turn
        val userMsg = ChatMessage(senderName = "المستخدم", receiverName = "الذكاء الاصطناعي 🤖", text = text)
        assistantMessages.value = assistantMessages.value + userMsg
        assistantInput = ""
        isAssistantTyping = true

        viewModelScope.launch {
            val settings = repository.getAppSettings()
            val promptContext = "أنت مساعد ذكي لتطبيق 'كل خدمات اليمن' الذي يربط المستفيدين بالمهنيين (سباك، كهربائي، دهان...). أجب بنبرة احترافية يمنية ودائمة المودة والوضوح."
            val response = GeminiService.getAiResponse(text, promptContext)
            
            val aiMsg = ChatMessage(senderName = "الذكاء الاصطناعي 🤖", receiverName = "المستخدم", text = response)
            assistantMessages.value = assistantMessages.value + aiMsg
            isAssistantTyping = false
            
            // Also save messages into room database of chat if required
            repository.sendChatMessage(userMsg)
            repository.sendChatMessage(aiMsg)
        }
    }

    // --- Admin Categories Custom Management ---
    fun addNewCategory(name: String, icon: String) {
        viewModelScope.launch {
            val order = (allCategories.value.maxOfOrNull { it.order } ?: 0) + 1
            repository.addCategory(Category(name = name, icon = icon, order = order))
            repository.addLog(loggedInUsername, "أضاف قسماً جديداً: $name")
        }
    }

    fun removeCategory(id: Int) {
        viewModelScope.launch {
            repository.deleteCategory(id)
            repository.addLog(loggedInUsername, "حذف قسماً رئيسياً رقم $id")
        }
    }

    // --- Admin Banners Custom Management ---
    fun addNewBanner(title: String, text: String) {
        viewModelScope.launch {
            val banner = Banner(title = title, text = text)
            repository.addBanner(banner)
            repository.addLog(loggedInUsername, "تم إضافة لافتة إعلانية ممولة جديدة: $title")
        }
    }

    fun removeBanner(id: Int) {
        viewModelScope.launch {
            repository.deleteBanner(id)
            repository.addLog(loggedInUsername, "حذف إلان بالمعرف $id")
        }
    }

    // --- Simulated Voice Search ---
    fun startVoiceListening() {
        voiceSearchActive = true
        // Simulated Speech To Text
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            voiceSearchActive = false
            searchQuery.value = "صنعاء" // Simulated voice query
        }
    }
}

class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
