package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val MODEL = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun getAiResponse(prompt: String, systemInstruction: String): String = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
        
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "MY_API_KEY") {
            return@withContext getLocalResponse(prompt)
        }

        return@withContext try {
            val systemPart = JSONObject().put("text", systemInstruction)
            val systemContent = JSONObject().put("parts", JSONArray().put(systemPart))

            val userPart = JSONObject().put("text", prompt)
            val userContent = JSONObject().put("parts", JSONArray().put(userPart))

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().put(userContent))
                put("systemInstruction", systemContent)
                put("generationConfig", JSONObject().put("temperature", 0.7))
            }

            val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    Log.e("GeminiService", "API error: $errorBody")
                    return@withContext getLocalResponse(prompt)
                }

                val responseBody = response.body?.string() ?: ""
                val jsonObj = JSONObject(responseBody)
                val candidates = jsonObj.getJSONArray("candidates")
                val content = candidates.getJSONObject(0).getJSONObject("content")
                val parts = content.getJSONArray("parts")
                parts.getJSONObject(0).getString("text")
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Network request failed: ${e.message}", e)
            getLocalResponse(prompt)
        }
    }

    fun getLocalResponse(prompt: String): String {
        return when {
            prompt.contains("الأقسام") || prompt.contains("أقسام") || prompt.contains("أقسام متوفرة") -> {
                "الأقسام المتوفرة حالياً في تطبيق 'كل خدمات اليمن' هي:\n" +
                        "1. سباكة وصيانة الأنابيب (🔧)\n" +
                        "2. كهرباء وتوصيلات (⚡)\n" +
                        "3. دهان وديكور (🎨)\n" +
                        "4. نجارة وتأثيث (🔨)\n" +
                        "5. حدادة ومعادن (⚙️)\n" +
                        "يمكنك مراجعتها وتصفيتها مباشرة من الشاشة الرئيسية للتطبيق."
            }
            prompt.contains("أتصل بمقدم خدمة") || prompt.contains("الاتصال") || prompt.contains("اتصل") -> {
                "يمكنك الاتصال بجميع مزودي الخدمات المعتمدين والموثقين:\n" +
                        "- اضغط على زر الاتصال الأخضر المباشر (📞) لفتح الهاتف والاتصال مباشرة.\n" +
                        "- أو اضغط على زر الواتساب (💬) لبدء التراسل والتفاهم وتفاصيل العمل بشكل آمن."
            }
            prompt.contains("رقم الدعم") || prompt.contains("دعم") || prompt.contains("المساعدة") -> {
                "أهلاً ومرحباً بك! رقم دعم ومساعدة المالك والمشرف الرئيسي (ماهر محمد) هو:\n" +
                        "هاتف/واتساب: 777644670\n" +
                        "البريد الإلكتروني: wam2026@gmail.com"
            }
            prompt.contains("بلاغ") || prompt.contains("إبلاغ") || prompt.contains("أشتكي") || prompt.contains("شكوى") -> {
                "أمان وجودة الخدمات غايتنا! لتقديم شكوى أو بلاغ عن مقدم خدمة:\n" +
                        "1. اضغط على بطاقة فني الخدمة.\n" +
                        "2. انقر على أيقونة الإبلاغ الحمراء (⚠️).\n" +
                        "3. حدد تفاصيل المخالفة ليقوم الأدمن والمالك بمراجعته فوراً واتخاذ قرار حظر المستخدم."
            }
            else -> {
                "أهلاً بك! أنا مساعدك الذكي لتطبيق 'كل خدمات اليمن'. في حال انقطاع اتصالك بالإنترنت، يرجى الاستعانة بالنقاط والأسئلة الشائعة من الشاشة الموجهة لمساعدتك فوراً!"
            }
        }
    }
}
