# LUME PDF

Leitor de arquivos PDF para Android, minimalista, offline e sem anúncios.

## Funcionalidades
- Abrir PDFs do armazenamento usando o seletor nativo.
- Receber PDFs de outros apps.
- Navegar por todas as páginas e ampliar com gesto de pinça.
- Compartilhar o PDF original.
- Imprimir usando a impressão nativa do Android.
- Alternar entre tema claro e escuro.
- Nenhuma permissão de armazenamento, conta ou acesso à internet.

## APK sem instalar programas no computador
Abra **Actions > Compilar APK LUME PDF** neste repositório, selecione a execução concluída e baixe o artefato **LUME-PDF-APK**. Extraia o ZIP, contendo **app-debug.apk**, e instale no Android.

## Compilação local
Com Android SDK 35, Java 17 e Gradle 8.9: `gradle assembleDebug`.

Este projeto usa Java e o renderizador nativo Android PdfRenderer; PDF protegido por senha pode não ser aberto. O APK debug é destinado a testes. O projeto foi publicado na branch `lume-pdf` para preservar a branch principal do repositório de teste.
