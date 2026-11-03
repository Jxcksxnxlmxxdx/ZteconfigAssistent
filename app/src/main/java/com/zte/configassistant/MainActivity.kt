package com.zte.configassistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
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
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) connectWifiNow()
        else status.text = "Permissão necessária não concedida. Autorize o acesso a dispositivos próximos/localização para solicitar a conexão Wi-Fi."
    }

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
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
                    status.text = if (foundSsid != null || foundKey != null) "Etiqueta lida. Confira os dados antes de conectar." else "Texto lido, mas não encontrei SSID/senha com segurança. Confira a etiqueta e preencha manualmente."
                }.addOnFailureListener { status.text = "Não foi possível ler o texto da imagem. Tente outra foto, bem iluminada e sem reflexo." }
                qrTask.addOnSuccessListener { codes ->
                    val wifiCode = codes.firstNotNullOfOrNull { it.rawValue?.takeIf { value -> value.startsWith("WIFI:") } }
                    if (wifiCode != null) {
                        parseWifiQr(wifiCode)?.let { (name, key) ->
                            ssid.setText(name)
                            password.setText(key)
                            status.text = "QR Code Wi-Fi reconhecido. Confira os dados antes de conectar."
                        }
                    }
                }
            } catch (_: Exception) { status.text = "Erro ao abrir a imagem selecionada." }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(22, 28, 22, 18) }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        val brand = TextView(this).apply { text = "◈  ZTE CONFIG ASSISTANT"; textSize = 19f; setTextColor(cyan); setPadding(0, 0, 0, 8) }
        content.addView(brand)
        content.addView(label("CONFIGURADOR INTELIGENTE • ANDROID"))
        content.addView(space(18))
        content.addView(cardTitle("Equipamento selecionado", "ZTE ZXHN F6201B  •  Firmware informado: 9.3"))
        content.addView(space(14))
        content.addView(section("01  /  LER ETIQUETA"))
        content.addView(body("Fotografe a etiqueta ou escolha uma imagem. O app procura SSID, chave Wi-Fi e QR Code de conexão."))
        content.addView(actionButton("ESCANEAR FOTO DA ETIQUETA") { imagePicker.launch("image/*") })
        ssid = edit("Nome da rede Wi-Fi (SSID)")
        content.addView(ssid)
        password = edit("Senha Wi-Fi", true)
        content.addView(password)
        content.addView(space(8))
        content.addView(actionButton("CONECTAR AO WI-FI") { connectWifi() })
        status = body("Aguardando leitura da etiqueta. Confira sempre os dados reconhecidos antes de conectar.")
        content.addView(status)
        content.addView(space(20))
        content.addView(section("02  /  PAINEL DO ROTEADOR"))
        content.addView(body("Depois de conectar, abra o painel local. O endereço é detectado pelo gateway da rede quando disponível."))
        content.addView(actionButton("ABRIR PAINEL ADMINISTRATIVO") { openRouterPanel() })
        content.addView(space(12))
        content.addView(section("03  /  GUIA DE CONFIGURAÇÃO"))
        listOf(
            "Wi-Fi 2,4 GHz e 5 GHz — SSID, senha e segurança",
            "Rede — DHCP, DNS e endereço IP",
            "Portas — redirecionamento e regras locais",
            "Acesso remoto — opções seguras disponíveis no firmware",
            "Backup — salvar e restaurar configurações",
            "Diagnóstico — estado da conexão e testes básicos"
        ).forEachIndexed { i, item -> content.addView(actionButton("${i + 1}.  $item") { showGuide(i) }) }
        content.addView(space(12))
        content.addView(body("Privacidade: as imagens são analisadas no aparelho. O app não envia a etiqueta para um servidor. Não compartilhe a senha Wi-Fi nem a senha administrativa."))

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
        val key = password.text.toString()
        if (name.isEmpty()) { status.text = "Informe ou reconheça o nome da rede (SSID)."; return }
        if (Build.VERSION.SDK_INT < 29) { status.text = "Conexão assistida pelo app exige Android 10 ou superior. Use as configurações de Wi-Fi do aparelho."; return }
        val needed = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (needed.any { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }) {
            permissionRequest.launch(needed)
            return
        }
        connectWifiNow()
    }

    private fun connectWifiNow() {
        val name = ssid.text.toString().trim()
        val key = password.text.toString()
        if (name.isEmpty()) { status.text = "Informe ou reconheça o nome da rede (SSID)."; return }
        try {
            val specBuilder = WifiNetworkSpecifier.Builder().setSsid(name)
            if (key.isNotEmpty()) specBuilder.setWpa2Passphrase(key)
            val specifier = specBuilder.build()
            val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).setNetworkSpecifier(specifier).build()
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            callback?.let { try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {} }
            callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    cm.bindProcessToNetwork(network)
                    runOnUiThread { status.text = "Solicitação de conexão aceita. O Android pode mostrar uma confirmação do sistema." }
                }
                override fun onUnavailable() { runOnUiThread { status.text = "Não foi possível conectar. Confira SSID, senha e sinal da rede." } }
                override fun onLost(network: Network) { runOnUiThread { status.text = "A conexão Wi-Fi foi perdida." } }
            }
            cm.requestNetwork(request, callback!!)
            status.text = "Aguardando autorização do Android para conectar..."
        } catch (e: Exception) { status.text = "Falha ao solicitar conexão: ${e.localizedMessage ?: "verifique as permissões"}" }
    }

    private fun openRouterPanel() {
        val gateway = try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val link = cm.getLinkProperties(cm.activeNetwork)
            link?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
        } catch (_: Exception) { null }
        val address = gateway ?: "192.168.1.1"
        web.visibility = View.VISIBLE
        web.loadUrl("http://$address")
        status.text = "Abrindo painel em http://$address. Se não carregar, confirme que o celular está conectado à rede da ZTE."
    }

    private fun showGuide(index: Int) {
        val guides = listOf(
            "WLAN: localize WLAN/SSID no painel. Configure 2,4 GHz e 5 GHz separadamente, se o firmware oferecer essa opção. Use WPA2-AES ou WPA3 quando disponível.",
            "Rede: confira DHCP antes de alterar. DNS pode ser automático ou definido manualmente. Anote os valores originais antes de salvar.",
            "Portas: crie regras apenas para serviços seus e dispositivos conhecidos. Evite expor interfaces administrativas à internet.",
            "Acesso remoto: prefira VPN. Não habilite administração WAN aberta nem use credenciais padrão. A disponibilidade depende das permissões do provedor.",
            "Backup: procure Maintenance/System/Configuration no painel. Salve o arquivo original em local seguro antes de qualquer alteração.",
            "Diagnóstico: confira status PON/LOS, estado WAN, clientes conectados e logs. Não restaure padrão de fábrica sem dados de provisionamento do provedor."
        )
        android.app.AlertDialog.Builder(this).setTitle("Guia ${index + 1}").setMessage(guides[index]).setPositiveButton("Entendi", null).show()
    }

    private fun findValue(text: String, labels: List<String>): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (i in lines.indices) {
            val line = lines[i]
            val label = labels.firstOrNull { line.uppercase().contains(it) } ?: continue
            val after = line.substringAfter(':', "").trim().ifEmpty { line.substringAfter(label, "").trim().trimStart(':', '=', ' ') }
            if (after.length in 3..64 && !after.contains(' ')) return after
            if (i + 1 < lines.size && lines[i + 1].length in 3..64 && !lines[i + 1].contains(' ')) return lines[i + 1]
        }
        return null
    }

    private fun parseWifiQr(raw: String): Pair<String, String>? {
        val fields = raw.removePrefix("WIFI:").split(';').mapNotNull { part ->
            val p = part.split(':', limit = 2)
            if (p.size == 2) p[0] to p[1].replace("\\;", ";").replace("\\,", ",") else null
        }.toMap()
        val name = fields["S"] ?: return null
        val key = fields["P"] ?: ""
        return name to key
    }

    private fun label(t: String) = TextView(this).apply { text = t; textSize = 11f; setTextColor(Color.LTGRAY) }
    private fun section(t: String) = TextView(this).apply { text = t; textSize = 13f; setTextColor(cyan); setPadding(0, 8, 0, 8) }
    private fun body(t: String) = TextView(this).apply { text = t; textSize = 13f; setTextColor(Color.rgb(204, 214, 228)); setPadding(0, 5, 0, 10) }
    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, h) }
    private fun cardTitle(a: String, b: String) = TextView(this).apply { text = "$a\n$b"; textSize = 15f; setTextColor(Color.WHITE); setPadding(16, 16, 16, 16); setBackgroundColor(panel) }
    private fun edit(hintText: String, secret: Boolean = false) = EditText(this).apply {
        hint = hintText; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); textSize = 14f
        inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        setPadding(14, 12, 14, 12); setBackgroundColor(panel)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 9 }
    }
    private fun actionButton(t: String, action: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setTextColor(Color.WHITE); setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(29, 105, 137)))
        setOnClickListener { action() }; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }
    }

    override fun onDestroy() {
        callback?.let { try { (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it) } catch (_: Exception) {} }
        try { (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).bindProcessToNetwork(null) } catch (_: Exception) {}
        super.onDestroy()
    }
}
