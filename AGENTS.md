# Instrucoes do Projeto Estoque

## Objetivo

Aplicativo Android pessoal para controle de estoque, vendas, lucro e sincronizacao simples entre aparelhos.

## Tecnologias

- Kotlin
- Jetpack Compose
- Room
- Coroutines / Flow
- Gradle Kotlin DSL

## Estilo de Codigo

- Manter solucoes simples e alinhadas ao padrao atual do projeto.
- Separar dados locais em `data/local`, modelos em `data/model`, regras de acesso em `data/repository` e estado de tela em `ui/viewmodel`.
- Evitar refatoracoes grandes sem necessidade direta.

## Regras de Seguranca

- Este e um app pessoal; o bucket de sincronizacao pode continuar no codigo enquanto o app nao for distribuido.
- Nao apagar dados locais em mudancas de schema; preferir migrations do Room.
- Nao versionar `.env`, chaves privadas ou keystores reais.

## Comandos

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

## Preferencias

- Priorizar estabilidade de estoque, venda e sincronizacao.
- Tratar melhorias futuras separadas de bugs reais.
