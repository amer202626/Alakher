package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModelFactory(application)
            )

            val appSettings by viewModel.appSettingsState.collectAsState()
            val safeSettings = appSettings ?: AppSettings()
            
            // Extract core theme colors parsed dynamically from database settings
            val themePrimary = remember(safeSettings.primaryColorHex) {
                try { Color(android.graphics.Color.parseColor(safeSettings.primaryColorHex)) }
                catch (e: Exception) { Color(0x00, 0x69, 0x5C) } // Fallback emerald green
            }
            val themeSecondary = remember(safeSettings.secondaryColorHex) {
                try { Color(android.graphics.Color.parseColor(safeSettings.secondaryColorHex)) }
                catch (e: Exception) { Color(0xFF, 0xB3, 0x00) } // Fallback gold
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = themePrimary,
                    secondary = themeSecondary,
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onPrimary = Color.White,
                    onBackground = Color(0xFFEEEEEE),
                    onSurface = Color(0xFFE0E0E0)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    YemenServicesApp(viewModel = viewModel, themePrimary = themePrimary, themeSecondary = themeSecondary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YemenServicesApp(
    viewModel: MainViewModel,
    themePrimary: Color,
    themeSecondary: Color
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Collect States
    val providers by viewModel.filteredProviders.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    val pendingList by viewModel.pendingProviders.collectAsState()
    val loyaltyState by viewModel.loyaltyPointsState.collectAsState()
    val settingsStateCollected by viewModel.appSettingsState.collectAsState()
    val settingsState = settingsStateCollected ?: AppSettings()
    val bannersList by viewModel.activeBanners.collectAsState()
    val unactiveBannersList by viewModel.allBanners.collectAsState()
    val logsList by viewModel.activityLogs.collectAsState()

    // Screen states
    val selectByCatState by viewModel.selectedCategory.collectAsState()
    val selectByRegionState by viewModel.selectedRegion.collectAsState()
    val searchWordState by viewModel.searchQuery.collectAsState()
    val minStarsState by viewModel.minRatingFilter.collectAsState()
    val moneyFilterState by viewModel.priceFilter.collectAsState()

    // Modals visibility toggles
    var backdoorModalOpen by remember { mutableStateOf(false) }
    var backdoorCodeText by remember { mutableStateOf("") }
    var backdoorError by remember { mutableStateOf("") }
    
    var assistentWindowOpen by remember { mutableStateOf(false) }
    var filtersContainerOpen by remember { mutableStateOf(false) }
    var demoAddProviderModal by remember { mutableStateOf(false) }
    var deleteProviderWarningId by remember { mutableStateOf<Int?>(null) }
    
    // Dynamic home click counter for developer doorway
    var rawLogoClicks by remember { mutableStateOf(0) }

    // Floating action offsets / sizing
    val footerOpacity = settingsState.footerOpacity
    val fontScaling = settingsState.footerFontSizeFactor
    
    // Main Container
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                rawLogoClicks++
                                if (rawLogoClicks >= 5) {
                                    rawLogoClicks = 0
                                    backdoorModalOpen = true
                                    Toast.makeText(context, "البوابة الخلفية السرية مطلوب التحقق!", Toast.LENGTH_SHORT).show()
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = settingsState.appName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = themeSecondary
                        )
                    }
                },
                actions = {
                    // RTL Bar controls
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Refresh
                        IconButton(onClick = {
                            coroutineScope.launch {
                                viewModel.repository.addLog("User Action", "تم تحديث البيانات وقوائم الفنيين.")
                                Toast.makeText(context, "تم تحديث البيانات تلقائياً!", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "تحديث", tint = Color.White)
                        }
                        
                        // Language
                        IconButton(onClick = {
                            viewModel.isLanguageArabic = !viewModel.isLanguageArabic
                            val lang = if (viewModel.isLanguageArabic) "العربية" else "English"
                            Toast.makeText(context, "تم تحويل اللغة إلى $lang", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Filled.Language, contentDescription = "اللغة", tint = Color.White)
                        }

                        // Register Professional 👤
                        IconButton(
                            onClick = { viewModel.currentView = "register" },
                            modifier = Modifier.testTag("nav_register_button")
                        ) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = "تسجيل فني", tint = if (viewModel.currentView == "register") themeSecondary else Color.White)
                        }

                        // Authenticate/Lock 🔐
                        IconButton(
                            onClick = {
                                if (viewModel.isLoggedIn) {
                                    viewModel.currentView = "admin"
                                } else {
                                    viewModel.currentView = "login"
                                }
                            },
                            modifier = Modifier.testTag("nav_login_button")
                        ) {
                            Icon(
                                imageVector = if (viewModel.isLoggedIn) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                contentDescription = "تسجيل الدخول",
                                tint = if (viewModel.currentView == "login" || viewModel.currentView == "admin") themeSecondary else Color.White
                            )
                        }

                        // Home icon 🏠 with logo clicks trigger too
                        IconButton(
                            onClick = {
                                viewModel.currentView = "home"
                                rawLogoClicks++
                                if (rawLogoClicks >= 5) {
                                    rawLogoClicks = 0
                                    backdoorModalOpen = true
                                    Toast.makeText(context, "البوابة الخلفية السرية مطلوب التحقق!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("nav_home_button")
                        ) {
                            Icon(Icons.Filled.Home, contentDescription = "الرئيسية", tint = if (viewModel.currentView == "home") themeSecondary else Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themePrimary
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            // High fidelity bottom Footer bar as requested
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121212))
                    .padding(8.dp)
                    .alpha(footerOpacity),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HorizontalDivider(color = themePrimary.copy(alpha = 0.5f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // About info ℹ️ Left Side
                    IconButton(
                        onClick = { viewModel.currentView = "about" },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("about_app_footer_button")
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = "عن التطبيق", tint = themeSecondary)
                    }

                    // MAW Center watermark with 50% smaller font
                    Text(
                        text = settingsState.footerText,
                        color = Color.Gray,
                        fontSize = (14 * fontScaling).sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center
                    )

                    // Build Release Label Right Side
                    Text(
                        text = "V2.6.2026",
                        color = Color.DarkGray,
                        fontSize = (11 * fontScaling).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        floatingActionButton = {
            // Tiny Chat Assistant Head floating button (🤖)
            FloatingActionButton(
                onClick = { assistentWindowOpen = true },
                containerColor = themePrimary,
                contentColor = themeSecondary,
                shape = CircleShape,
                modifier = Modifier
                    .size(settingsState.chatbotIconSize.dp)
                    .testTag("floating_chatbot_button")
            ) {
                Text(text = "🤖", fontSize = (settingsState.chatbotIconSize / 3).sp)
            }
        }
    ) { innerPadding ->
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF101010))
        ) {
            
            // Render view depending on navigation controller state
            when (viewModel.currentView) {
                "home" -> {
                    HomeScreenContent(
                        viewModel = viewModel,
                        providers = providers,
                        categories = categories,
                        banners = bannersList,
                        loyaltyState = loyaltyState,
                        settingsState = settingsState,
                        themePrimary = themePrimary,
                        themeSecondary = themeSecondary,
                        filtersOpen = filtersContainerOpen,
                        onToggleFilters = { filtersContainerOpen = !filtersContainerOpen }
                    )
                }
                "register" -> {
                    ProviderRegistrationForm(
                        viewModel = viewModel,
                        themePrimary = themePrimary,
                        themeSecondary = themeSecondary
                    )
                }
                "login" -> {
                    AdminLoginForm(
                        viewModel = viewModel,
                        themePrimary = themePrimary,
                        themeSecondary = themeSecondary
                    )
                }
                "admin" -> {
                    if (viewModel.isLoggedIn) {
                        AdminControlPanel(
                            viewModel = viewModel,
                            pendingList = pendingList,
                            allProvidersList = providers,
                            categories = categories,
                            logsList = logsList,
                            bannersList = unactiveBannersList,
                            settingsState = settingsState,
                            themePrimary = themePrimary,
                            themeSecondary = themeSecondary,
                            onOpenAddModal = { demoAddProviderModal = true },
                            onDeleteTrigger = { deleteProviderWarningId = it }
                        )
                    } else {
                        viewModel.currentView = "login"
                    }
                }
                "about" -> {
                    AboutAppScreen(
                        viewModel = viewModel,
                        settingsState = settingsState,
                        themePrimary = themePrimary,
                        themeSecondary = themeSecondary
                    )
                }
            }
            
            // --- Custom Scrolling Marquee banner immediately below the top app bar ---
            // Let's draw it dynamically on top of the screen content depending on scroll
        }
    }

    // --- Developer Doorway / Secret Backdoor verify passcode dialogue ---
    if (backdoorModalOpen) {
        AlertDialog(
            onDismissRequest = { backdoorModalOpen = false },
            title = { Text("فتح بوابة المالك الفني", color = themeSecondary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("الرجاء إدخال الرمز السري للمطور لتعديل معالم النظام والألوان الأساسية والتذييل.", color = Color.LightGray)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = backdoorCodeText,
                        onValueChange = { backdoorCodeText = it },
                        label = { Text("كلمة المرور الخلفية") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = themePrimary,
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("backdoor_password_input")
                    )
                    if (backdoorError.isNotEmpty()) {
                        Text(backdoorError, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val valid = viewModel.submitBackdoorPassword(backdoorCodeText)
                        if (valid) {
                            backdoorModalOpen = false
                            backdoorCodeText = ""
                            backdoorError = ""
                            Toast.makeText(context, "أهلاً بك يا مطور التطبيق في عرش التحكم!", Toast.LENGTH_LONG).show()
                        } else {
                            backdoorError = "الرمز السري غير صالح!"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themePrimary, contentColor = Color.White),
                    modifier = Modifier.testTag("submit_backdoor_button")
                ) {
                    Text("تحقق وولج")
                }
            },
            dismissButton = {
                TextButton(onClick = { backdoorModalOpen = false }) {
                    Text("إلغاء", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    // --- Smart Gemini Assistant (Chatbot overlay window) ---
    if (assistentWindowOpen) {
        Dialog(onDismissRequest = { assistentWindowOpen = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(550.dp)
                    .testTag("chatbot_overlay_window"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                border = BorderStroke(1.dp, themePrimary)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🤖", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("المساعد الذكي لخدمات اليمن", fontWeight = FontWeight.Bold, color = themeSecondary, fontSize = 15.sp)
                                Text("مدعوم بتقنية Gemini الذكية والأوفلاين", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                        IconButton(onClick = { assistentWindowOpen = false }) {
                            Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = Color.LightGray)
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = themePrimary.copy(alpha = 0.3f))
                    
                    // Recommended guided questions chips
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val guidedQ = listOf(
                            "ماهي الأقسام المتوفرة؟",
                            "كيف أتصل بمقدم خدمة؟",
                            "ما هو رقم الدعم؟",
                            "كيف أقدم بلاغ؟"
                        )
                        items(guidedQ) { question ->
                            Card(
                                modifier = Modifier.clickable {
                                    viewModel.sendAssistantMessage(question)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = themePrimary.copy(alpha = 0.2f)),
                                border = BorderStroke(0.5.dp, themePrimary)
                            ) {
                                Text(
                                    text = question,
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Messages logs
                    val logs = viewModel.assistantMessages.value
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF121212), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs) { chMsg ->
                            val isAi = chMsg.senderName.contains("الذكاء الاصطناعي")
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = if (isAi) Alignment.CenterStart else Alignment.CenterEnd
                            ) {
                                Card(
                                    shape = RoundedCornerShape(
                                        topStart = 12.dp,
                                        topEnd = 12.dp,
                                        bottomStart = if (isAi) 0.dp else 12.dp,
                                        bottomEnd = if (isAi) 12.dp else 0.dp
                                    ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isAi) themePrimary.copy(alpha = 0.15f) else Color(0xFF2E2E2E)
                                    ),
                                    border = BorderStroke(0.5.dp, if (isAi) themePrimary else Color.Gray),
                                    modifier = Modifier.widthIn(max = 240.dp)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(chMsg.senderName, fontSize = 10.sp, color = themeSecondary, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(chMsg.text, fontSize = 13.sp, color = Color.White, textAlign = TextAlign.Right)
                                    }
                                }
                            }
                        }
                        
                        if (viewModel.isAssistantTyping) {
                            item {
                                Text("جاري معالجة الإجابة بالذكاء الاصطناعي... 🤖", color = themeSecondary, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Input bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = viewModel.assistantInput,
                            onValueChange = { viewModel.assistantInput = it },
                            placeholder = { Text("أدخل سؤالك هنا...", fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = themePrimary,
                                unfocusedBorderColor = Color.Gray
                            ),
                            maxLines = 2,
                            modifier = Modifier.weight(1f).testTag("chatbot_input_bar")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                viewModel.sendAssistantMessage(viewModel.assistantInput)
                            },
                            enabled = viewModel.assistantInput.isNotEmpty() && !viewModel.isAssistantTyping,
                            modifier = Modifier
                                .background(themePrimary, CircleShape)
                                .size(44.dp)
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "إرسال", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    // --- Rating Review Dialogue ---
    if (viewModel.isReviewModalOpen) {
        var reviewText by remember { mutableStateOf("") }
        var reviewStars by remember { mutableStateOf(5) }
        AlertDialog(
            onDismissRequest = { viewModel.isReviewModalOpen = false },
            title = { Text("تقييم مقدم الخدمة ⭐", color = themeSecondary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("رأيك يهمنا في تقييم جودة عمل فنيي صيانة اليمن. سيمنحك التقييم +15 نقطة ولاء مجانية!", color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    // Stars Selector
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        for (star in 1..5) {
                            IconButton(onClick = { reviewStars = star }) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = "$star Stars",
                                    tint = if (star <= reviewStars) themeSecondary else Color.Gray,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = reviewText,
                        onValueChange = { reviewText = it },
                        label = { Text("أكتب مراجعتك هنا") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themePrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.submitUserReview(reviewStars, reviewText)
                        Toast.makeText(context, "شكراً لتقييمك! كسبت +15 نقطة ولاء بنجاح 🎁", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themePrimary)
                ) {
                    Text("تقديم التقييم")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.isReviewModalOpen = false }) {
                    Text("إلغاء", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    // --- Admin Add Provider Manually Dialogue ---
    if (demoAddProviderModal) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var cat by remember { mutableStateOf("كهرباء وتوصيلات") }
        var address by remember { mutableStateOf("") }
        var district by remember { mutableStateOf("") }
        var feeStr by remember { mutableStateOf("3000") }

        AlertDialog(
            onDismissRequest = { demoAddProviderModal = false },
            title = { Text("إضافة مقدم خدمة يدوياً", color = themeSecondary, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم الكامل") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("رقم الهاتف") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = cat, onValueChange = { cat = it }, label = { Text("القسم الرئيسي") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("عنوان المكتب") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = district, onValueChange = { district = it }, label = { Text("منطقة الدائرة السكنية") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = feeStr, onValueChange = { feeStr = it }, label = { Text("سعر المعاينة الأولية") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotEmpty() && phone.isNotEmpty()) {
                            viewModel.addProviderProvider(name, phone, cat, address, district, feeStr.toIntOrNull() ?: 3000)
                            demoAddProviderModal = false
                            Toast.makeText(context, "تم حفظ مزود الخدمة ونشره فوراً!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themePrimary)
                ) {
                    Text("حفظ ونشر")
                }
            },
            dismissButton = {
                TextButton(onClick = { demoAddProviderModal = false }) { Text("إلغاء", color = Color.Gray) }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    // Delete Provider confirmation dialogue
    if (deleteProviderWarningId != null) {
        val provId = deleteProviderWarningId!!
        AlertDialog(
            onDismissRequest = { deleteProviderWarningId = null },
            title = { Text("تأكيد حذف الفني ⚠️", color = Color.Red) },
            text = { Text("هل أنت متأكد تماماً من رغبتك في حذف بطاقة مزود الخدمة وملفه نهائياً من قاعدة بيانات خدمات اليمن؟", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProviderProvider(provId)
                        deleteProviderWarningId = null
                        Toast.makeText(context, "تم حذف الفني بنجاح.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("نعم، احذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteProviderWarningId = null }) { Text("تراجع", color = Color.White) }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }
}

// Extends viewModel functions to compile correctly w/ custom named methods if required
private fun MainViewModel.addProviderProvider(name: String, phone: String, cat: String, address: String, district: String, fee: Int) {
    this.addProviderDirectly(name, phone, cat, address, district, fee)
}

private fun MainViewModel.deleteProviderProvider(id: Int) {
    viewModelScope.launch {
        this@deleteProviderProvider.repository.deleteProvider(id)
        this@deleteProviderProvider.repository.addLog(this@deleteProviderProvider.loggedInUsername, "تم حذف بطاقة فني بالمعرف: $id")
    }
}

// --- Dynamic Marquee welcome Text component ---
@Composable
fun RunningMarqueeText(text: String, primaryColor: Color) {
    val scrollState = rememberScrollState()
    var shouldAnimate by remember { mutableStateOf(true) }
    
    LaunchedEffect(key1 = shouldAnimate) {
        while (shouldAnimate) {
            scrollState.animateScrollTo(
                value = scrollState.maxValue,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 9000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
            delay(10)
            scrollState.scrollTo(0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(primaryColor.copy(alpha = 0.85f))
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState, enabled = false),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Padding to start off-screen
            Spacer(modifier = Modifier.width(300.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Visible
            )
            Spacer(modifier = Modifier.width(300.dp))
        }
    }
}

// --- Main screen display layouts ---
@Composable
fun HomeScreenContent(
    viewModel: MainViewModel,
    providers: List<ServiceProvider>,
    categories: List<Category>,
    banners: List<Banner>,
    loyaltyState: LoyaltyPoints?,
    settingsState: AppSettings,
    themePrimary: Color,
    themeSecondary: Color,
    filtersOpen: Boolean,
    onToggleFilters: () -> Unit
) {
    val context = LocalContext.current
    val selectByCatState by viewModel.selectedCategory.collectAsState()
    val selectByRegionState by viewModel.selectedRegion.collectAsState()
    val searchWordState by viewModel.searchQuery.collectAsState()
    val minStarsState by viewModel.minRatingFilter.collectAsState()
    val moneyFilterState by viewModel.priceFilter.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // 1. Text welcome horizontal marquee text immediately under top bar
        item {
            RunningMarqueeText(text = settingsState.welcomeMessage, primaryColor = themePrimary)
        }

        // 2. Sponsored sponsored banners slider
        if (banners.isNotEmpty()) {
            item {
                val banner = banners.first() // Select main sponsored banner
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .testTag("sponsored_banner"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = themePrimary.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, themeSecondary.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Badge(containerColor = themeSecondary, contentColor = Color.Black) {
                                Text("إعلان ممول", fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp), fontSize = 10.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(banner.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(banner.text, fontSize = 13.sp, color = Color.LightGray)
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(banner.actionUrl))
                                try { context.startActivity(intent) } catch (e: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = themeSecondary, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.End).height(36.dp)
                        ) {
                            Text("الذهاب الآن", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. Search and Quick Filters Row
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchWordState,
                            onValueChange = { viewModel.searchQuery.value = it },
                            placeholder = { Text("ابحث عن سباك، كهربائي، صنعاء...", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "بحث", tint = themeSecondary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = themePrimary,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            modifier = Modifier.weight(1f).testTag("search_field_input")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Voice Search Button (Mic icon 🎤)
                        IconButton(
                            onClick = { viewModel.startVoiceListening() },
                            modifier = Modifier
                                .background(themeSecondary.copy(alpha = 0.15f), CircleShape)
                                .size(48.dp)
                                .testTag("voice_search_mic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "بحث صوتي",
                                tint = if (viewModel.voiceSearchActive) Color.Red else themeSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))
                        
                        // Filters switch toggle
                        IconButton(
                            onClick = onToggleFilters,
                            modifier = Modifier
                                .background(themePrimary.copy(alpha = 0.15f), CircleShape)
                                .size(48.dp)
                        ) {
                            Icon(Icons.Filled.FilterList, contentDescription = "فلاتر", tint = themePrimary)
                        }
                    }
                    
                    if (viewModel.voiceSearchActive) {
                        Text("جاري الاستماع للبحث الصوتي... تحدث الآن 🎤", color = themeSecondary, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }

                    // Collapsible Advanced filters options container
                    if (filtersOpen) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Text("خيارات البحث المتقدم للأقسام:", color = themeSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        // District selector
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("الموقع الجغرافي:", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(90.dp))
                            val regions = listOf("الكل", "الحصبة", "حدة", "الدائري", "الستين")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(regions) { reg ->
                                    val isSel = selectByRegionState == reg
                                    Card(
                                        modifier = Modifier.clickable { viewModel.selectedRegion.value = reg },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSel) themePrimary else Color(0xFF2E2E2E)
                                        )
                                    ) {
                                        Text(reg, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Price selector ("منخفض", "متوسط", "مرتفع")
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("مستوى التكلفة:", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(90.dp))
                            val prices = listOf("الكل", "منخفض", "متوسط", "مرتفع")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(prices) { pr ->
                                    val isSel = moneyFilterState == pr
                                    Card(
                                        modifier = Modifier.clickable { viewModel.priceFilter.value = pr },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSel) themePrimary else Color(0xFF2E2E2E)
                                        )
                                    ) {
                                        Text(pr, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Rating stars selector
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("التقييم الأدنى:", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(90.dp))
                            val stars = listOf(0f, 3f, 4f, 4.5f)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(stars) { star ->
                                    val isSel = minStarsState == star
                                    val token = if (star == 0f) "الكل" else "★ $star+"
                                    Card(
                                        modifier = Modifier.clickable { viewModel.minRatingFilter.value = star },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSel) themePrimary else Color(0xFF2E2E2E)
                                        )
                                    ) {
                                        Text(token, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. elite VIP recommended horizontal list
        val recommendedProviders = providers.filter { it.isRecommended }
        if (recommendedProviders.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(
                        text = "★ نخبة VIP والنشطين الموصى بهم",
                        color = themeSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(recommendedProviders) { p ->
                            Card(
                                modifier = Modifier
                                    .width(200.dp)
                                    .clickable {
                                        // Auto-filter by this name as visual detail inspection
                                        viewModel.searchQuery.value = p.name
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                                border = BorderStroke(1.dp, themeSecondary)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("VIP", color = themeSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Icon(Icons.Filled.Star, contentDescription = "", tint = themeSecondary, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(p.category, fontSize = 11.sp, color = Color.LightGray)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.LocationOn, contentDescription = "", tint = themePrimary, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(p.district, fontSize = 10.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Quick Circular Categories filtering List
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("categories_circular_list")
                ) {
                    items(categories) { cat ->
                        val isSelected = selectByCatState == cat.name
                        Card(
                            onClick = {
                                viewModel.selectedCategory.value = cat.name
                            },
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, if (isSelected) themeSecondary else Color.Transparent),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) themePrimary else Color(0xFF1E1E1E)
                            ),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(cat.icon, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(cat.name, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // 6. Loyalty Point balance card section
        if (settingsState.isLoyaltyPointsVisible) {
            item {
                val pt = loyaltyState?.points ?: 100
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("loyalty_card"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2A3A)),
                    border = BorderStroke(1.dp, themePrimary)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎁", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("رصيد نقاط الولاء الخاص بك: $pt نقطة", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                Text("كل تقييم أو مراجعة يمنحك +15 نقطة مجانية!", fontSize = 10.sp, color = Color.LightGray)
                            }
                        }
                        
                        Button(
                            onClick = { viewModel.isLoyaltyModalOpen = true },
                            colors = ButtonDefaults.buttonColors(containerColor = themeSecondary, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("استبدال خصم", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 7. Empty State check if providers filtered list is clear
        if (providers.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🔍", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("عذراً، لم نجد أي مقدمي خدمة متاحين حالياً!", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                    Text("يرجى تغيير فلاتر البحث أو البحث عن موقع آخر لصيانتك.", color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        } else {
            // 8. Service Provider Core card listing
            items(providers) { provider ->
                ServiceProviderRowCard(
                    provider = provider,
                    themePrimary = themePrimary,
                    themeSecondary = themeSecondary,
                    onOpenReview = {
                        viewModel.activeReviewProviderId = provider.id
                        viewModel.isReviewModalOpen = true
                    },
                    viewModel = viewModel
                )
            }
        }
    }

    // Loyalty Redemption dialogue info
    if (viewModel.isLoyaltyModalOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.isLoyaltyModalOpen = false },
            title = { Text("استبدال رصيد نقاط الولاء 🎁", color = themeSecondary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    val pts = loyaltyState?.points ?: 100
                    Text("قيمة استبدال النقاط الحالية:\n- 100 نقطة ولاء = 5000 ريال يمني خصم على أي معاينة أولية لفني معتمد وصاحب مهنة في التطبيق.", color = Color.White, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("رصيدك الحالي: $pts نقطة", color = themeSecondary, fontWeight = FontWeight.Bold)
                    if (pts < 100) {
                        Text("\nيرجى تجميع 100 نقطة على الأقل للاستبدال! كسب النقاط سهل جداً عبر تقييم الفنيين.", color = Color.Red, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pts = loyaltyState?.points ?: 100
                        if (pts >= 100) {
                            viewModel.redeemPointsNow()
                            viewModel.isLoyaltyModalOpen = false
                            Toast.makeText(context, "تم خصم 100 نقطة بنجاح! كود خصم الخمسة آلاف الخاص بك هو: YER-5000-MAW", Toast.LENGTH_LONG).show()
                        } else {
                            viewModel.isLoyaltyModalOpen = false
                        }
                    },
                    enabled = (loyaltyState?.points ?: 100) >= 100,
                    colors = ButtonDefaults.buttonColors(containerColor = themePrimary)
                ) {
                    Text("استبدل الخصم الآن")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.isLoyaltyModalOpen = false }) {
                    Text("إلغاء", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }
}

// --- List Card design layout with dynamic Call/Whatsapp/Map bindings as instructed ---
@Composable
fun ServiceProviderRowCard(
    provider: ServiceProvider,
    themePrimary: Color,
    themeSecondary: Color,
    onOpenReview: () -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("provider_card_${provider.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        border = BorderStroke(1.dp, if (provider.isRecommended) themeSecondary else Color.DarkGray)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: Logo/Avatar + Names + verified check badge + VIP status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Quick Rounded Avatar Letter or Emoji
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(themePrimary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (provider.avatarUrl.isNotEmpty()) provider.avatarUrl else "👨‍🔧",
                            fontSize = 20.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = provider.name,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            // Blue Verified account stamp
                            if (provider.isVerified) {
                                Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = "موثّق",
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(provider.category, fontSize = 11.sp, color = Color.LightGray)
                    }
                }
                
                // VIP Label tag block
                if (provider.isRecommended) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = themeSecondary.copy(alpha = 0.15f)),
                        border = BorderStroke(0.5.dp, themeSecondary)
                    ) {
                        Text(
                            "نخبة VIP",
                            color = themeSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Location and dynamic availability badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = "", tint = themePrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(provider.address, fontSize = 12.sp, color = Color.LightGray)
                }
                
                // Availability badge (متاح للعمل ✓ or غير متاح)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (provider.isAvailable) Color.Green else Color.Red, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (provider.isAvailable) "متاح للعمل ✓" else "غير متاح حالياً",
                        color = if (provider.isAvailable) Color.Green else Color.Red,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 3: Rating stars values + inspecting fee
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ratings display
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = "", tint = themeSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${provider.rating} (${provider.reviewCount} تقييم)",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                // Inspecting fee
                Text(
                    text = "سعر المعاينة الأولي: ${provider.fee} ر.ي",
                    fontSize = 12.sp,
                    color = themeSecondary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Row 4: Interactive buttons (Call, WhatsApp, Location Google Map redirection, evaluation)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Call 📞 Button
                Button(
                    onClick = {
                        val number = provider.phone
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:$number")
                        }
                        try {
                            context.startActivity(intent)
                            coroutineScope.launch {
                                viewModel.repository.addLog("Call Logs", "بدء مكالمة هاتفية مع الفني ${provider.name}")
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "لم يتم العثور على تطبيق اتصال!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(38.dp).testTag("provider_call_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Call, contentDescription = "اتصال", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("اتصل 📞", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                // WhatsApp 💬 Button
                Button(
                    onClick = {
                        val trimmedPhone = provider.phone.trim()
                        val waUrl = "https://api.whatsapp.com/send?phone=967$trimmedPhone&text=مرحباً بك من تطبيق كل خدمات اليمن! أود الاستفسار عن خدمتك وصيانتك المتاحة."
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "يرجى تثبيت تطبيق واتساب للتواصل المباشر!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(38.dp).testTag("provider_whatsapp_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("واتساب 💬", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                // Map Directions 📍 Button
                Button(
                    onClick = {
                        val queryLocation = Uri.encode(provider.address)
                        val gMapUrl = "geo:0,0?q=$queryLocation"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(gMapUrl))
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "خرائط جوجل غير متوفرة!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0E0E0), contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Directions, contentDescription = "الاتجاهات", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("الاتجاهات 📍", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                // Rate ⭐ Button
                Button(
                    onClick = onOpenReview,
                    colors = ButtonDefaults.buttonColors(containerColor = themeSecondary, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(38.dp).testTag("provider_review_button")
                ) {
                    Text("تقييم ⭐", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

// --- Join Request Profession Form (👤) ---
@Composable
fun ProviderRegistrationForm(
    viewModel: MainViewModel,
    themePrimary: Color,
    themeSecondary: Color
) {
    val context = LocalContext.current
    var uploadDialogMale by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "تسجيل أصحاب المهن للانضمام",
            fontWeight = FontWeight.Bold,
            color = themeSecondary,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
        Text(
            "الرجاء إدخال بيانات دقيقة لترشيح ملفك والمراجعة المباشرة من قبل المشرفين.",
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Name
                OutlinedTextField(
                    value = viewModel.regName,
                    onValueChange = { viewModel.regName = it },
                    label = { Text("الاسم الثلاثي الكامل (إجباري)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themePrimary),
                    modifier = Modifier.fillMaxWidth().testTag("reg_name_input")
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Phone
                OutlinedTextField(
                    value = viewModel.regPhone,
                    onValueChange = { viewModel.regPhone = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    label = { Text("رقم الهاتف الفعال أو واتساب (إجباري)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themePrimary),
                    modifier = Modifier.fillMaxWidth().testTag("reg_phone_input")
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Category selection dropdown simulated
                Text("القسم والخدمة الرئيسية (إجباري):", fontSize = 12.sp, color = themeSecondary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                val catsList = listOf("سباكة وصيانة الأنابيب", "كهرباء وتوصيلات", "دهان وديكور", "نجارة وتأثيث", "حدادة ومعادن")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(catsList) { itemCat ->
                        val isSelected = viewModel.regCategory == itemCat
                        Card(
                            modifier = Modifier.clickable { viewModel.regCategory = itemCat },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) themePrimary else Color(0xFF2E2E2E)
                            )
                        ) {
                            Text(itemCat, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Work center address or office Location
                OutlinedTextField(
                    value = viewModel.regAddress,
                    onValueChange = { viewModel.regAddress = it },
                    label = { Text("مكان وعنوان مكتب العمل الحالي (إجباري)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themePrimary),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Region/Neighborhood Residential circle
                OutlinedTextField(
                    value = viewModel.regDistrict,
                    onValueChange = { viewModel.regDistrict = it },
                    label = { Text("منطقة الدائرة السكنية الحالية (إجباري)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themePrimary),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Gender Toggle to solve avatar photo requirement for girls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("انثى؟ (اختياري لرفع صورة تعبر عن المهنة)", fontSize = 12.sp, color = Color.White)
                    Switch(
                        checked = viewModel.regGenderIsFemale,
                        onCheckedChange = { viewModel.regGenderIsFemale = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = themeSecondary)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Avatar / Profile Pic uploading (MEN required, GIRLs optional)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (viewModel.regGenderIsFemale) "صورة تعبر عن مهنتك (اختياري)" else "تحميل صورتك الشخصية (إجباري للرجال)",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = if (viewModel.regAvatarUri.isNotEmpty()) "تم إرفاق الصورة ✓" else "لم يتم الرفع",
                            fontSize = 12.sp,
                            color = if (viewModel.regAvatarUri.isNotEmpty()) Color.Green else Color.Red,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.regAvatarUri = if (viewModel.regGenderIsFemale) "👩‍💼" else "👤"
                            Toast.makeText(context, "تم رفع الصورة الرمزية بنجاح!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = themePrimary)
                    ) {
                        Text("التقاط / رفع", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ID identity Card upload optional
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("صورة بطاقة الهوية الشخصية (اختياري)", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            text = if (viewModel.regIdUri.isNotEmpty()) "تم إرفاق البطاقة ✓" else "غير مرفق",
                            fontSize = 12.sp,
                            color = if (viewModel.regIdUri.isNotEmpty()) Color.Green else Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.regIdUri = "ID"
                            Toast.makeText(context, "تم التصوير بنجاح!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = themePrimary)
                    ) {
                        Text("رفع الكارت", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Submit request Button
                Button(
                    onClick = {
                        viewModel.submitJoinRequest()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeSecondary, contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("submit_join_request_button")
                ) {
                    Text("تقديم طلب الانضمام للمراجعة الفورية", fontWeight = FontWeight.Bold)
                }

                if (viewModel.registrationSuccessMsg.isNotEmpty()) {
                    Text(
                        text = viewModel.registrationSuccessMsg,
                        color = themeSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    )
                }
            }
        }
    }
}

// --- Admin/Owner Login screen (🔐) ---
@Composable
fun AdminLoginForm(
    viewModel: MainViewModel,
    themePrimary: Color,
    themeSecondary: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🔐 تسجيل دخول الإدارة والمشرفين", fontWeight = FontWeight.Bold, color = themeSecondary, fontSize = 20.sp)
        Text("الولوج الآمن وبوابة ترشيح الفنيين والنسخ الاحتياطي.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = viewModel.adminUsernameField,
                    onValueChange = { viewModel.adminUsernameField = it },
                    label = { Text("اسم المستخدم (WAM2026)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themePrimary),
                    modifier = Modifier.fillMaxWidth().testTag("admin_username_input")
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = viewModel.adminPasswordField,
                    onValueChange = { viewModel.adminPasswordField = it },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    label = { Text("الرمز السري (maher736462)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themePrimary),
                    modifier = Modifier.fillMaxWidth().testTag("admin_password_input")
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Remember Me check box as requested
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Checkbox(
                        checked = viewModel.rememberMeState,
                        onCheckedChange = { viewModel.rememberMeState = it },
                        colors = CheckboxDefaults.colors(checkedColor = themePrimary)
                    )
                    Text("تذكرني في هذا الجهاز", color = Color.LightGray, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (viewModel.loginErrorMessage.isNotEmpty()) {
                    Text(viewModel.loginErrorMessage, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                }

                Button(
                    onClick = { viewModel.performAdminLogin() },
                    colors = ButtonDefaults.buttonColors(containerColor = themePrimary),
                    modifier = Modifier.fillMaxWidth().testTag("admin_login_submit")
                ) {
                    Text("تسجيل وحفظ الجلسة")
                }
            }
        }
    }
}

// --- Multi-tab Admin & Backdoor Control Panel ---
@Composable
fun AdminControlPanel(
    viewModel: MainViewModel,
    pendingList: List<PendingProvider>,
    allProvidersList: List<ServiceProvider>,
    categories: List<Category>,
    logsList: List<ActivityLog>,
    bannersList: List<Banner>,
    settingsState: AppSettings,
    themePrimary: Color,
    themeSecondary: Color,
    onOpenAddModal: () -> Unit,
    onDeleteTrigger: (Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Tab Headers scroll
        ScrollableTabRow(
            selectedTabIndex = when (viewModel.adminActiveTab) {
                "dashboard" -> 0
                "categories" -> 1
                "providers" -> 2
                "banners" -> 3
                "settings" -> 4
                "logs" -> 5
                else -> 0
            },
            containerColor = themePrimary,
            contentColor = Color.White
        ) {
            val tabs = listOf(
                "dashboard" to "الأحصائيات 📊",
                "categories" to "الأقسام 🔧",
                "providers" to "الفنيين 👥",
                "banners" to "الإعلانات 📢",
                "settings" to "الإعدادات ⚙️",
                "logs" to "السجلات 📋"
            )
            tabs.forEachIndexed { i, (key, label) ->
                Tab(
                    selected = viewModel.adminActiveTab == key,
                    onClick = { viewModel.adminActiveTab = key },
                    text = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            
            // Header: Logged user badge
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("الجلسة: " + viewModel.loggedInUsername, fontSize = 13.sp, color = themeSecondary, fontWeight = FontWeight.SemiBold)
                    Button(
                        onClick = { viewModel.logout() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("خروج 🚪", fontSize = 12.sp)
                    }
                }
            }

            when (viewModel.adminActiveTab) {
                "dashboard" -> {
                    // TAB 1: Dashboard graphs & counters
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("📈 ملخص لوحة المعلومات الفورية", color = themeSecondary, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("عدد مقدمي الخدمة:", color = Color.Gray, fontSize = 11.sp)
                                        Text("${allProvidersList.size} فنيين", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Column {
                                        Text("طلبات معلقة:", color = Color.Gray, fontSize = 11.sp)
                                        Text("${pendingList.size} طلبات", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeSecondary)
                                    }
                                    Column {
                                        Text("النقاط بالاستبدال:", color = Color.Gray, fontSize = 11.sp)
                                        Text("مفعل ✓", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Green)
                                    }
                                }
                            }
                        }
                    }
                    
                    // Chart representing percentages as requested: (سباكة 45%, كهرباء 30%, دهان 20%, نجارة 5%)
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("الأقسام الأكثر نشاطاً ونسب الاتصالات:", color = themeSecondary, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                val shares = listOf(
                                    Triple("سباكة وصيانة الأنابيب", 45, Color(0xFF2196F3)),
                                    Triple("كهرباء وتوصيلات", 30, Color(0xFFFF9800)),
                                    Triple("دهان وديكور", 20, Color(0xFFE91E63)),
                                    Triple("نجارة وتأثيث", 5, Color(0xFF4CAF50))
                                )
                                shares.forEach { (title, pct, color) ->
                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text(title, color = Color.White, fontSize = 12.sp)
                                            Text("$pct%", color = themeSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        // Visual bar chart
                                        Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.DarkGray)) {
                                            Box(modifier = Modifier.fillMaxWidth((pct / 100f)).height(8.dp).background(color))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "categories" -> {
                    // TAB 2: Edit categories lists
                    item {
                        var catName by remember { mutableStateOf("") }
                        var catIcon by remember { mutableStateOf("🔧") }
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF191919))) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("إضافة قسم جديد مخصص:", fontWeight = FontWeight.Bold, color = themeSecondary)
                                OutlinedTextField(value = catName, onValueChange = { catName = it }, label = { Text("اسم القسم") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(value = catIcon, onValueChange = { catIcon = it }, label = { Text("الرمز التعبيري (أيقونة)") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        if (catName.isNotEmpty()) {
                                            viewModel.addNewCategory(catName, catIcon)
                                            catName = ""
                                            Toast.makeText(context, "تم حفظ القسم الإداري!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = themePrimary)
                                ) {
                                    Text("أضف القسم")
                                }
                            }
                        }
                    }

                    items(categories) { cat ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E2E))) {
                            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(cat.icon, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(cat.name, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = { viewModel.removeCategory(cat.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Red)
                                }
                            }
                        }
                    }
                }

                "providers" -> {
                    // TAB 3: Handle pending providers or update active providers (Pin, Verify, Recommended VIP)
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("الطلبات المعلقة (${pendingList.size})", color = themeSecondary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Button(onClick = onOpenAddModal, colors = ButtonDefaults.buttonColors(containerColor = themeSecondary, contentColor = Color.Black)) {
                                Text("+ إضافة فني يدوياً")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    if (pendingList.isEmpty()) {
                        item {
                            Text("لا توجد طلبات انضمام معلقة للمراجعة حالياً.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
                        }
                    } else {
                        items(pendingList) { pending ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF262626)),
                                border = BorderStroke(1.dp, themePrimary)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("طلب انضمام من: " + pending.name, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("القسم المختار: " + pending.category, fontSize = 11.sp, color = themeSecondary)
                                    Text("العنوان: " + pending.address + " | " + pending.district, fontSize = 11.sp, color = Color.LightGray)
                                    Text("الهاتف: " + pending.phone, fontSize = 11.sp, color = Color.LightGray)
                                    
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.acceptPendingRequest(pending) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Green, contentColor = Color.Black)
                                        ) { Text("قبول الآن", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                        
                                        Button(
                                            onClick = { viewModel.rejectPendingRequest(pending.id, "لم يستوفِ الملف كامل الشروط") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                                        ) { Text("رفض الطلب", fontSize = 11.sp) }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("إدارة الفنيين النشطين وثلاثية الأوسمة (تثبيت، توصية، توثيق):", color = themeSecondary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(allProvidersList) { provider ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(provider.name, fontWeight = FontWeight.Bold, color = Color.White)
                                    IconButton(onClick = { onDeleteTrigger(provider.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = Color.Red, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Text(provider.category + " | " + provider.district, fontSize = 11.sp, color = Color.Gray)
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Pinned (isPinned)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = provider.isPinned,
                                            onCheckedChange = { viewModel.toggleProviderPin(provider.id, it) },
                                            colors = CheckboxDefaults.colors(checkedColor = themePrimary)
                                        )
                                        Text("تثبيت 📌", fontSize = 11.sp, color = Color.White)
                                    }
                                    
                                    // Recommended VIP (isRecommended)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = provider.isRecommended,
                                            onCheckedChange = { viewModel.toggleProviderRecommendation(provider.id, it) },
                                            colors = CheckboxDefaults.colors(checkedColor = themeSecondary)
                                        )
                                        Text("توصية VIP ⭐", fontSize = 11.sp, color = Color.White)
                                    }

                                    // Verified Badge (isVerified)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = provider.isVerified,
                                            onCheckedChange = { viewModel.toggleProviderVerification(provider.id, it) },
                                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF2196F3))
                                        )
                                        Text("توثيق 🛡️", fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                "banners" -> {
                    // TAB 4: Manage banners
                    item {
                        var bannerTitle by remember { mutableStateOf("") }
                        var bannerText by remember { mutableStateOf("") }
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF191919))) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("إضافة إعلان لافتة علوية ممولة:", fontWeight = FontWeight.Bold, color = themeSecondary)
                                OutlinedTextField(value = bannerTitle, onValueChange = { bannerTitle = it }, label = { Text("عنوان الاعلان") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(value = bannerText, onValueChange = { bannerText = it }, label = { Text("تفاصيل أو نص الاعلان") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        if (bannerTitle.isNotEmpty()) {
                                            viewModel.addNewBanner(bannerTitle, bannerText)
                                            bannerTitle = ""
                                            bannerText = ""
                                            Toast.makeText(context, "تم حفظ الإعلان ونشره!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = themePrimary)
                                ) {
                                    Text("نشر الإعلان العلوي")
                                }
                            }
                        }
                    }

                    items(bannersList) { ban ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF222222))) {
                            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ban.title, color = themeSecondary, fontWeight = FontWeight.Bold)
                                    Text(ban.text, color = Color.White, fontSize = 12.sp)
                                }
                                IconButton(onClick = { viewModel.removeBanner(ban.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Red)
                                }
                            }
                        }
                    }
                }

                "settings" -> {
                    // TAB 5: Manage settings configurations (Backdoor theme switching & opacity)
                    item {
                        var nameInput by remember { mutableStateOf(settingsState.appName) }
                        var supportMail by remember { mutableStateOf(settingsState.supportEmail) }
                        var footerTextInput by remember { mutableStateOf(settingsState.footerText) }
                        
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("التحكم في معالم وهيكل النظام (المالك الفني):", color = themeSecondary, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                OutlinedTextField(
                                    value = nameInput,
                                    onValueChange = { nameInput = it; viewModel.updateSystemName(it) },
                                    label = { Text("اسم التطبيق المباشر") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = supportMail,
                                    onValueChange = { supportMail = it; viewModel.updateSupportContacts(settingsState.supportPhone, it, settingsState.supportWhatsApp) },
                                    label = { Text("إيميل الدعم المباشر") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = footerTextInput,
                                    onValueChange = { footerTextInput = it; viewModel.updateFooterCustom(it, settingsState.footerFontSizeFactor, settingsState.footerOpacity) },
                                    label = { Text("صناعة التذييل والماركة المعلم") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                // Fast color palette switches as requested (كوزميك سيلفر بتدرجات فضية, ذهبي فاخر, زمردي راقي الملكي)
                                Text("تعديل الثيم اللوني الموحد للتطبيق:", color = Color.Gray, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(6.dp))

                                val themes = listOf(
                                    Triple("زمردي راقي الملكي 💚", "#00695C", "#FFB300"),
                                    Triple("ذهبي فاخر 💛", "#455A64", "#FFC107"),
                                    Triple("كوزميك سيلفر بتدرجات فضية 🩶", "#212121", "#CFD8DC"),
                                    Triple("أصفر أنيق وبراق 💛", "#000000", "#FFEB3B")
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(themes) { (name, hex1, hex2) ->
                                        Button(
                                            onClick = {
                                                viewModel.updateSystemTheme(name, hex1, hex2)
                                                Toast.makeText(context, "تم تبديل الثيم إلى: $name", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(android.graphics.Color.parseColor(hex1))
                                            )
                                        ) {
                                            Text(name, fontSize = 10.sp, color = Color.White)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Footer Transparency & Font controls
                                Text("تغيير معامل شفافية التذييل: ${settingsState.footerOpacity}", color = Color.White, fontSize = 12.sp)
                                Slider(
                                    value = settingsState.footerOpacity,
                                    onValueChange = {
                                        viewModel.updateFooterCustom(settingsState.footerText, settingsState.footerFontSizeFactor, it)
                                    },
                                    valueRange = 0.2f..1.0f,
                                    colors = SliderDefaults.colors(thumbColor = themeSecondary, activeTrackColor = themePrimary)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Maintenance Mode Switch
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("وضع الصيانة لإيقاف استقبال الطلبات", color = Color.White, fontSize = 12.sp)
                                    Switch(
                                        checked = settingsState.isMaintenanceMode,
                                        onCheckedChange = { viewModel.changeMaintenanceMode(it) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = themeSecondary)
                                    )
                                }
                            }
                        }
                    }
                }

                "logs" -> {
                    // TAB 6: Sytem Activity Logs to trace changes
                    items(logsList) { log ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(log.adminName, fontWeight = FontWeight.Bold, color = themeSecondary, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(log.action, color = Color.White, fontSize = 13.sp)
                                Text("الوقت: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ENGLISH).format(log.timestamp)}", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- About Application info screen (ℹ️) ---
@Composable
fun AboutAppScreen(
    viewModel: MainViewModel,
    settingsState: AppSettings,
    themePrimary: Color,
    themeSecondary: Color
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo illustration icon placeholder
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(themePrimary.copy(alpha = 0.15f), CircleShape)
                .border(2.dp, themeSecondary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("🇾🇪", fontSize = 38.sp)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = settingsState.appName,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 20.sp
        )
        Text(
            text = "منصة صيانة وربط المهن المتكاملة في اليمن الأولى",
            color = themeSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("📋 تفاصيل ومعلومات الدعم الفني المباشر:", fontWeight = FontWeight.Bold, color = themeSecondary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("رقم هاتف الدعم:", color = Color.Gray, fontSize = 12.sp)
                    Text(settingsState.supportPhone, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("راسلنا واتساب:", color = Color.Gray, fontSize = 12.sp)
                    Text(settingsState.supportWhatsApp, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("البريد الإلكتروني:", color = Color.Gray, fontSize = 12.sp)
                    Text(settingsState.supportEmail, color = Color.White, fontSize = 13.sp)
                }

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("نسخة إصدار النظام:", color = Color.Gray, fontSize = 12.sp)
                    Text("V2.6.2026 Build Pro", color = Color.White, fontSize = 13.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Button(
            onClick = { viewModel.currentView = "home" },
            colors = ButtonDefaults.buttonColors(containerColor = themePrimary, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("العودة إلى الصفحة الرئيسية 🏠")
        }
    }
}
