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
            error(buildErrorMessage(code))
        }

        val contentType = response.header("Content-Type").orEmpty()
        if (contentType.contains("json", ignoreCase = true)) {
            val preview = response.body?.string().orEmpty().take(180)
            response.close()
            error(
                "GitHub вернул JSON вместо APK. Проверьте download_url релиза. $preview",
            )
        }
        return response
    }

    private fun resolveDownloadUrl(httpClient: OkHttpClient, downloadUrl: String): String {
        if (!ApiAssetUrlRegex.matches(downloadUrl)) {
            return downloadUrl
        }
        return toPublicBrowserUrl(httpClient, downloadUrl) ?: downloadUrl
    }

    private fun toPublicBrowserUrl(httpClient: OkHttpClient, apiAssetUrl: String): String? {
        val match = ApiAssetUrlRegex.matchEntire(apiAssetUrl) ?: return null
        val owner = match.groupValues[1]
        val repo = match.groupValues[2]
        val assetId = match.groupValues[3]
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases/assets/$assetId")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "XvmBlitz-Android/${BuildConfig.VERSION_NAME}")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use null
                }
                val body = response.body?.string().orEmpty()
                val asset = json.decodeFromString<GitHubAssetDto>(body)
                asset.browserDownloadUrl.takeIf { url -> url.isNotBlank() }
            }
        }.getOrNull()
    }

    private fun buildDownloadRequest(downloadUrl: String): Request {
        val builder = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "XvmBlitz-Android/${BuildConfig.VERSION_NAME}")

        if (isGitHubApiAssetUrl(downloadUrl)) {
            builder.header("Accept", "application/octet-stream")
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

    private fun buildErrorMessage(code: Int): String {
        return "Не удалось скачать обновление: HTTP $code"
    }

    @Serializable
    private data class GitHubAssetDto(
        @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    )
}
