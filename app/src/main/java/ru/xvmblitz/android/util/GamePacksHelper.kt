package ru.xvmblitz.android.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import java.io.File

object GamePacksHelper {
    const val TANKS_PACKS_RELATIVE_PATH = "Android/data/com.tanksblitz/files/packs"
    private const val ASSETS_DIR = "game_packs"
    private const val OUTPUT_DIR_NAME = "game_packs"

    private data class PackFile(
        val assetName: String,
        val relativePath: String,
    )

    private val PackFiles = listOf(
        PackFile(
            assetName = "Font.style.dvpl",
            relativePath = "UI/Screens3/Font.style.dvpl",
        ),
        PackFile(
            assetName = "BattleLoadingScreen.yaml.dvpl",
            relativePath = "UI/Screens/Battle/BattleLoadingScreen.yaml.dvpl",
        ),
        PackFile(
            assetName = "Jost-Light.ttf.dvpl",
            relativePath = "Fonts/Jost-Light.ttf.dvpl",
        ),
    )

    fun exportPackFiles(context: Context): File {
        val outputDir = File(context.getExternalFilesDir(null), OUTPUT_DIR_NAME).apply {
            mkdirs()
        }
        PackFiles.forEach { packFile ->
            val targetFile = File(outputDir, packFile.relativePath)
            targetFile.parentFile?.mkdirs()
            context.assets.open("$ASSETS_DIR/${packFile.assetName}").use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outputDir
    }

    fun openFolder(context: Context, folder: File): Boolean {
        val relativePath = "Android/data/${context.packageName}/files/$OUTPUT_DIR_NAME"
        val documentId = "primary:$relativePath"
        val documentUri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            documentId,
        )
        val directoryIntents = listOf(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(documentUri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(
                    "content://com.android.externalstorage.documents/document/" +
                        Uri.encode(documentId),
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        if (directoryIntents.any { intent -> startSafely(context, intent) }) {
            return true
        }

        val authority = "${context.packageName}.fileprovider"
        val files = folder.walkTopDown().filter { file -> file.isFile }.toList()
        if (files.isEmpty()) {
            return false
        }
        val uris = ArrayList(
            files.map { file ->
                FileProvider.getUriForFile(context, authority, file)
            },
        )
        val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafely(context, Intent.createChooser(sendIntent, "Открыть файлы"))
    }

    fun openTanksRootFolder(context: Context): Boolean {
        return openExternalFolder(context, TANKS_PACKS_RELATIVE_PATH) ||
            openExternalFolder(context, "Android/data/com.tanksblitz/files")
    }

    private fun openExternalFolder(context: Context, relativePath: String): Boolean {
        val documentId = "primary:$relativePath"
        val documentUri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            documentId,
        )
        val treeUri = DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            documentId,
        )
        val storageRoot = Environment.getExternalStorageDirectory()
        val candidates = listOf(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(documentUri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(treeUri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(
                    "content://com.android.externalstorage.documents/document/" +
                        Uri.encode(documentId),
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.parse("file://$storageRoot/$relativePath"),
                    "resource/folder",
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        return candidates.any { intent -> startSafely(context, intent) }
    }

    private fun startSafely(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
