package com.zte.configassistant

import android.Manifest
import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : ComponentActivity() {
    private val bg = Color.rgb(12, 17, 29)
    private val panel = Color.rgb(24, 33, 50)
    private val cyan = Color.rgb(77, 211, 255)

    private lateinit var ssid: EditText
    private lateinit var password: EditText
    private lateinit var status: TextView
    private lateinit var web: WebView
    private var callback: ConnectivityManager.NetworkCallback? = null

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) connectWifiNow()
            else status.text = "Permissão necessária não concedida."
        }

    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            status.text = "Analisando etiqueta..."

            try {
                val image = InputImage.fromFilePath(this, uri)
                val textTask = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
                val qrTask = BarcodeScanning.getClient().process(image)

                textTask.addOnSuccessListener { result ->
                    val raw = result.text
                    val foundSsid = findValue(raw, listOf("SSID", "WIFI NAME", "WI-FI NAME", "WLAN NAME", "WIRELESS NAME"))
                    val foundKey = findValue(raw, listOf("WLAN KEY", "WIFI KEY", "WI-FI KEY", "WIFI PASSWORD", "PASSWORD", "WPA KEY", "WIRELESS KEY"))

                    if (!foundSsid.isNullOrBlank()) ssid.setText(foundSsid)
                    if (!foundKey.isNullOrBlank()) password.setText(foundKey)

                    status.text = if (foundSsid != null || foundKey != null) {
                        "Etiqueta lida. Confira os dados antes de conectar."
                    } else {
                        "Não encontrei SSID ou senha com segurança. Confira a etiqueta e preencha manualmente."
                    }
                }.addOnFailureListener {
                    status.text = "Falha na leitura do texto. Tente outra foto."
                }

                qrTask.addOnSuccessListener { codes ->
                    val wifiCode = codes.firstNotNullOfOrNull { code ->
                        code.rawValue?.takeIf { it.startsWith("WIFI:", ignoreCase = true) }
                    }
                    if (wifiCode != null) {
                        parseWifiQr(wifiCode)?.let { (name, key) ->
                            ssid.setText(name)
                            password.setText(key)
                            status.text = "QR Code Wi-Fi reconhecido. Confira os dados antes de conectar."
                        }
                    }
                }.addOnFailureListener {
                    // O resultado do OCR continua disponível.
                }
            } catch (_: Exception) {
                status.text = "Erro ao abrir a imagem selecionada."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(22, 28, 22, 18)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        val brand = TextView(this).apply {
            text = "◈  ZTE CONFIG ASSISTANT"
            textSize = 19f
            setTextColor(cyan)
            setPadding(0, 0, 0, 8)
        }
        content.addView(brand)
        content.addView(label("CONFIGURADOR INTELIGENTE • ANDROID"))
        content.addView(space(18))
        content.addView(cardTitle("Equipamento selecionado", "ZTE ZXHN F6201B  •  Firmware informado: 9.3"))
        content.addView(space(14))
        content.addView(section("01  /  LER ETIQUETA"))
        content.addView(body("Escolha uma foto da etiqueta. O app procura SSID, senha e QR Code Wi-Fi."))
        content.addView(actionButton("ESCANEAR FOTO DA ETIQUETA") { imagePicker.launch("image/*") })

        ssid = edit("Nome da rede Wi-Fi (SSID)")
        content.addView(ssid)
        password = edit("Senha Wi-Fi", true)
        content.addView(password)
        content.addView(space(8))
        content.addView(actionButton("CONECTAR AO WI-FI") { connectWifi() })
        status = body("Aguardando leitura da etiqueta.")
        content.addView(status)

        content.addView(space(20))
        content.addView(section("02  /  PAINEL DO ROTEADOR"))
        content.addView(body("Depois de conectar, abra o painel local do roteador."))
        content.addView(actionButton("ABRIR PAINEL ADMINISTRATIVO") { openRouterPanel() })

        content.addView(space(12))
        content.addView(section("03  /  GUIA DE CONFIGURAÇÃO"))
        listOf(
            "Wi-Fi 2,4 GHz e 5 GHz — SSID, senha e segurança",
            "Rede — DHCP, DNS e endereço IP",
            "Portas — redirecionamento e regras locais",
            "Acesso remoto — opções seguras",
            "Backup — salvar e restaurar configurações",
            "Diagnóstico — estado da conexão"
        ).forEachIndexed { i, item ->
            content.addView(actionButton("${i + 1}.  $item") { showGuide(i) })
        }

        content.addView(space(12))
        content.addView(body("Privacidade: as imagens são analisadas no aparelho. Não compartilhe suas senhas."))

        web = WebView(this).apply {
            visibility = View.GONE
            settings.javaScriptEnabled = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
        content.addView(web, LinearLayout.LayoutParams(-1, 520))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun connectWifi() {
        val name = ssid.text.toString().trim()
        if (name.isEmpty()) {
            status.text = "Informe o nome da rede Wi-Fi."
            return
        }
        if (Build.VERSION.SDK_INT < 29) {
            status.text = "A conexão assistida exige Android 10 ou superior."
            return
        }

        val needed = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (needed.any { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }) {
            permissionRequest.launch(needed)
            return
        }
        connectWifiNow()
    }

    private fun connectWifiNow() {
        val name = ssid.text.toString().trim()
        val key = password.text.toString()
        if (name.isEmpty()) {
            status.text = "Informe o nome da rede Wi-Fi."
            return
        }

        try {
            val builder = WifiNetworkSpecifier.Builder().setSsid(name)
            if (key.isNotEmpty()) builder.setWpa2Passphrase(key)
            val specifier = builder.build()
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()

            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            callback?.let {
                try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {}
            }

            callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    cm.bindProcessToNetwork(network)
                    runOnUiThread {
                        status.text = "Conexão solicitada. O Android pode mostrar uma confirmação do sistema."
                    }
                }
                override fun onUnavailable() {
                    runOnUiThread { status.text = "Não foi possível conectar. Confira SSID, senha e sinal." }
                }
                override fun onLost(network: Network) {
                    runOnUiThread { status.text = "A conexão Wi-Fi foi perdida." }
                }
            }

            cm.requestNetwork(request, callback!!)
            status.text = "Aguardando autorização do Android para conectar..."
        } catch (e: Exception) {
            status.text = "Falha ao solicitar conexão: ${e.localizedMessage ?: "verifique as permissões"}"
        }
    }

    private fun openRouterPanel() {
        val gateway = try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val link = cm.getLinkProperties(cm.activeNetwork)
            link?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
        } catch (_: Exception) {
            null
        }
        val address = gateway ?: "192.168.1.1"
        web.visibility = View.VISIBLE
        web.loadUrl("http://$address")
        status.text = "Abrindo painel em http://$address. Confira se o celular está conectado à rede ZTE."
    }

    private fun showGuide(index: Int) {
        val guides = listOf(
            "WLAN: localize WLAN/SSID no painel. Configure 2,4 GHz e 5 GHz separadamente, se o firmware oferecer essa opção. Use WPA2-AES ou WPA3 quando disponível.",
            "Rede: confira DHCP antes de alterar. DNS pode ser automático ou definido manualmente. Anote os valores originais antes de salvar.",
            "Portas: crie regras apenas para serviços seus e dispositivos conhecidos. Evite expor interfaces administrativas à internet.",
            "Acesso remoto: prefira VPN. Não habilite administração WAN aberta nem use credenciais padrão.",
            "Backup: procure Maintenance/System/Configuration no painel. Salve o arquivo original em local seguro antes de qualquer alteração.",
            "Diagnóstico: confira status PON/LOS, estado WAN, clientes conectados e logs. Não restaure padrão de fábrica sem dados do provedor."
        )
        android.app.AlertDialog.Builder(this)
            .setTitle("Guia ${index + 1}")
            .setMessage(guides[index])
            .setPositiveButton("Entendi", null)
            .show()
    }

    private fun findValue(text: String, labels: List<String>): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val sortedLabels = labels.sortedByDescending { it.length }

        for (i in lines.indices) {
            val line = lines[i]
            val upper = line.uppercase()
            val label = sortedLabels.firstOrNull { upper.contains(it.uppercase()) } ?: continue
            val index = upper.indexOf(label.uppercase())
            val suffix = line.substring(index + label.length).trim().trimStart(':', '=', '-', ' ')
            val candidate = suffix.trim().trim('"', '\'', ' ')

            if (isLikelyCredential(candidate)) return candidate

            if (i + 1 < lines.size) {
                val next = lines[i + 1].trim().trim('"', '\'', ' ')
                if (isLikelyCredential(next)) return next
            }
        }
        return null
    }

    private fun isLikelyCredential(value: String): Boolean {
        if (value.length !in 3..64) return false
        if (value.any { it.isWhitespace() }) return false
        if (value.contains("SSID", ignoreCase = true)) return false
        if (value.contains("PASSWORD", ignoreCase = true)) return false
        return true
    }

    private fun parseWifiQr(raw: String): Pair<String, String>? {
        if (!raw.startsWith("WIFI:", ignoreCase = true)) return null

        val content = raw.substringAfter(':')
        val fields = mutableMapOf<String, String>()
        val key = StringBuilder()
        val value = StringBuilder()
        var readingKey = true
        var escaped = false

        fun saveField() {
            if (key.isNotEmpty()) fields[key.toString()] = value.toString()
            key.clear()
            value.clear()
            readingKey = true
        }

        for (char in content) {
            if (escaped) {
                (if (readingKey) key else value).append(char)
                escaped = false
                continue
            }
            when (char) {
                '\\' -> escaped = true
                ':' -> if (readingKey) readingKey = false else value.append(char)
                ';' -> saveField()
                else -> (if (readingKey) key else value).append(char)
            }
        }

        if (escaped) value.append('\\')
        saveField()

        val name = fields["S"]?.takeIf { it.isNotBlank() } ?: return null
        val wifiPassword = if (fields["T"].equals("nopass", ignoreCase = true)) "" else fields["P"] ?: ""
        return name to wifiPassword
    }

    private fun label(t: String) = TextView(this).apply {
        text = t
        textSize = 11f
        setTextColor(Color.LTGRAY)
    }

    private fun section(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(cyan)
        setPadding(0, 8, 0, 8)
    }

    private fun body(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(Color.rgb(204, 214, 228))
        setPadding(0, 5, 0, 10)
    }

    private fun space(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, h)
    }

    private fun cardTitle(a: String, b: String) = TextView(this).apply {
        text = "$a\n$b"
        textSize = 15f
        setTextColor(Color.WHITE)
        setPadding(16, 16, 16, 16)
        setBackgroundColor(panel)
    }

    private fun edit(hintText: String, secret: Boolean = false) = EditText(this).apply {
        hint = hintText
        setHintTextColor(Color.GRAY)
        setTextColor(Color.WHITE)
        textSize = 14f
        inputType = if (secret) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT
        }
        setPadding(14, 12, 14, 12)
        setBackgroundColor(panel)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 9 }
    }

    private fun actionButton(t: String, action: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(Color.rgb(29, 105, 137))
        )
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }
    }

    override fun onDestroy() {
        callback?.let {
            try {
                (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
        try {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .bindProcessToNetwork(null)
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
