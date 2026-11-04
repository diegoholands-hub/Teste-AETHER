# CODEXA AI — Estúdio de Programação

Aplicação web responsiva para programar com IA em português, usando **Puter.js** como provedor de modelos. Projeto criado em uma branch separada para não sobrescrever os arquivos da branch principal.

## Recursos funcionais

- Conversa em linguagem natural e geração de **arquivos completos** por IA (não um contador de evolução ou simulação)
- Seleção de modelos de IA no Puter.js
- Revisão e aplicação manual dos arquivos propostos pela IA
- Editor de código com múltiplos arquivos, criação, exclusão e cópia
- Prévia de HTML/CSS/JavaScript em iframe isolado
- Importação de arquivos e ZIP, exportação ZIP com JSZip
- Histórico de conversa e arquivos salvos automaticamente no armazenamento local do navegador
- Layout adaptado para Android e desktop

## Como hospedar gratuitamente usando GitHub Pages

1. Abra o repositório: https://github.com/diegoholands-hub/Teste-AETHER
2. Vá em **Settings → Pages**.
3. Em **Build and deployment**, selecione **Deploy from a branch**.
4. Selecione a branch **codexa-ai** e a pasta **/(root)**.
5. Clique em **Save** e acompanhe o status indicado pelo GitHub.
6. Quando a publicação for confirmada, o endereço habitual será: https://diegoholands-hub.github.io/Teste-AETHER/.

**Atenção:** o endereço acima só estará ativo depois que o próprio GitHub confirmar o deploy. A publicação do GitHub Pages não foi verificada automaticamente nesta entrega.

## Login e custos

Abra o site publicado, clique em **Conectar IA**, faça login com uma conta Puter e envie seu primeiro pedido. A hospedagem do site pode ser gratuita, mas o serviço de IA usa o modelo **User-Pays**: créditos grátis podem existir e são limitados; o usuário arca com eventuais custos adicionais. Consulte limites e preços na plataforma Puter antes de escolher modelos mais caros.

## Limites e privacidade

- A IA gera código, mas a aplicação não possui um servidor para compilar APK ou executar código Python/Java remotamente.
- Os arquivos propostos precisam ser revisados antes de aplicar e executar.
- O preview executa JavaScript somente em um iframe isolado, não no ambiente principal.
- As solicitações e o código enviado ao modelo são processados por terceiros. Evite incluir senhas, tokens ou dados sensíveis.
- O histórico é salvo apenas no navegador atual, salvo exportações do projeto.
- Esta é uma primeira versão, ainda sem testes reais da conta Puter e sem validação de hospedagem em produção.

## Tecnologia

HTML5, CSS3, JavaScript vanilla, Puter.js e JSZip. Não precisa de compilação nem de servidor backend.
