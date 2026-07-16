package ru.xvmblitz.android.data

object ApiDefaults {
    const val BASE_URL = "https://xvmblitz.ru/api/"

    fun normalizeBaseUrl(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) {
            return BASE_URL
        }
        require(url.startsWith("https://")) { "Base URL must use https://" }
        if (!url.endsWith("/")) {
            url += "/"
        }
        return url
    }
}
