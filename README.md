# StockSync Pro

Aplicativo Android pessoal para controle de estoque, vendas e lucro, com banco local Room e sincronizacao simples entre aparelhos usando uma sala compartilhada no KVDB.

## Tecnologias

- Kotlin
- Android / Jetpack Compose
- Room
- Coroutines / Flow
- Gradle Kotlin DSL

## Como Rodar

**Pre-requisito:** Android Studio com SDK Android instalado.

1. Abra o Android Studio.
2. Selecione **Open** e escolha esta pasta.
3. Aguarde o sync do Gradle.
4. Rode o app em um emulador ou aparelho fisico.

## Comandos Uteis

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

## Observacoes

- O app usa armazenamento local no aparelho e sincronizacao por sala.
- Para usar o botao de atualizacao do app, crie um `.env` com `UPDATE_APK_URL=https://link-direto/estoque-debug.apk`.
- O APK debug e gerado em `app/build/outputs/apk/debug/`.
