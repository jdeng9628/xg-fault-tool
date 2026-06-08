package com.xg.faulttool

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    companion object {
        const val APP_URL = "https://jdeng9628.github.io/xg-fault-tool/fault-tool.html"
    }

    private lateinit var webView: WebView
    private var pendingFileContent: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        setupWebView()

        // 检查是否通过文件打开
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        pendingFileContent = null

        // 尝试从 VIEW intent 读取文件
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                pendingFileContent = readTextFromUri(uri)
            }
        }

        // 尝试从 SEND intent 读取（微信分享）
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                pendingFileContent = it
            }
            // 也尝试读流
            intent.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i).uri?.let { uri ->
                        pendingFileContent = readTextFromUri(uri)
                        if (pendingFileContent != null) break
                    }
                }
            }
        }

        // 加载网页
        webView.loadUrl(APP_URL)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowContentAccess = true
            allowFileAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportZoom(false)
            builtInZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // 页面加载完成后，注入文件内容
                pendingFileContent?.let { content ->
                    injectFileContent(content)
                    pendingFileContent = null
                }
            }
        }
        webView.webChromeClient = WebChromeClient()
    }

    /**
     * 通过 base64 编码安全地注入文件内容到网页
     */
    private fun injectFileContent(content: String) {
        val encoded = Base64.encodeToString(
            content.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        val js = """
            (function(){
                try {
                    var d = atob('$encoded');
                    window.postMessage({
                        type: 'shared-file',
                        content: d,
                        fileName: 'from_app.asc'
                    }, '*');
                } catch(e) {
                    console.error('Inject failed:', e);
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    /**
     * 从 content URI 读取文本内容
     */
    private fun readTextFromUri(uri: android.net.Uri): String? {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
