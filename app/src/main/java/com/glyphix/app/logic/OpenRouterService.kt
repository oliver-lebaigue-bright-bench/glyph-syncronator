package com.glyphix.app.logic

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.glyphix.app.model.DeviceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.*

data class AiPresetResult(
    val name: String,
    val description: String,
    val decayAlpha: Double,
    val zones: List<AudioProcessor.ZoneSpec>,
    val isOfflineGenerated: Boolean = false
)

class OpenRouterService(private val context: Context) {

    companion object {
        private const val TAG = "OpenRouterService"
        private const val PREFS_NAME = "glyphix_ai_prefs"
        private const val KEY_USER_API_KEY = "openrouter_user_key"
        private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

        // Live verified 100% Free models sequence on OpenRouter
        val FREE_MODELS = listOf(
            "nvidia/nemotron-3.5-lightning:free",
            "openrouter/free",
            "inclusionai/ling-3.0-flash-vl:free",
            "nex-agi/nex-n2.5-mini:free"
        )

        // Encrypted/Obfuscated built-in OpenRouter key
        private val OBFUSCATED_KEY = byteArrayOf(
            72, 55, 50, 17, 88, 96, 31, 35, 101, 21, 22, 53, 89, 1, 126, 110,
            13, 100, 38, 27, 28, 40, 12, 36, 41, 65, 24, 48, 87, 85, 121, 108,
            13, 107, 43, 70, 25, 47, 94, 42, 125, 68, 17, 101, 13, 82, 45, 108,
            8, 100, 43, 31, 19, 41, 81, 43, 120, 74, 23, 108, 15, 9, 120, 59,
            94, 56, 41, 73, 30, 117, 93, 115, 42
        )

        private val KEY_MASK = byteArrayOf(
            0x3B, 0x5C, 0x1F, 0x7E, 0x2A, 0x4D, 0x69, 0x12,
            0x48, 0x73, 0x20, 0x54, 0x6E, 0x31, 0x4B, 0x5A
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getApiKey(): String {
        val userKey = prefs.getString(KEY_USER_API_KEY, "") ?: ""
        if (userKey.isNotBlank()) return userKey

        return try {
            val bytes = ByteArray(OBFUSCATED_KEY.size)
            for (i in OBFUSCATED_KEY.indices) {
                bytes[i] = (OBFUSCATED_KEY[i].toInt() xor KEY_MASK[i % KEY_MASK.size].toInt()).toByte()
            }
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt built-in key", e)
            ""
        }
    }

    private fun extractJsonObject(rawContent: String): JSONObject? {
        if (rawContent.isBlank()) return null
        
        // 1. Strip think/thought reasoning tags
        val clean = rawContent
            .replace(Regex("(?s)<think>.*?</think>"), "")
            .replace(Regex("(?s)<thought>.*?</thought>"), "")
            .trim()

        // 2. Extract from markdown code fences if present
        val fenceRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```")
        val match = fenceRegex.find(clean)
        if (match != null) {
            val inside = match.groupValues[1].trim()
            try { return JSONObject(inside) } catch (_: Exception) {}
        }

        // 3. Extract substring between first '{' and last '}'
        val start = clean.indexOf('{')
        val end = clean.lastIndexOf('}')
        if (start != -1 && end > start) {
            val candidate = clean.substring(start, end + 1)
            try { return JSONObject(candidate) } catch (_: Exception) {}
        }

        // 4. Try parsing raw
        return try { JSONObject(clean) } catch (_: Exception) { null }
    }

    suspend fun generatePreset(
        prompt: String,
        device: Int
    ): Result<AiPresetResult> = withContext(Dispatchers.IO) {
        val effectiveDevice = if (device == DeviceProfile.DEVICE_UNKNOWN) DeviceProfile.DEVICE_NP2 else device
        val ledCount = DeviceProfile.getLedCount(effectiveDevice)
        val deviceName = DeviceProfile.deviceName(effectiveDevice)
        val apiKey = getApiKey()

        if (apiKey.isBlank()) {
            return@withContext Result.success(generateSemanticFallbackPreset(prompt, effectiveDevice, ledCount))
        }

        val hardwareInfo = getHardwareLayoutDescription(effectiveDevice, ledCount)

        val systemPrompt = """
You are an expert audio-visual engineer for Nothing Phone Glyph visualizers.
Target Device: $deviceName
Total LEDs: $ledCount
Hardware Segment Map:
$hardwareInfo

Acoustic Rules:
- Frequencies range from 20 Hz to 20,000 Hz.
  * Sub-Bass: 20-60 Hz (kicks, 808s)
  * Bass: 60-250 Hz (basslines, drums)
  * Low-Mid: 250-800 Hz (snare body, guitar warmth)
  * Mid: 800-2500 Hz (vocals, leads, synths)
  * Presence/Highs: 2500-6000 Hz (snare crack, cymbals)
  * Brilliance: 6000-16000 Hz (air, hi-hats, sparkles)
- Thresholds (low_percent and high_percent):
  * 0 to 100 percentage.
  * For noise gates / bass kicks: low_percent: 40-70, high_percent: 100.
  * For progressive VU meter: stagger thresholds across segments.
- decay_alpha: float between 0.30 (punchy) and 0.95 (smooth fade).

You MUST output strictly valid JSON only without markdown or reasoning:
{
  "preset_name": "Creative Name (max 3 words)",
  "description": "1 concise sentence explaining the visual choreography",
  "decay_alpha": 0.80,
  "zones": [
    {
      "index": 0,
      "low_hz": 40.0,
      "high_hz": 160.0,
      "low_percent": 0.0,
      "high_percent": 100.0
    }
  ]
}
Provide zones covering the segments from index 0 to ${ledCount - 1}.
        """.trimIndent()

        val userMessage = "Create an audio reactive Glyph preset for: \"$prompt\" for $deviceName"

        for (model in FREE_MODELS) {
            try {
                val requestJson = JSONObject().apply {
                    put("model", model)
                    put("max_tokens", 1200)
                    put("temperature", 0.3)
                    put("reasoning", JSONObject().apply { put("effort", "none") })
                    put("response_format", JSONObject().apply { put("type", "json_object") })
                    val messagesArr = JSONArray()
                    messagesArr.put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    messagesArr.put(JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    })
                    put("messages", messagesArr)
                }

                val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(ENDPOINT)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("HTTP-Referer", "https://github.com/glyphix/glyph-syncronator")
                    .addHeader("X-Title", "Glyphix Studio AI")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                    val parsedResponse = JSONObject(responseBody)
                    val choices = parsedResponse.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val messageObj = choices.getJSONObject(0).optJSONObject("message")
                        val rawContent = messageObj?.optString("content") ?: ""

                        val jsonResult = extractJsonObject(rawContent)
                        if (jsonResult != null) {
                            val name = jsonResult.optString("preset_name", "AI Preset").trim()
                            val description = jsonResult.optString("description", "AI-generated Glyph lighting pattern").trim()
                            val decayAlpha = jsonResult.optDouble("decay_alpha", 0.80).coerceIn(0.1, 0.98)
                            val zonesArray = jsonResult.optJSONArray("zones")

                            val zoneList = Array<AudioProcessor.ZoneSpec?>(ledCount) { null }

                            if (zonesArray != null && zonesArray.length() > 0) {
                                for (i in 0 until zonesArray.length()) {
                                    val zoneObj = zonesArray.optJSONObject(i)
                                    if (zoneObj != null) {
                                        val targetIdx = zoneObj.optInt("index", i)
                                        val lowHz = zoneObj.optDouble("low_hz", 60.0).toFloat().coerceIn(20f, 19000f)
                                        val highHz = zoneObj.optDouble("high_hz", 2000.0).toFloat().coerceIn(lowHz + 10f, 20000f)
                                        var lowP = zoneObj.optDouble("low_percent", Double.NaN).toFloat()
                                        var highP = zoneObj.optDouble("high_percent", Double.NaN).toFloat()

                                        if (!lowP.isNaN() && lowP in 0.01f..1.0f && !highP.isNaN() && highP in 0.01f..1.0f) {
                                            lowP *= 100f
                                            highP *= 100f
                                        }

                                        val spec = AudioProcessor.ZoneSpec(lowHz, highHz, lowP, highP)
                                        if (targetIdx in 0 until ledCount) {
                                            zoneList[targetIdx] = spec
                                        }
                                    } else {
                                        val arr = zonesArray.optJSONArray(i)
                                        if (arr != null && arr.length() >= 2) {
                                            val lowHz = arr.optDouble(0, 60.0).toFloat().coerceIn(20f, 19000f)
                                            val highHz = arr.optDouble(1, 2000.0).toFloat().coerceIn(lowHz + 10f, 20000f)
                                            val spec = AudioProcessor.ZoneSpec(lowHz, highHz, Float.NaN, Float.NaN)
                                            if (i in 0 until ledCount) {
                                                zoneList[i] = spec
                                            }
                                        }
                                    }
                                }
                            }

                            // Fill any unspecified zones with smooth interpolation based on device
                            val fallbackPreset = generateSemanticFallbackPreset(prompt, effectiveDevice, ledCount)
                            val finalList = mutableListOf<AudioProcessor.ZoneSpec>()
                            for (i in 0 until ledCount) {
                                finalList.add(zoneList[i] ?: fallbackPreset.zones[i])
                            }

                            Log.i(TAG, "Successfully generated AI preset '$name' via $model")
                            return@withContext Result.success(
                                AiPresetResult(
                                    name = name,
                                    description = description,
                                    decayAlpha = decayAlpha,
                                    zones = finalList,
                                    isOfflineGenerated = false
                                )
                            )
                        }
                    }
                } else {
                    Log.w(TAG, "Model $model returned ${response.code}: ${responseBody?.take(100)}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error generating with model $model: ${e.message}")
            }
        }

        Log.i(TAG, "OpenRouter calls finished, using intelligent semantic fallback.")
        Result.success(generateSemanticFallbackPreset(prompt, effectiveDevice, ledCount))
    }

    fun generateSemanticFallbackPreset(prompt: String, device: Int, ledCount: Int): AiPresetResult {
        val lower = prompt.lowercase()
        val hasBass = lower.contains("bass") || lower.contains("808") || lower.contains("sub") || lower.contains("kick") || lower.contains("drop")
        val hasTreble = lower.contains("treble") || lower.contains("hi-hat") || lower.contains("sparkle") || lower.contains("air") || lower.contains("cymbals")
        val hasSynth = lower.contains("synth") || lower.contains("synthwave") || lower.contains("retro") || lower.contains("80s") || lower.contains("wave")
        val hasAcoustic = lower.contains("acoustic") || lower.contains("guitar") || lower.contains("vocal") || lower.contains("warm")
        val hasStrobe = lower.contains("strobe") || lower.contains("rave") || lower.contains("drill") || lower.contains("trap") || lower.contains("punchy")
        val hasVU = lower.contains("vu") || lower.contains("meter") || lower.contains("progress") || lower.contains("level")
        val hasChill = lower.contains("chill") || lower.contains("lo-fi") || lower.contains("lofi") || lower.contains("ambient") || lower.contains("smooth")

        val name = when {
            hasBass && hasStrobe -> "Sub Strobe Blitz"
            hasSynth -> "Synthwave Pulse"
            hasAcoustic -> "Acoustic Warmth"
            hasVU -> "Progressive VU Arc"
            hasChill -> "Lo-Fi Ambient"
            hasTreble -> "Crystal Spark"
            else -> "AI Harmonic Wave"
        }

        val description = when {
            hasBass && hasStrobe -> "Gated sub-bass on battery bar with snappy strobe flashes."
            hasSynth -> "Smooth, continuous 80s synth progression across the Glyph arcs."
            hasAcoustic -> "Warm acoustic mid-range frequencies with natural breath dynamic."
            hasVU -> "Staggered progressive volume threshold meter."
            hasChill -> "Gentle low-frequency glow with silky smooth fade decay."
            else -> "Balanced multi-band spectrum tuned to $prompt."
        }

        val decay = when {
            hasStrobe -> 0.45
            hasChill || hasAcoustic -> 0.88
            hasSynth -> 0.82
            else -> 0.75
        }

        val zones = mutableListOf<AudioProcessor.ZoneSpec>()

        when (device) {
            DeviceProfile.DEVICE_NP2 -> {
                for (i in 0 until ledCount) {
                    when {
                        i in 25..32 -> {
                            val step = i - 25
                            if (hasVU) {
                                val lowP = (step.toFloat() / 8f) * 100f
                                val highP = ((step + 1).toFloat() / 8f) * 100f
                                zones.add(AudioProcessor.ZoneSpec(30f, 150f, lowP, highP))
                            } else if (hasBass) {
                                zones.add(AudioProcessor.ZoneSpec(25f, 120f, if (hasStrobe) 35f else Float.NaN, 100f))
                            } else {
                                val low = 40f + step * 25f
                                zones.add(AudioProcessor.ZoneSpec(low, low * 2f, Float.NaN, Float.NaN))
                            }
                        }
                        i in 0..2 -> {
                            if (hasTreble || hasStrobe) {
                                zones.add(AudioProcessor.ZoneSpec(5000f, 15000f, 40f, 100f))
                            } else {
                                zones.add(AudioProcessor.ZoneSpec(3000f, 10000f, Float.NaN, Float.NaN))
                            }
                        }
                        i in 3..18 -> {
                            val arcIdx = i - 3
                            if (hasVU) {
                                val lowP = (arcIdx.toFloat() / 16f) * 100f
                                val highP = ((arcIdx + 1).toFloat() / 16f) * 100f
                                zones.add(AudioProcessor.ZoneSpec(80f, 400f, lowP, highP))
                            } else if (hasSynth) {
                                val low = 250f * (4000f / 250f).toDouble().pow(arcIdx.toDouble() / 16.0).toFloat()
                                val high = 250f * (4000f / 250f).toDouble().pow((arcIdx + 1).toDouble() / 16.0).toFloat()
                                zones.add(AudioProcessor.ZoneSpec(low, high, Float.NaN, Float.NaN))
                            } else {
                                val low = 120f * (8000f / 120f).toDouble().pow(arcIdx.toDouble() / 16.0).toFloat()
                                val high = 120f * (8000f / 120f).toDouble().pow((arcIdx + 1).toDouble() / 16.0).toFloat()
                                zones.add(AudioProcessor.ZoneSpec(low, high, Float.NaN, Float.NaN))
                            }
                        }
                        else -> {
                            zones.add(AudioProcessor.ZoneSpec(400f, 3500f, if (hasStrobe) 30f else Float.NaN, 100f))
                        }
                    }
                }
            }
            DeviceProfile.DEVICE_NP1 -> {
                for (i in 0 until ledCount) {
                    when (i) {
                        in 7..14 -> {
                            val step = i - 7
                            val lowP = if (hasVU) (step.toFloat() / 8f) * 100f else Float.NaN
                            val highP = if (hasVU) ((step + 1).toFloat() / 8f) * 100f else Float.NaN
                            zones.add(AudioProcessor.ZoneSpec(30f, 160f, lowP, highP))
                        }
                        0, 1 -> zones.add(AudioProcessor.ZoneSpec(4000f, 14000f, Float.NaN, Float.NaN))
                        else -> {
                            val low = 100f * (4000f / 100f).toDouble().pow(i.toDouble() / 6.0).toFloat()
                            val high = 100f * (4000f / 100f).toDouble().pow((i + 1).toDouble() / 6.0).toFloat()
                            zones.add(AudioProcessor.ZoneSpec(low, high, Float.NaN, Float.NaN))
                        }
                    }
                }
            }
            else -> {
                val minFreq = if (hasBass) 25f else 60f
                val maxFreq = if (hasTreble) 18000f else 12000f
                for (i in 0 until ledCount) {
                    val low = minFreq * (maxFreq / minFreq).toDouble().pow(i.toDouble() / ledCount).toFloat()
                    val high = minFreq * (maxFreq / minFreq).toDouble().pow((i + 1).toDouble() / ledCount).toFloat()
                    val lowP = if (hasVU) (i.toFloat() / ledCount) * 100f else if (hasStrobe && i % 2 == 0) 25f else Float.NaN
                    val highP = if (hasVU) ((i + 1).toFloat() / ledCount) * 100f else Float.NaN
                    zones.add(AudioProcessor.ZoneSpec(low, high, lowP, highP))
                }
            }
        }

        return AiPresetResult(
            name = name,
            description = description,
            decayAlpha = decay,
            zones = zones,
            isOfflineGenerated = true
        )
    }

    private fun getHardwareLayoutDescription(device: Int, count: Int): String {
        return when (device) {
            DeviceProfile.DEVICE_NP1 -> """
- 0: Top Camera ring
- 1: Top-Right Diagonal slash
- 2: Bottom-Left Central coil ring
- 3: Bottom-Right Central coil ring
- 4: Top-Right Central coil ring
- 5: Top-Left Central coil ring
- 6: Bottom Dot LED
- 7..14: Vertical Battery Level indicator (7 is bottom, 14 is top)
            """.trimIndent()

            DeviceProfile.DEVICE_NP2 -> """
- 0..2: Top-Left Camera arc segments (0: Top-left, 1: Top-right, 2: Bottom)
- 3..18: Central 16-Segment Circular Progress Arc (clockwise from top)
- 19..23: Center & perimeter diagonal slashes
- 24: Bottom Dot LED
- 25..32: Vertical Battery Line indicator (25 is bottom, 32 is top)
            """.trimIndent()

            DeviceProfile.DEVICE_NP2A -> """
- 0..23: Top large circular 24-segment progress arc
- 24: Medium straight horizontal bar
- 25: Small bottom dot LED
            """.trimIndent()

            DeviceProfile.DEVICE_NP3A -> """
- 0..19: Large upper circular arc (20 segments)
- 20..30: Medium lower circular arc (11 segments)
- 31..35: Small bottom straight bar (5 segments)
            """.trimIndent()

            DeviceProfile.DEVICE_NP4A -> """
- 0..6: Single vertical strip bar with 7 addressable LED segments (0 is top, 6 is bottom)
            """.trimIndent()

            DeviceProfile.DEVICE_NP4B -> """
- 0..4: Single vertical strip bar with 5 addressable LED segments (0 is top, 4 is bottom)
            """.trimIndent()

            DeviceProfile.DEVICE_NP4APRO -> """
- 13x13 Dot-Matrix Display (169 individual addressable LEDs in a grid from index 0 to 168)
            """.trimIndent()

            DeviceProfile.DEVICE_NP3 -> """
- 25x25 Dot-Matrix Display (625 individual addressable LEDs in a grid from index 0 to 624)
            """.trimIndent()

            else -> "- 0..${count - 1}: Sequential addressable LED segments"
        }
    }
}
