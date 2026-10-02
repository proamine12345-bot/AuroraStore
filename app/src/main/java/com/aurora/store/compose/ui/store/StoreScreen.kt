/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.aurora.store.compose.ui.store

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.aurora.store.compose.navigation.Destination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

private data class PublishedApk(
    val title: String,
    val version: String,
    val fileName: String,
    val size: Long,
    val downloadUrl: String
)

private val client = OkHttpClient()
private const val STORE_MARKER = "[AURORA_STORE_PUBLISHED]"

@Composable
fun StoreScreen(
    onNavigateTo: (Destination) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var repository by remember { mutableStateOf("proamine12345-bot/AuroraStore") }
    var token by remember { mutableStateOf("") }
    var appName by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("") }
    var selectedApk by remember { mutableStateOf<Uri?>(null) }
    var apps by remember { mutableStateOf<List<PublishedApk>>(emptyList()) }
    var status by remember { mutableStateOf("لا توجد تطبيقات منشورة بعد") }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        selectedApk = uri
    }

    fun refresh() {
        scope.launch {
            busy = true
            status = "جاري تحميل التطبيقات..."
            try {
                apps = loadApps(repository)
                status = if (apps.isEmpty()) "لا توجد تطبيقات منشورة بعد" else "تم تحديث المتجر"
            } catch (e: Exception) {
                status = "تعذر تحميل المتجر: ${e.message ?: "خطأ في الاتصال"}"
            } finally {
                busy = false
            }
        }
    }

    fun publish() {
        val uri = selectedApk
        if (uri == null || token.isBlank() || appName.isBlank() || version.isBlank()) {
            status = "أدخل اسم التطبيق والإصدار والمفتاح واختر APK"
            return
        }
        scope.launch {
            busy = true
            status = "جاري نشر APK..."
            try {
                publishApk(
                    context = context,
                    repository = repository,
                    token = token.trim(),
                    appName = appName.trim(),
                    version = version.trim(),
                    uri = uri
                )
                status = "تم نشر APK. اضغط تحديث لرؤيته في المتجر."
                selectedApk = null
                refresh()
            } catch (e: Exception) {
                status = "فشل النشر: ${e.message ?: "خطأ"}"
            } finally {
                busy = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("المتجر", style = MaterialTheme.typography.headlineMedium)
            Text(
                "متجر APK فارغ عند البداية. يظهر التطبيق هنا فقط بعد نشره من خلال هذا المتجر.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item {
            OutlinedTextField(
                value = repository,
                onValueChange = { repository = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("مستودع المتجر owner/repo") },
                singleLine = true
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { refresh() }, enabled = !busy) { Text("تحديث") }
                TextButton(onClick = { onNavigateTo(Destination.Main(0)) }) {
                    Text("رجوع")
                }
            }
        }

        item { HorizontalDivider() }

        item {
            Text("نشر APK", style = MaterialTheme.typography.titleLarge)
            Text(
                "لن يظهر أي APK موجود مسبقًا في المستودع. يظهر فقط ما يتم نشره عبر قسم النشر هنا.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        item {
            OutlinedTextField(
                value = appName,
                onValueChange = { appName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("اسم التطبيق") },
                singleLine = true
            )
        }

        item {
            OutlinedTextField(
                value = version,
                onValueChange = { version = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("الإصدار / tag مثل 1.0.0") },
                singleLine = true
            )
        }

        item {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("GitHub token") },
                singleLine = true
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    picker.launch(arrayOf("application/vnd.android.package-archive"))
                }) { Text(if (selectedApk == null) "اختيار APK" else "تم اختيار APK") }

                Button(onClick = { publish() }, enabled = !busy) {
                    Text("نشر APK")
                }
            }
        }

        item {
            Text(status, style = MaterialTheme.typography.bodyMedium)
        }

        item {
            Text("التطبيقات المنشورة", style = MaterialTheme.typography.titleLarge)
        }

        items(apps) { app ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(app.title, style = MaterialTheme.typography.titleMedium)
                    Text("الإصدار: ${app.version}")
                    Text("الملف: ${app.fileName}")
                    if (app.size > 0) Text("الحجم: ${app.size / 1024 / 1024} MB")
                    Button(onClick = {
                        installFromUrl(context, app.downloadUrl)
                    }) {
                        Text("تنزيل وتثبيت")
                    }
                }
            }
        }
    }
}

