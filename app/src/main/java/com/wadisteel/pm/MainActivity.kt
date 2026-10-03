package com.wadisteel.pm

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.wadisteel.pm.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var doubleBackToExit = false

    companion object {
        private const val DASHBOARD_URL =
            "https://wadi-steel-industrial-group.wadi-steel200.workers.dev/"
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback
        filePathCallback = null
        if (callback == null) return@registerForActivityResult

        val uris: Array<Uri>? = when {
            result.resultCode != Activity.RESULT_OK -> null
            result.data?.clipData != null -> {
                val clip = result.data!!.clipData!!
                Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
            }
            result.data?.data != null -> arrayOf(result.data!!.data!!)
            else -> null
        }
        callback.onReceiveValue(uris)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep content below status/nav bars (prevents wrong size & cut-off UI)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.parseColor("#0A1218")
        window.navigationBarColor = Color.parseColor("#0A1218")
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply system insets so WebView fills usable area correctly
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setupWebView()
        setupBackPress()
        setupRetry()
        loadDashboard()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView

        // Make sure WebView fills the screen
        webView.layoutParams = webView.layoutParams.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true

            // Correct page size / scaling for phone
            useWideViewPort = true
            loadWithOverviewMode = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            textZoom = 100

            // Allow pinch-zoom if needed (helps when content feels small)
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            loadsImagesAutomatically = true

            // Mobile Chrome UA so the dashboard's mobile CSS (sidebar hamburger) activates
            userAgentString =
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        webView.setBackgroundColor(Color.parseColor("#0A1218"))
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_NEVER

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.progressBar.visibility = View.VISIBLE
                binding.progressBar.progress = 0
                binding.errorLayout.visibility = View.GONE
                webView.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBar.visibility = View.GONE
                // Fix layout + make sidebar usable on phone
                injectMobileFixes(view)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) showError()
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                showError()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                return if (url.contains("workers.dev") || url.startsWith(DASHBOARD_URL)) {
                    false
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: Exception) {
                    }
                    true
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
                binding.progressBar.visibility =
                    if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent()
                    ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(
                            Intent.EXTRA_MIME_TYPES,
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/msword",
                                "text/csv",
                                "*/*"
                            )
                        )
                    }

                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    Toast.makeText(
                        this@MainActivity,
                        "خطا در باز کردن انتخاب فایل",
                        Toast.LENGTH_SHORT
                    ).show()
                    false
                }
            }
        }
    }

    /**
     * Inject CSS/JS so:
     * - page width matches phone
     * - hamburger menu works
     * - sidebar slides as overlay (usable with touch)
     * - no horizontal overflow
     */
    private fun injectMobileFixes(view: WebView?) {
        if (view == null) return
        val js = """
            (function() {
              try {
                // Force mobile viewport
                var meta = document.querySelector('meta[name=viewport]');
                if (!meta) {
                  meta = document.createElement('meta');
                  meta.name = 'viewport';
                  document.head.appendChild(meta);
                }
                meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes, viewport-fit=cover';

                // Extra CSS for phone WebView
                var style = document.getElementById('wadi-android-fix');
                if (!style) {
                  style = document.createElement('style');
                  style.id = 'wadi-android-fix';
                  style.textContent = `
                    html, body {
                      width: 100% !important;
                      max-width: 100vw !important;
                      overflow-x: hidden !important;
                      -webkit-text-size-adjust: 100% !important;
                    }
                    /* Always show hamburger on narrow screens */
                    .menu-toggle, #menuToggle {
                      display: flex !important;
                      align-items: center !important;
                      justify-content: center !important;
                      z-index: 120 !important;
                      min-width: 44px !important;
                      min-height: 44px !important;
                      font-size: 1.4rem !important;
                      cursor: pointer !important;
                    }
                    /* Sidebar as overlay drawer */
                    @media (max-width: 1100px) {
                      .sidebar, #sidebar {
                        position: fixed !important;
                        top: 0 !important;
                        right: 0 !important;
                        bottom: 0 !important;
                        width: min(280px, 85vw) !important;
                        max-width: 85vw !important;
                        transform: translateX(110%) !important;
                        transition: transform .25s ease !important;
                        z-index: 100 !important;
                        box-shadow: -8px 0 24px rgba(0,0,0,.45) !important;
                      }
                      .sidebar.open, #sidebar.open {
                        transform: translateX(0) !important;
                      }
                      .main {
                        margin-right: 0 !important;
                        width: 100% !important;
                        max-width: 100% !important;
                        padding: 12px !important;
                      }
                      .kpi-grid {
                        grid-template-columns: repeat(2, 1fr) !important;
                      }
                      .topbar {
                        flex-wrap: wrap !important;
                        gap: 8px !important;
                      }
                      .table-wrap {
                        max-height: 55vh !important;
                      }
                      #sidebarOverlay, .sidebar-overlay {
                        display: none;
                        position: fixed !important;
                        inset: 0 !important;
                        background: rgba(0,0,0,.55) !important;
                        z-index: 90 !important;
                      }
                      #sidebarOverlay.show, .sidebar-overlay.show {
                        display: block !important;
                      }
                    }
                  `;
                  document.head.appendChild(style);
                }

                // Ensure mobile nav handlers are bound
                if (typeof setupMobileNav === 'function') {
                  try { setupMobileNav(); } catch (e) {}
                }

                // Fallback: wire hamburger manually if needed
                var btn = document.getElementById('menuToggle');
                var side = document.getElementById('sidebar');
                var overlay = document.getElementById('sidebarOverlay');
                if (btn && side) {
                  btn.onclick = function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    var open = side.classList.toggle('open');
                    if (overlay) {
                      if (open) overlay.classList.add('show');
                      else overlay.classList.remove('show');
                    }
                  };
                  if (overlay) {
                    overlay.onclick = function() {
                      side.classList.remove('open');
                      overlay.classList.remove('show');
                    };
                  }
                  document.querySelectorAll('.nav-item').forEach(function(el) {
                    el.addEventListener('click', function() {
                      side.classList.remove('open');
                      if (overlay) overlay.classList.remove('show');
                    });
                  });
                }
              } catch (err) {
                console.log('wadi-android-fix', err);
              }
            })();
        """.trimIndent()

        view.evaluateJavascript(js, null)
    }

    private fun loadDashboard() {
        binding.errorLayout.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        binding.webView.loadUrl(DASHBOARD_URL)
    }

    private fun showError() {
        binding.webView.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.errorLayout.visibility = View.VISIBLE
    }

    private fun setupRetry() {
        binding.btnRetry.setOnClickListener { loadDashboard() }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Close sidebar first if open
                binding.webView.evaluateJavascript(
                    """
                    (function(){
                      var s=document.getElementById('sidebar');
                      var o=document.getElementById('sidebarOverlay');
                      if(s && s.classList.contains('open')){
                        s.classList.remove('open');
                        if(o) o.classList.remove('show');
                        return 'closed';
                      }
                      return 'none';
                    })();
                    """.trimIndent()
                ) { result ->
                    if (result != null && result.contains("closed")) return@evaluateJavascript
                    when {
                        binding.webView.canGoBack() -> binding.webView.goBack()
                        doubleBackToExit -> finish()
                        else -> {
                            doubleBackToExit = true
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.exit_confirm),
                                Toast.LENGTH_SHORT
                            ).show()
                            Handler(Looper.getMainLooper()).postDelayed({
                                doubleBackToExit = false
                            }, 2000)
                        }
                    }
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onDestroy() {
        binding.webView.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }
}
