package com.example.nodejsmobile

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.nodejsmobile.ui.theme.NodejsMobileExampleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    companion object {
        init {
            System.loadLibrary("native-lib")
            System.loadLibrary("node")
        }
    }

    private external fun startNodeWithArguments(arguments: Array<String>): Int

    private val nodeDirName = "nodejs-project"
    private var _startedNodeAlready: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        startNode()

        setContent {
            NodejsMobileExampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NodeVerison(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun startNode() {
        if (!_startedNodeAlready) {
            _startedNodeAlready = true
            val executor = Executors.newSingleThreadExecutor()
            executor.submit {
                val nodeDir = applicationContext.filesDir.absolutePath + "/" + nodeDirName
                copyAssets()
                startNodeWithArguments(
                    arrayOf(
                        "node",
                        "$nodeDir/main.js",
                        "--custom-cwd",
                        nodeDir
                    )
                )
            }
        }
    }

    private fun copyAssets() {
        val nodeDir = applicationContext.filesDir.absolutePath + "/" + nodeDirName
        if (wasAPKUpdated()) {
            val nodeDirReference = File(nodeDir)
            if (nodeDirReference.exists()) {
                deleteFolderRecursively(nodeDirReference)
            }
            copyAssetFolder(applicationContext.assets, nodeDirName, nodeDir)

            saveLastUpdateTime()
        }
    }

    private fun wasAPKUpdated(): Boolean {
        val prefs =
            applicationContext.getSharedPreferences("NODEJS_MOBILE_PREFS", Context.MODE_PRIVATE)
        val previousLastUpdateTime = prefs.getLong("NODEJS_MOBILE_APK_LastUpdateTime", 0)
        var lastUpdateTime: Long = 1
        try {
            val packageInfo =
                applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
            lastUpdateTime = packageInfo.lastUpdateTime
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return lastUpdateTime != previousLastUpdateTime
    }

    private fun deleteFolderRecursively(file: File): Boolean {
        return try {
            var res = true
            val childFiles = file.listFiles() ?: return file.delete()

            for (childFile in childFiles) {
                res = if (childFile.isDirectory) {
                    res and deleteFolderRecursively(childFile)
                } else {
                    res and childFile.delete()
                }
            }
            res and file.delete()
            res
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun copyAssetFolder(
        assetManager: AssetManager,
        fromAssetPath: String,
        toPath: String
    ): Boolean {
        return try {
            val files = assetManager.list(fromAssetPath) ?: return copyAsset(
                assetManager,
                fromAssetPath,
                toPath
            )
            var res = true

            if (files.isEmpty()) {
                res = copyAsset(assetManager, fromAssetPath, toPath)
            } else {
                File(toPath).mkdirs()
                for (file in files) {
                    res = copyAssetFolder(assetManager, "$fromAssetPath/$file", "$toPath/$file")
                }
            }
            res
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun copyAsset(
        assetManager: AssetManager,
        fromAssetPath: String,
        toPath: String
    ): Boolean {
        var `in`: InputStream? = null
        var out: OutputStream? = null
        return try {
            `in` = assetManager.open(fromAssetPath)
            File(toPath).createNewFile()
            out = FileOutputStream(toPath)
            copyFile(`in`, out)
            `in`.close()
            out.flush()
            out.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try {
                `in`?.close()
                out?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun copyFile(inputStream: InputStream, outputStream: OutputStream) {
        val buffer = ByteArray(1024)
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            outputStream.write(buffer, 0, read)
        }
    }

    private fun saveLastUpdateTime() {
        var lastUpdateTime: Long = 1
        try {
            val packageInfo =
                applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
            lastUpdateTime = packageInfo.lastUpdateTime
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        val prefs =
            applicationContext.getSharedPreferences("NODEJS_MOBILE_PREFS", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putLong("NODEJS_MOBILE_APK_LastUpdateTime", lastUpdateTime)
        editor.apply()
    }
}

@Composable
fun NodeVerison(modifier: Modifier = Modifier) {
    var greeting by remember { mutableStateOf("Loading...") }

    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = fetchGreeting()
                greeting = response
            } catch (e: Exception) {
                greeting = "Error: ${e.message}"
            }
        }
    }

    Text(
        text = greeting,
        modifier = modifier
    )
}

suspend fun fetchGreeting(): String {
    val url = URL("http://localhost:3000")
    val connection = withContext(Dispatchers.IO) {
        url.openConnection()
    } as HttpURLConnection
    return connection.inputStream.bufferedReader().use { it.readText() }
}