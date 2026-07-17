package ru.xvmblitz.android.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import ru.xvmblitz.android.BuildConfig

internal object GitHubReleaseDownloader {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val BrowserDownloadUrlRegex =
        Regex(
            """^https://github\.com/([^/]+)/([^/]+)/releases/download/([^/]+)/([^/?#]+)$""",
        )
    private val ApiAssetUrlRegex =
        Regex(
            """^https://api\.github\.com/repos/([^/]+)/([^/]+)/releases/assets/(\d+)$""",
        )

    fun openDownloadResponse(httpClient: OkHttpClient, downloadUrl: String): Response {
        val resolvedUrl = resolveDownloadUrl(httpClient, downloadUrl)
        val request = buildDownloadRequest(resolvedUrl)
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            error(buildErrorMessage(downloadUrl, resolvedUrl, code))
        }

        val contentType = response.header("Content-Type").orEmpty()
        if (contentType.contains("json", ignoreCase = true)) {
            val preview = response.body?.string().orEmpty().take(180)
            response.close()
            error(
                "GitHub вернул JSON вместо APK. Для api.github.com нужен " +
                    "Accept: application/octet-stream и RELEASE_DOWNLOAD_TOKEN. $preview",
            )
        }
        return response
    }

    private fun resolveDownloadUrl(httpClient: OkHttpClient, downloadUrl: String): String {
        if (ApiAssetUrlRegex.matches(downloadUrl)) {
            requireToken("API asset URL")
            return downloadUrl
        }

        val browserMatch = BrowserDownloadUrlRegex.matchEntire(downloadUrl) ?: return downloadUrl
        val token = BuildConfig.RELEASE_DOWNLOAD_TOKEN
        if (token.isBlank()) {
            return downloadUrl
        }

        val owner = browserMatch.groupValues[1]
        val repo = browserMatch.groupValues[2]
        val tag = browserMatch.groupValues[3]
        val assetName = browserMatch.groupValues[4]
        return resolveApiAssetUrl(httpClient, owner, repo, tag, assetName, token)
    }

    private fun resolveApiAssetUrl(
        httpClient: OkHttpClient,
        owner: String,
        repo: String,
        tag: String,
        assetName: String,
        token: String,
    ): String {
        val releaseRequest = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases/tags/$tag")
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "XvmBlitz-Android/${BuildConfig.VERSION_NAME}")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()

        httpClient.newCall(releaseRequest).execute().use { response ->
            if (!response.isSuccessful) {
                error(
                    "Не удалось получить данные релиза GitHub: HTTP ${response.code}. " +
                        "Проверьте RELEASE_DOWNLOAD_TOKEN.",
                )
            }
            val body = response.body?.string().orEmpty()
            val release = json.decodeFromString<GitHubReleaseDto>(body)
            return release.assets
                .firstOrNull { asset -> asset.name == assetName }
                ?.url
                ?.takeIf { url -> url.isNotBlank() }
                ?: error("Asset $assetName не найден в релизе $tag")
        }
    }

    private fun buildDownloadRequest(downloadUrl: String): Request {
        val builder = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "XvmBlitz-Android/${BuildConfig.VERSION_NAME}")

        if (isGitHubApiAssetUrl(downloadUrl)) {
            requireToken("API asset download")
            builder.header("Accept", "application/octet-stream")
            builder.header("Authorization", "Bearer ${BuildConfig.RELEASE_DOWNLOAD_TOKEN}")
            builder.header("X-GitHub-Api-Version", "2022-11-28")
        } else {
            builder.header("Accept", "*/*")
        }

        return builder.build()
    }

    private fun isGitHubApiAssetUrl(downloadUrl: String): Boolean {
        return ApiAssetUrlRegex.matches(downloadUrl) ||
            downloadUrl.startsWith("https://api.github.com/repos/")
    }

    private fun requireToken(reason: String) {
        if (BuildConfig.RELEASE_DOWNLOAD_TOKEN.isBlank()) {
            error(
                "Для $reason из приватного GitHub нужен RELEASE_DOWNLOAD_TOKEN " +
                    "(или публичный URL релиза).",
            )
        }
    }

    private fun buildErrorMessage(originalUrl: String, resolvedUrl: String, code: Int): String {
        if (code != 404) {
            return "Не удалось скачать обновление: HTTP $code"
        }
        val isGitHub = originalUrl.contains("github.com") || resolvedUrl.contains("github.com")
        if (isGitHub) {
            return "Не удалось скачать обновление: HTTP 404. " +
                "Репозиторий приватный — прямая ссылка GitHub не работает из приложения. " +
                "Нужен публичный URL релиза или RELEASE_DOWNLOAD_TOKEN."
        }
        return "Не удалось скачать обновление: HTTP 404"
    }

    @Serializable
    private data class GitHubReleaseDto(
        @SerialName("assets") val assets: List<GitHubAssetDto> = emptyList(),
    )

    @Serializable
    private data class GitHubAssetDto(
        @SerialName("name") val name: String = "",
        @SerialName("url") val url: String = "",
    )
}
