<div align="center">

# 📺 Neto OS

### Uma experiência de Smart TV limpa, rápida e moderna para Android TV e Google TV.

Inspirado no visual refinado do **tvOS / Apple TV**, com **Central de Controle** deslizante, dock rápido personalizável, navegação fluida pelo controle remoto (D-Pad) e zero anúncios ou poluição visual na tela inicial.

[![Plataforma](https://img.shields.io/badge/Plataforma-Android_TV_%2F_Google_TV-3DDC84?style=flat-square)](#dispositivos-suportados)
[![SDK Mínimo](https://img.shields.io/badge/SDK_Mínimo-26_(Android_8.0+)-CC785C?style=flat-square)](launcher/app/build.gradle)
[![Java](https://img.shields.io/badge/Java-17-555555?style=flat-square)](launcher/app/build.gradle)
[![Idioma](https://img.shields.io/badge/Idioma-Português_(pt--BR)-009c3b?style=flat-square)](#)
[![Licença](https://img.shields.io/badge/Licença-MIT-blue?style=flat-square)](LICENSE)

[Início Rápido](#-início-rápido) · [Funcionalidades](#-principais-funcionalidades) · [Customização](#-personalização) · [Créditos](#-créditos-e-reconhecimento)

---

</div>

## ✨ O que é o Neto OS?

O **Neto OS** é um launcher nativo para Android TV / Google TV desenvolvido com foco em desempenho, estética premium e usabilidade. Enquanto a tela inicial padrão de muitas fabricantes (TCL, Xiaomi, etc.) e o próprio Google TV são carregados de recomendações forçadas, banners promocionais e lentidão, o Neto OS devolve o controle da tela para você: seus aplicativos favoritos primeiro, resposta instantânea no controle remoto e ferramentas seguras para desativar bloatwares sem precisar de root.

---

## 🚀 Principais Funcionalidades

| Recurso | Descrição |
|---|---|
| 🎛️ **Central de Controle (tvOS)** | Painel lateral translúcido (*glassmorphism*) acessível pelo topo, com status e controles rápidos: Wi-Fi, Bluetooth, Entradas HDMI, Modo Descanso, Ajustes e Menu de Energia. |
| 🏷️ **Cápsula de Status & Perfil** | Barra superior em estilo *island* exibindo saudações dinâmicas (*Bom dia / Boa tarde / Boa noite*), data por extenso em português, status da rede e avatar de perfil. |
| ⚡ **Dock Rápido para o Brasil** | Acesso direto e prioritário aos streamings mais populares: **Globoplay, Disney+, Max, Netflix, Prime Video e YouTube**. |
| 🎮 **Navegação D-Pad Fluida** | Transições suaves desenhadas especificamente para navegação por controle remoto, com foco inteligente ao retornar de qualquer app. |
| 🇧🇷 **Totalmente em Português (pt-BR)** | Todos os textos, menus de entrada, ajustes e estados de conexão traduzidos e adaptados para o Brasil. |
| 🔌 **Menu de Entradas Completo** | Acesso direto e intuitivo a portas HDMI ativas, entrada AV e aplicativo de TV Aberta/Sintonizador. |
| 🌙 **Descanso de Tela Ambient Clock** | Protetor de tela integrado (*Dream service*) com relógio suave e iluminação ambiente que preserva sua tela OLED/LED. |
| 🛡️ **Scripts de Debloat Seguros** | Scripts em PowerShell e Bash para desativar pacotes intrusivos e telemetrias de fabricantes (ex: TCL, Xiaomi) sem apagar arquivos do sistema. |

---

## 🛠️ Dispositivos Suportados & Requisitos

- **Requisitos:**
  - Qualquer Smart TV ou TV Box rodando **Android 8.0 (API 26) ou superior** (Android TV ou Google TV).
  - Opções de Desenvolvedor e Depuração USB / Depuração de Rede (ADB) ativadas.
  - PC conectado na mesma rede local que a TV.

- **Exemplos e perfis testados:**
  - TCL 55" 4K Google TV (Plataforma Realtek RTD2875P / G10)
  - Xiaomi Mi TV 4A & MiTV-AXSO0
  - Sanyo ATV R2
  - OnePlus TV (Dubai)

*(Nota: TVs com sistemas proprietários como LG webOS, Samsung Tizen e Amazon Fire TV não são compatíveis).*

---

## ⚡ Início Rápido

### 1. Conecte-se à TV via ADB

Descubra o endereço IP da sua TV (em *Configurações > Rede > Wi-Fi*) e autorize a depuração no popup exibido na TV:

No **Windows (PowerShell)**:
```powershell
$env:TV_HOST = "192.168.X.X"   # Exemplo: substitua pelo IP local da sua TV (ex: 192.168.1.100)
python tools/tv.py --probe
```

No **Linux / macOS (Bash)**:
```bash
export TV_HOST="192.168.X.X"   # Exemplo: substitua pelo IP local da sua TV
python3 tools/tv.py --probe
```

### 2. Compile e Instale o Launcher

O script compila o código-fonte nativo em Java, transfere o APK para a TV e configura as permissões de rede:

No **PowerShell**:
```powershell
powershell -ExecutionPolicy Bypass -File launcher/build-and-install.ps1 -TvHost $env:TV_HOST
```

No **Bash**:
```bash
bash launcher/build-and-install.sh
```

Pressione o botão **Home** no controle remoto da TV e selecione o **Neto OS** como inicializador padrão.

---

## 🎨 Personalização

### Alterando os Aplicativos do Dock
Você pode personalizar os aplicativos fixados na barra inferior editando o arquivo [`launcher/app/src/main/res/values/config.xml`](launcher/app/src/main/res/values/config.xml):

```xml
<string-array name="dock_apps">
    <item>com.netflix.ninja</item>
    <item>com.amazon.amazonvideo.livingroom</item>
    <item>com.google.android.youtube.tv</item>
    <item>com.disney.disneyplus</item>
    <item>com.globo.globotv</item>
    <item>com.wbd.stream</item>
</string-array>
```

Para descobrir o package name de qualquer app instalado na TV:
```powershell
python tools/tv.py "pm list packages -3"
```

---

## 🧹 Debloat (Otimização e Limpeza)

Para remover travamentos, anúncios do sistema e telemetria, utilize os perfis prontos na pasta `examples/`:

Para aplicar:
```powershell
powershell -ExecutionPolicy Bypass -File tools/disable.ps1 -TvHost $env:TV_HOST examples/tcl-g10-google-tv/debloat.txt
```

Para restaurar todos os pacotes a qualquer momento:
```powershell
powershell -ExecutionPolicy Bypass -File tools/restore.ps1 -TvHost $env:TV_HOST examples/tcl-g10-google-tv/debloat.txt
```

---

## 🤝 Créditos e Reconhecimento

O **Neto OS** é construído sobre o excelente trabalho da comunidade de código aberto. Agradecimentos especiais:

- **[Claude-TV](https://github.com/vishalsingh2972/Claude-TV)** por **[Vishal Singh](https://github.com/vishalsingh2972)**: Base direta deste projeto, que estruturou o launcher limpo, o suporte a múltiplos dispositivos e a arquitetura das ferramentas em Python.
- **[tv-vibehack](https://github.com/Gathin23/tv-vibehack)** por **[Gathin](https://github.com/Gathin23)**: O projeto upstream original que deu início à ideia de um launcher independente e sem anúncios para Android TV.
- **Tipografia Outfit**: Por The Outfit Project Authors sob licença [SIL Open Font License 1.1](launcher/FONT-LICENSE-OFL.txt).

Este projeto é distribuído sob a licença **MIT** (consulte o arquivo [LICENSE](LICENSE) para mais detalhes).

---

<div align="center">

Feito com dedicação por **João Neto** (@jaoqzs) · Baseado em código livre e transparente.

</div>
