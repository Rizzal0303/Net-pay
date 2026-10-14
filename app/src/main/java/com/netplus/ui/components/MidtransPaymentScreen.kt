package com.netplus.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MidtransPaymentScreen(
    paymentUrl: String,
    totalAmountFormatted: String,
    onPaymentSuccess: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableFloatStateOf(0.1f) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var currentLoadedUrl by remember { mutableStateOf("") }

    // Intercept hardware Back button: navigate inside WebView first if possible
    BackHandler {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            onClose()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("midtrans_payment_screen"),
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = {
                            if (webViewInstance?.canGoBack() == true) {
                                webViewInstance?.goBack()
                            } else {
                                onClose()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali ke Dashboard",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFEFF6FF),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Pembayaran Midtrans",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Aman & Terenkripsi",
                                    modifier = Modifier.size(13.dp),
                                    tint = Color(0xFF059669)
                                )
                            }
                            Text(
                                text = "Tagihan: $totalAmountFormatted",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF2563EB),
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Reload button
                        IconButton(onClick = {
                            hasError = false
                            errorMessage = ""
                            webViewInstance?.reload()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Muat Ulang Halaman",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPaymentSuccess,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("payment_success_check_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF059669)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sudah Bayar? Cek Status",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = onClose,
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "Tutup",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Security banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF0FDF4)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔒 Jalur Pembayaran Resmi & Terenkripsi di Aplikasi",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = Color(0xFF166534)
                    )
                    Text(
                        text = "Netplus Pay",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D),
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // The main in-app WebView
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("midtrans_webview"),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            webViewInstance = this
                            setBackgroundColor(android.graphics.Color.WHITE)
                            setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            isFocusable = true
                            isFocusableInTouchMode = true

                            // 1. Cookie configuration for Midtrans cross-origin iframes
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            // 2. Comprehensive settings for React/Vue SPA
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                setSupportZoom(true)
                                builtInZoomControls = false
                                displayZoomControls = false
                                javaScriptCanOpenWindowsAutomatically = true
                                // CRITICAL: Multiple windows must be false so Midtrans Snap renders in this WebView
                                setSupportMultipleWindows(false)
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                cacheMode = WebSettings.LOAD_DEFAULT

                                // Clean native UserAgent: remove WebView tag '; wv' so banking/gateways render as mobile browser
                                val defaultUa = userAgentString
                                userAgentString = defaultUa.replace("; wv", "")
                            }

                            // 3. WebChromeClient for page loading progress
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    loadingProgress = (newProgress / 100f).coerceIn(0f, 1f)
                                    if (newProgress >= 85) {
                                        isLoading = false
                                    }
                                }
                            }

                            // 4. WebViewClient for URL interception, Deep Links, and SSL
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    hasError = false

                                    if (url != null && isMerchantFinishUrl(url)) {
                                        onPaymentSuccess()
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false

                                    if (url != null && isMerchantFinishUrl(url)) {
                                        onPaymentSuccess()
                                    }
                                }

                                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                    // Proceed through intermediate sandbox / simulator SSL certs
                                    handler?.proceed()
                                }

                                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        hasError = true
                                        errorMessage = error?.description?.toString() ?: "Gagal memuat halaman"
                                        isLoading = false
                                    }
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val targetUrl = request?.url?.toString() ?: return false
                                    return handleNavigation(ctx, view, targetUrl, onPaymentSuccess)
                                }

                                @Deprecated("Deprecated in Java")
                                override fun shouldOverrideUrlLoading(view: WebView?, targetUrl: String?): Boolean {
                                    if (targetUrl == null) return false
                                    return handleNavigation(ctx, view, targetUrl, onPaymentSuccess)
                                }
                            }

                            // Load initial target URL
                            currentLoadedUrl = paymentUrl
                            loadUrl(paymentUrl)
                        }
                    },
                    update = { webView ->
                        // Only reload if the paymentUrl changed to a completely new URL
                        if (paymentUrl.isNotBlank() && currentLoadedUrl != paymentUrl) {
                            currentLoadedUrl = paymentUrl
                            hasError = false
                            webView.loadUrl(paymentUrl)
                        }
                    }
                )

                // Top Progress Indicator
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { loadingProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        color = Color(0xFF2563EB),
                    )
                }

                // Initial loading state overlay while nothing is drawn yet
                if (isLoading && loadingProgress < 0.35f) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.95f),
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF2563EB),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Menyiapkan Gerbang Midtrans...",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Mohon tunggu sebentar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Error Overlay
                if (hasError) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .align(Alignment.Center),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFFFF1F2),
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Gagal Memuat Halaman Pembayaran",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFBE123C)
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (errorMessage.isNotBlank()) errorMessage else "Pastikan koneksi internet aktif atau buka langsung melalui browser luar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = {
                                            hasError = false
                                            errorMessage = ""
                                            webViewInstance?.reload()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                                    ) {
                                        Text("Coba Muat Ulang")
                                    }

                                    Button(
                                        onClick = onClose,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Text(
                                            text = "Tutup",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Handle URL navigation, Midtrans completion callbacks, and external App / Intent schemes (GoPay, ShopeePay, Dana, dll.)
 */
private fun handleNavigation(
    context: Context,
    view: WebView?,
    url: String,
    onPaymentSuccess: () -> Unit
): Boolean {
    // 1. Check for Midtrans completion callback
    if (isMerchantFinishUrl(url)) {
        onPaymentSuccess()
        return true
    }

    val lowerUrl = url.lowercase()

    // 2. Handle intent:// scheme (Format standar Midtrans Snap untuk GoPay, ShopeePay, Bank App)
    if (url.startsWith("intent://", ignoreCase = true) || url.startsWith("intent:", ignoreCase = true)) {
        return try {
            val parsedIntent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                // Langsung coba lempar intent ke aplikasi Gojek/GoPay
                context.startActivity(parsedIntent)
                true
            } catch (e: android.content.ActivityNotFoundException) {
                // Jika aplikasi Gojek / e-wallet belum terpasang, buka fallback URL (halaman web / simulator)
                val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
                if (!fallbackUrl.isNullOrEmpty()) {
                    view?.loadUrl(fallbackUrl)
                    true
                } else {
                    val pkg = parsedIntent.`package`
                    if (!pkg.isNullOrEmpty()) {
                        try {
                            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(marketIntent)
                            true
                        } catch (_: Exception) {
                            view?.loadUrl("https://play.google.com/store/apps/details?id=$pkg")
                            true
                        }
                    } else {
                        false
                    }
                }
            } catch (e: Exception) {
                val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
                if (!fallbackUrl.isNullOrEmpty()) {
                    view?.loadUrl(fallbackUrl)
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    // 3. Handle custom scheme apps (gojek://, gopay://, shopeepay://, dana://, ovo://, etc.)
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: android.content.ActivityNotFoundException) {
            // Coba buka Play Store jika aplikasi tidak ada
            val scheme = Uri.parse(url).scheme?.lowercase()
            val packageName = when (scheme) {
                "gojek" -> "com.gojek.app"
                "gopay" -> "com.gopay.wallet"
                "shopeepay" -> "com.shopee.id"
                "dana" -> "id.dana"
                "ovo" -> "ovo.id"
                else -> null
            }
            if (packageName != null) {
                try {
                    val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(marketIntent)
                    true
                } catch (_: Exception) {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    // 4. Handle GoPay / Gojek Universal App Links via HTTP/HTTPS (gw.gojekapi.com atau gopay.co.id deeplinks)
    if (lowerUrl.contains("gw.gojekapi.com") || lowerUrl.contains("gopay.co.id/app") || lowerUrl.contains("link.dana.id")) {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false // Biarkan WebView memuat jika tidak bisa dibuka aplikasi luar
        }
    }

    // 5. Default: allow WebView to load HTTP/HTTPS URLs internally
    return false
}

private fun isMerchantFinishUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("midtrans_done=1") ||
            lower.contains("payment_finish=1") ||
            lower.contains("status_code=200") ||
            lower.contains("transaction_status=settlement")
}
