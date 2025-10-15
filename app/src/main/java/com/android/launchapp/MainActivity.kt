package com.android.launchapp

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.android.launchapp.ui.theme.LaunchappTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 檢查是否是從 ASSIST 意圖啟動的
        if (intent?.action == Intent.ACTION_ASSIST) {
            val prefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
            val targetPackage = prefs.getString("target_app_package", null)

            if (targetPackage != null) {
                val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                }
            }
            // 啟動完目標應用程式後，結束這個助理 Activity
            finish()
            return // 結束 onCreate，不顯示 UI
        }

        setContent {
            LaunchappTheme {
                MainScreen()
            }
        }
    }
}

// App 資訊的資料類別
data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val prefs = remember { context.getSharedPreferences("Settings", Context.MODE_PRIVATE) }

    // --- ↓↓↓ 新增的部分：用於控制提示對話框的顯示狀態 ↓↓↓ ---
    var showPermissionDialog by remember { mutableStateOf(false) }

    // 使用 LaunchedEffect 來確保這個檢查只在畫面首次載入時執行一次
    LaunchedEffect(Unit) {
        val isFirstLaunch = prefs.getBoolean("is_first_launch", true)
        if (isFirstLaunch) {
            showPermissionDialog = true
        }
    }

    // --- ↓↓↓ 新增的部分：權限說明對話框的 UI ↓↓↓ ---
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { /* 不允許透過點擊背景關閉 */ },
            title = { Text("權限說明") },
            text = { Text("為了能夠完整列出您裝置上的所有應用程式，以便您從中選擇啟動目標，本程式需要「查詢所有應用程式」的權限。\n\n這項權限僅用於在本 App 內顯示列表，不會上傳或分享您的應用程式資訊。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 關閉對話框
                        showPermissionDialog = false
                        // 記錄下來，下次不再顯示
                        prefs.edit().putBoolean("is_first_launch", false).apply()
                    }
                ) {
                    Text("我了解")
                }
            }
        )
    }
    // --- ↑↑↑ 新增部分結束 ↑↑↑ ---


    // 使用 remember 來儲存應用程式列表，避免重複載入
    val appList = remember {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        packageManager.queryIntentActivities(mainIntent, 0).mapNotNull { resolveInfo ->
            AppInfo(
                name = resolveInfo.loadLabel(packageManager).toString(),
                packageName = resolveInfo.activityInfo.packageName,
                icon = resolveInfo.loadIcon(packageManager)
            )
        }.sortedBy { it.name }
    }

    // 追蹤當前選擇的應用程式
    var selectedPackageName by remember {
        mutableStateOf(prefs.getString("target_app_package", null))
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("助理啟動設定") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = {
                val intent = Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS)
                if (intent.resolveActivity(packageManager) != null) {
                    context.startActivity(intent)
                }
            }) {
                Text("設定為預設數位助理")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "請選擇一個應用程式作為啟動目標:",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedPackageName != null) {
                Text(text = "當前已選: $selectedPackageName")
                Spacer(modifier = Modifier.height(8.dp))
            }


            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(appList) { appInfo ->
                    AppListItem(appInfo = appInfo, isSelected = appInfo.packageName == selectedPackageName) {
                        // 當使用者點擊一個項目時
                        selectedPackageName = appInfo.packageName
                        prefs.edit().putString("target_app_package", appInfo.packageName).apply()
                    }
                }
            }
        }
    }
}

@Composable
fun AppListItem(appInfo: AppInfo, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = appInfo.icon.toBitmap().asImageBitmap(),
            contentDescription = appInfo.name,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = appInfo.name,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            RadioButton(selected = true, onClick = null)
        }
    }
}