private suspend fun loadApps(repository: String): List<PublishedApk> = withContext(Dispatchers.IO) {
    require(repository.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
        "صيغة المستودع غير صحيحة"
    }
    val request = Request.Builder()
        .url("https://api.github.com/repos/$repository/releases?per_page=100")
        .header("Accept", "application/vnd.github+json")
        .build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("HTTP ${response.code}")
        val body = response.body?.string() ?: "[]"
        val releases = JSONArray(body)
        buildList {
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
                if (release.optString("body").trim() != STORE_MARKER) continue
                val assets = release.optJSONArray("assets") ?: JSONArray()
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    val name = asset.optString("name")
                    if (!name.endsWith(".apk", ignoreCase = true)) continue
                    add(
                        PublishedApk(
                            title = release.optString("name").ifBlank { release.optString("tag_name") },
                            version = release.optString("tag_name"),
                            fileName = name,
                            size = asset.optLong("size"),
                            downloadUrl = asset.optString("browser_download_url")
                        )
                    )
                }
            }
        }
    }
}

private suspend fun publishApk(
    context: android.content.Context,
    repository: String,
    token: String,
    appName: String,
    version: String,
    uri: Uri
) = withContext(Dispatchers.IO) {
    val temp = File.createTempFile("aurora-publish-", ".apk", context.cacheDir)
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        } ?: error("تعذر قراءة APK")

        val auth = "Bearer $token"
        val tag = version.removePrefix(" ").replace(" ", "-")
        val createBody = JSONObject()
            .put("tag_name", tag)
            .put("name", appName)
            .put("body", STORE_MARKER)
            .put("draft", false)
            .put("prerelease", false)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val createRequest = Request.Builder()
            .url("https://api.github.com/repos/$repository/releases")
            .header("Authorization", auth)
            .header("Accept", "application/vnd.github+json")
            .post(createBody)
            .build()

        val release = client.newCall(createRequest).execute().use { response ->
            if (!response.isSuccessful) {
                error("إنشاء Release فشل: HTTP ${response.code}")
            }
            JSONObject(response.body?.string() ?: error("رد فارغ"))
        }

        val uploadTemplate = release.optString("upload_url")
            .replace("{?name,label}", "")
        val fileName = temp.name.replace("aurora-publish-", "${tag}-")
        val uploadUrl = "$uploadTemplate?name=${URLEncoder.encode(fileName, "UTF-8")}"

        val uploadRequest = Request.Builder()
            .url(uploadUrl)
            .header("Authorization", auth)
            .header("Accept", "application/vnd.github+json")
            .header("Content-Type", "application/vnd.android.package-archive")
            .post(temp.asRequestBody("application/vnd.android.package-archive".toMediaType()))
            .build()

        client.newCall(uploadRequest).execute().use { response ->
            if (!response.isSuccessful) error("رفع APK فشل: HTTP ${response.code}")
        }
    } finally {
        temp.delete()
    }
}

private fun installFromUrl(context: android.content.Context, url: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        !context.packageManager.canRequestPackageInstalls()
    ) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
        return
    }

    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        try {
            val request = Request.Builder().url(url).build()
            val file = File.createTempFile("aurora-download-", ".apk", context.cacheDir)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body?.byteStream()?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: error("ملف APK فارغ")
            }

            withContext(Dispatchers.Main) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileProvider",
                    file
                )
                val install = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(install)
            }
        } catch (_: Exception) {
            // The store screen remains usable; the next refresh/download can be retried.
        }
    }
}
