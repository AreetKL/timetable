package com.aamina.timetable

/**
 * 在线识别用的服务商预设。
 *
 * 全部走 **OpenAI 兼容** 的 `/chat/completions` 协议（智谱 v4、通义 compatible-mode、
 * Kimi、硅基流动、Gemini 的 openai 兼容层都是这套），所以请求代码只有一份，
 * 换服务商只是换 baseUrl + model。
 */
data class AiProvider(
    val id: String,
    val label: String,
    val baseUrl: String,
    /** (模型 id, 展示名) */
    val models: List<Pair<String, String>>,
    /** 去哪里领 Key，直接显示在设置页 */
    val hint: String,
    /** Key 前缀特征，用于自动识别；空 = 不参与自动识别 */
    val keyPrefix: String = ""
)

object Providers {

    val ALL = listOf(
        AiProvider(
            id = "zhipu",
            label = "智谱 GLM",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            models = listOf(
                "glm-4v-flash" to "GLM-4V-Flash（轻快·免费）",
                "glm-4.1v-thinking-flash" to "GLM-4.1V-Thinking（更准）",
                "glm-4.6v" to "GLM-4.6V（旗舰·限免）"
            ),
            hint = "bigmodel.cn 注册 → 控制台 → API Keys，以上模型均免费"
        ),
        AiProvider(
            id = "dashscope",
            label = "通义千问",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            models = listOf(
                "qwen-vl-plus" to "qwen-vl-plus（快）",
                "qwen-vl-max" to "qwen-vl-max（准）",
                "qwen3-vl-plus" to "qwen3-vl-plus（新）"
            ),
            hint = "bailian.console.aliyun.com → API-KEY 管理，新用户有免费额度"
        ),
        AiProvider(
            id = "moonshot",
            label = "Kimi",
            baseUrl = "https://api.moonshot.cn/v1/chat/completions",
            models = listOf(
                "moonshot-v1-8k-vision-preview" to "moonshot-v1-8k-vision（识图）",
                "moonshot-v1-32k-vision-preview" to "moonshot-v1-32k-vision（长图）"
            ),
            hint = "platform.moonshot.cn → API Key 管理"
        ),
        AiProvider(
            id = "siliconflow",
            label = "硅基流动",
            baseUrl = "https://api.siliconflow.cn/v1/chat/completions",
            models = listOf(
                "Qwen/Qwen2.5-VL-32B-Instruct" to "Qwen2.5-VL-32B",
                "Qwen/Qwen2.5-VL-72B-Instruct" to "Qwen2.5-VL-72B（更准）"
            ),
            hint = "cloud.siliconflow.cn → API 密钥，部分模型免费"
        ),
        AiProvider(
            id = "openai",
            label = "OpenAI",
            baseUrl = "https://api.openai.com/v1/chat/completions",
            models = listOf(
                "gpt-4o-mini" to "gpt-4o-mini（便宜）",
                "gpt-4.1-mini" to "gpt-4.1-mini（新）",
                "gpt-4o" to "gpt-4o（最准）"
            ),
            hint = "platform.openai.com → API keys，需自备额度 / 中转地址"
        ),
        AiProvider(
            id = "gemini",
            label = "Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            models = listOf(
                "gemini-2.0-flash" to "gemini-2.0-flash（有免费额度）",
                "gemini-2.5-flash" to "gemini-2.5-flash（更准）"
            ),
            hint = "aistudio.google.com → Get API key，免费额度较大"
        ),
        AiProvider(
            id = "stepfun",
            label = "阶跃星辰",
            baseUrl = "https://api.stepfun.com/v1/chat/completions",
            models = listOf(
                "step-1o-turbo-vision" to "step-1o-turbo-vision",
                "step-1v-8k" to "step-1v-8k"
            ),
            hint = "platform.stepfun.com → 接口密钥"
        ),
        AiProvider(
            id = "custom",
            label = "自定义",
            baseUrl = "",
            models = emptyList(),
            hint = "任何 OpenAI 兼容服务：填完整的 chat/completions 地址 + 模型名"
        )
    )

    fun byId(id: String?): AiProvider = ALL.firstOrNull { it.id == id } ?: ALL[0]

    /** 按 Key 长相猜服务商：智谱是 `id.secret` 带点，Gemini 是 AIza 开头。 */
    fun guessByKey(key: String): AiProvider? {
        val k = key.trim()
        if (k.isEmpty()) return null
        return when {
            k.startsWith("AIza") -> byId("gemini")
            k.startsWith("sk-ant") -> null
            // 32 位 + "." + 16 位十六进制，智谱的经典格式
            Regex("^[0-9a-fA-F]{16,64}\\.[0-9a-zA-Z]{6,}$").matches(k) -> byId("zhipu")
            else -> null
        }
    }
}

/** 一次在线识别的完整配置。 */
data class AiConfig(
    val apiKey: String,
    val providerId: String,
    /** 自定义或用户改过的完整地址；空 = 用预设 */
    val baseUrl: String = "",
    val model: String = ""
) {
    val provider: AiProvider get() = Providers.byId(providerId)
    val endpoint: String get() = baseUrl.ifBlank { provider.baseUrl }
    val realModel: String
        get() = model.ifBlank { provider.models.firstOrNull()?.first ?: "glm-4v-flash" }
}
