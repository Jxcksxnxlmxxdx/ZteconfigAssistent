# ZTE Config Assistant — protótipo Android

Aplicativo inicial para Android 10+ focado na ZTE ZXHN F6201B. Inclui:
- seleção de imagem da etiqueta;
- OCR local com ML Kit para tentar reconhecer SSID e chave Wi-Fi;
- leitura de QR Code Wi-Fi no formato `WIFI:T:WPA;S:...;P:...;;`;
- solicitação de conexão Wi-Fi pelo diálogo de autorização do Android;
- detecção do gateway e abertura do painel local em WebView;
- guias de configuração para WLAN, rede, portas, acesso remoto seguro, backup e diagnóstico.

## Abrir no Android Studio
1. Extraia o ZIP.
2. Abra a pasta `ZTEConfigAssistant` no Android Studio.
3. Aguarde a sincronização do Gradle (internet necessária para baixar dependências).
4. Execute em um celular Android 10 ou superior.

## Limitações importantes
- Este é um protótipo-fonte, não um APK compilado.
- OCR é uma tentativa: confira SSID e senha antes de solicitar conexão. Etiquetas variam e podem usar nomes de campo diferentes.
- A conexão Wi-Fi é solicitada pelo Android e depende de confirmação do usuário e das permissões/versão do sistema.
- O acesso ao painel abre a página local; ainda não automatiza login nem grava configurações no roteador.
- Os menus da F6201B variam por firmware e customização do provedor. Não aplicar alterações em lote sem mapear o firmware exato.
- O app não transmite as imagens da etiqueta a um servidor. Não adicione senhas administrativas a prints ou logs.

## Gerar APK automaticamente na nuvem (GitHub Actions)
1. Crie um repositório no GitHub e envie o conteúdo desta pasta para a branch `main`.
2. Abra a aba **Actions** do repositório.
3. Se necessário, habilite workflows para o repositório.
4. Execute **Build Android APK** com **Run workflow** (ou envie um commit para `main`).
5. Quando o workflow terminar com sucesso, abra a execução e baixe o artefato **ZTEConfigAssistant-debug-apk**.
6. Extraia o artefato e instale `app-debug.apk` no Android. Talvez seja necessário permitir instalação desta fonte.

O APK gerado por esse fluxo é de depuração (debug), adequado para testes. Para distribuição ampla, será necessário configurar assinatura de release e proteger a chave de assinatura.
