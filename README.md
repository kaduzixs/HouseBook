::: {align="center"}
# 🌲 HouseBook

### Sua biblioteca pessoal, organizada em um refúgio de histórias.

**Uma aplicação web para construir, organizar e acompanhar sua jornada
literária.**

[Visão geral](#-visão-geral) · [Tecnologias](#-tecnologias) ·
[Arquitetura](#-arquitetura-do-projeto) · [Execução](#-como-executar) ·
[API](#-endpoints-disponíveis) · [Segurança](#-segurança)
:::

<div align="center">
    Para verificar como está o andamento do projeto entre neste link:
    
</div>
https://housebook-0xes.onrender.com

------------------------------------------------------------------------

## 📖 Visão geral

O **HouseBook** é uma aplicação web desenvolvida com Java e Spring Boot,
estruturada para oferecer uma experiência centralizada de organização
literária. A proposta é disponibilizar um ambiente no qual cada pessoa
possa acessar sua conta e, nas próximas etapas de desenvolvimento,
administrar sua própria biblioteca, acompanhar leituras e reunir
informações sobre seus livros.

A identidade visual segue o conceito de uma **floresta encantada
antiga**: uma interface acolhedora, com referências a bibliotecas
medievais, livros pixelados, iluminação suave e opções de
acessibilidade.

O projeto está sendo construído por etapas. Este repositório contém a
base de autenticação, persistência de usuários, configuração de
segurança e a interface inicial de login e cadastro. Recursos literários
adicionais fazem parte da evolução planejada e não devem ser
considerados concluídos até que seus respectivos módulos estejam
implementados.

## 🎯 Objetivos do projeto

-   Estruturar uma aplicação web com separação entre apresentação,
    controladores, serviços e persistência.
-   Implementar cadastro e autenticação de usuários.
-   Armazenar os dados de conta em PostgreSQL.
-   Proteger senhas com hash BCrypt.
-   Preparar a aplicação para que os dados de cada usuário sejam
    associados à sua própria identidade.
-   Construir uma interface acessível, responsiva e visualmente
    consistente.
-   Evoluir a plataforma para gerenciamento de livros, favoritos,
    comentários e histórico.

## ✨ Experiência da interface

A página inicial de autenticação foi concebida para apresentar a
identidade visual do HouseBook sem comprometer a clareza dos
formulários.

### Login

A tela de entrada permite que o usuário informe e-mail e senha. O
navegador envia os dados ao endpoint de autenticação e apresenta o
retorno recebido pela API.

### Cadastro

O formulário de registro solicita nome, e-mail e senha. A validação no
navegador melhora a experiência, enquanto as regras definitivas devem
permanecer no backend.

### Foto de perfil

A interface permite selecionar uma imagem de perfil opcional e exibe uma
prévia antes do envio. O JavaScript limita a seleção a JPG, PNG ou WEBP
e a arquivos de até 2 MB.

> **Estado técnico importante:** na versão atual do código analisado, o
> frontend envia o arquivo no campo multipart `foto`, mas o
> `UserController` e o `UserService` ainda recebem `fotoUrl` como texto
> e validam uma URL HTTPS de um host configurado como exemplo. Portanto,
> o upload binário e o armazenamento da imagem no PostgreSQL ainda
> precisam ser alinhados no backend. Não considere o armazenamento da
> foto como concluído nesta versão.

### Acessibilidade e personalização

A interface oferece controles para: - aumentar o tamanho do texto; -
ativar alto contraste; - alternar entre paletas de cores; - navegar
pelos controles com rótulos acessíveis; - abrir e fechar o painel de
acessibilidade pelo teclado.

### Identidade visual

-   Cenário inspirado em uma floresta antiga e encantada.
-   Livros pixelados com movimento decorativo.
-   Elementos de iluminação e atmosfera de fantasia.
-   Layout adaptável a diferentes tamanhos de tela.

## 🧰 Tecnologias

  -----------------------------------------------------------------------
Tecnologia                          Responsabilidade
  ----------------------------------- -----------------------------------
Java 17                             Linguagem da aplicação

Spring Boot 4.1.2                   Base da aplicação backend

Spring WebMVC                       Exposição dos endpoints HTTP

Spring Security                     Regras de acesso e contexto de
autenticação

Spring Data JPA                     Persistência e consultas de
entidades

Hibernate                           Mapeamento objeto-relacional

PostgreSQL                          Banco de dados relacional

Supabase                            Hospedagem do PostgreSQL utilizada
na configuração

BCrypt                              Hash de senhas

Jakarta Validation                  Suporte à validação de dados

Lombok                              Redução de código repetitivo

HTML5                               Estrutura da interface

CSS3                                Estilização e responsividade

JavaScript                          Interações, validações de
experiência e requisições HTTP

Maven                               Gerenciamento de dependências e
build

Docker                              Empacotamento e execução em
contêiner
  -----------------------------------------------------------------------

## 🏛️ Arquitetura do projeto

A aplicação utiliza uma organização em camadas. Cada camada possui uma
responsabilidade delimitada, evitando concentrar regras de negócio,
persistência e comunicação HTTP em uma única classe.

``` text
company/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── HouseBook/
│   │   │       └── company/
│   │   │           ├── config/
│   │   │           │   ├── ByCript.java
│   │   │           │   └── SecurityConfig.java
│   │   │           ├── controller/
│   │   │           │   └── UserController.java
│   │   │           ├── dto/
│   │   │           │   └── RegisterRequest.java
│   │   │           ├── model/
│   │   │           │   ├── User.java
│   │   │           │   └── UserRepository.java
│   │   │           ├── service/
│   │   │           │   └── UserService.java
│   │   │           └── CompanyApplication.java
│   │   └── resources/
│   │       ├── static/
│   │       │   ├── index.html
│   │       │   ├── auth.css
│   │       │   └── auth.js
│   │       └── application.yaml
│   └── test/
├── .mvn/
├── Dockerfile
├── mvnw
├── mvnw.cmd
├── pom.xml
└── README.md
```

### Fase 1 --- Configuração (`config`)

Responsável por definir componentes transversais da aplicação.

-   **`ByCript.java`**: disponibiliza um `PasswordEncoder` baseado em
    BCrypt.
-   **`SecurityConfig.java`**: define as rotas públicas e exige
    autenticação para as demais requisições.

### Fase 2 --- Entidade e persistência (`model`)

Responsável pela representação dos dados e pelo acesso ao banco.

-   **`User.java`**: entidade JPA associada à tabela `users`, com
    identificador, nome, e-mail, senha e campo `fotoUrl`.
-   **`UserRepository.java`**: interface baseada em `JpaRepository`, com
    consulta por e-mail.

O e-mail é definido como único no mapeamento da entidade. A aplicação
também normaliza o e-mail antes da consulta e do cadastro.

### Fase 3 --- Regras de negócio (`service`)

O `UserService` concentra as regras do cadastro e da autenticação.

No cadastro, o serviço: 1. normaliza o nome e o e-mail; 2. verifica se o
nome foi informado e respeita o limite de caracteres; 3. valida a
estrutura e o tamanho do e-mail; 4. exige senha entre 8 e 72 caracteres;
5. verifica se o e-mail já existe; 6. aplica BCrypt à senha; 7. persiste
   o usuário; 8. trata conflitos de integridade que podem ocorrer em
   cadastros simultâneos.

No login, o serviço: 1. rejeita campos ausentes ou vazios; 2. normaliza
o e-mail; 3. procura o usuário pelo endereço informado; 4. compara a
senha recebida com o hash armazenado usando `PasswordEncoder.matches`;
5. retorna o usuário somente quando as credenciais são válidas.

### Fase 4 --- Comunicação HTTP (`controller`)

O `UserController` disponibiliza os endpoints de cadastro, login e
logout. Ele recebe as requisições, delega as regras ao serviço e devolve
códigos HTTP compatíveis com o resultado.

O login cria uma autenticação Spring Security, registra o contexto de
segurança na sessão HTTP e mantém o identificador do usuário na sessão.

### Fase 5 --- Contratos de entrada (`dto`)

A pasta `dto` contém objetos destinados ao transporte e à validação de
dados. `RegisterRequest.java` está presente como estrutura de registro.
O endpoint atualmente implementado recebe os campos por `@RequestParam`;
o DTO ainda não está integrado ao método de cadastro.

### Fase 6 --- Interface (`static`)

-   **`index.html`**: estrutura da tela de login e cadastro.
-   **`auth.css`**: identidade visual, layout, animações e adaptação
    para telas menores.
-   **`auth.js`**: alternância dos formulários, prévia de foto,
    validações de experiência, controles de acessibilidade e chamadas à
    API.

A validação executada no JavaScript é uma conveniência para o usuário.
Ela não substitui a validação no servidor, pois qualquer cliente pode
enviar requisições diretamente à API.

## 🔐 Segurança

Segurança é uma responsabilidade distribuída entre backend, banco de
dados, sessão e interface. O HouseBook utiliza as seguintes medidas na
base atual:

### Proteção de senha

As senhas são processadas por `BCryptPasswordEncoder`. O banco deve
armazenar somente o hash produzido pelo encoder, nunca a senha original.

### Verificação de credenciais

O login utiliza `PasswordEncoder.matches()` para comparar a senha
informada com o hash persistido. A API retorna uma mensagem genérica
para credenciais inválidas, evitando distinguir e-mail inexistente de
senha incorreta.

### Sessão e autenticação

Após validar as credenciais, o controlador cria um
`UsernamePasswordAuthenticationToken`, define o `SecurityContext` e o
persiste por meio de `HttpSessionSecurityContextRepository`.

A aplicação também altera o identificador da sessão após o login, medida
relacionada à proteção contra session fixation.

### Controle de acesso

A configuração atual libera a página inicial, os arquivos estáticos e as
rotas de login e cadastro. As demais requisições exigem autenticação.

### Validação de entrada

O serviço aplica limites para nome, e-mail e senha. A consulta por
e-mail normalizado reduz inconsistências causadas por diferenças entre
letras maiúsculas, minúsculas e espaços.

### Cuidados necessários antes de produção

-   Implementar limitação de tentativas de login (*rate limiting*). Essa
    proteção não está implementada no código atual.
-   Revisar a política CSRF. Atualmente, a configuração ignora CSRF
    especificamente em `/user/login` e `/user/register`; isso deve ser
    avaliado antes da publicação.
-   Configurar cookies de sessão com `HttpOnly`, `Secure` em HTTPS e
    `SameSite` adequado ao cenário.
-   Não expor stack traces, credenciais ou detalhes internos em
    respostas HTTP.
-   Validar conteúdo real de arquivos enviados, e não confiar
    exclusivamente no `Content-Type`.
-   Aplicar limites de tamanho no servidor, além dos limites do
    navegador.
-   Configurar segredos e credenciais por variáveis de ambiente.
-   Implementar testes de autorização para garantir que um usuário não
    acesse dados pertencentes a outra conta.
-   Adotar política de atualização e migração de schema controlada em
    vez de depender indefinidamente de `ddl-auto: update`.

> O README descreve o código presente no repositório analisado. Itens
> apresentados como cuidados ou evolução não devem ser interpretados
> como funcionalidades já entregues.

## 🗄️ Banco de dados e configuração

A aplicação utiliza PostgreSQL por meio do driver JDBC e do Spring Data
JPA. A configuração está em `src/main/resources/application.yaml`.

As credenciais são lidas pelas variáveis de ambiente:

Variável        Finalidade
  --------------- -----------------------------
`DB_USERNAME`   Usuário do banco PostgreSQL
`DB_PASSWORD`   Senha do banco PostgreSQL

Não coloque credenciais reais no repositório. Configure essas variáveis
no ambiente local, no provedor de hospedagem ou no serviço de contêiner.

A configuração atual utiliza `ddl-auto: update`, que permite ao
Hibernate atualizar o schema durante o desenvolvimento. Para ambientes
críticos, prefira migrações versionadas com uma ferramenta própria para
esse fim.

### Modelo atual de usuário

  -----------------------------------------------------------------------
Campo                   Tipo Java               Regra observada
  ----------------------- ----------------------- -----------------------
`id`                    `Long`                  Identificador gerado

`name`                  `String`                Não nulo

`email`                 `String`                Não nulo e único

`password`              `String`                Não nulo; deve conter
hash BCrypt

`fotoUrl`               `String`                Opcional; atualmente
representa URL, não
bytes da imagem
  -----------------------------------------------------------------------

**Atenção à foto de perfil:** o modelo atual não utiliza `byte[]` nem
`BYTEA`. Para armazenar a imagem diretamente no PostgreSQL, será
necessário alterar a entidade, o serviço e o controller para receber
`MultipartFile`, validar o arquivo no backend e persistir os bytes em
uma coluna binária. O frontend atual já envia o campo `foto` como
multipart, portanto os contratos precisam ser alinhados antes de
considerar esse fluxo funcional.

## 🚀 Como executar localmente

### Pré-requisitos

-   Java 17 instalado;
-   PostgreSQL acessível;
-   Git;
-   Maven Wrapper incluído no repositório.

### 1. Clonar o repositório

``` bash
git clone https://github.com/SEU-USUARIO/SEU-REPOSITORIO.git
cd SEU-REPOSITORIO
```

Substitua o endereço de exemplo pelo endereço real do repositório.

### 2. Configurar o banco

Crie ou selecione um banco PostgreSQL e obtenha as credenciais de
conexão. A URL JDBC está definida em `application.yaml`; ajuste-a para o
ambiente que será utilizado.

### 3. Definir as variáveis de ambiente

No Linux:

``` bash
export DB_USERNAME="seu_usuario"
export DB_PASSWORD="sua_senha"
```

No Windows PowerShell:

``` powershell
$env:DB_USERNAME="seu_usuario"
$env:DB_PASSWORD="sua_senha"
```

Use valores locais e privados. Não publique senhas em commits, prints ou
arquivos versionados.

### 4. Executar a aplicação

No Linux ou macOS:

``` bash
chmod +x mvnw
./mvnw spring-boot:run
```

No Windows:

``` powershell
.\mvnw.cmd spring-boot:run
```

### 5. Acessar a aplicação

Com a aplicação iniciada, abra:

``` text
http://localhost:8080/
```

A página está em `src/main/resources/static/index.html`, servida pelo
Spring Boot.

## 🔌 Endpoints disponíveis

Os endpoints abaixo correspondem ao `UserController` presente nesta
versão.

Método   Rota               Acesso          Finalidade
  -------- ------------------ --------------- -----------------------------------
`POST`   `/user/register`   Público         Cadastrar usuário
`POST`   `/user/login`      Público         Autenticar usuário e criar sessão
`POST`   `/user/logout`     Requer sessão   Encerrar sessão

### Cadastro --- `POST /user/register`

O controller atual recebe os parâmetros `name`, `email`, `password` e
`fotoUrl`.

Exemplo de requisição `application/x-www-form-urlencoded`:

``` text
name=Ana
email=ana@example.com
password=SenhaForte123
fotoUrl=
```

Respostas previstas: - `201 Created`: cadastro concluído; -
`400 Bad Request`: dados inválidos; - `409 Conflict`: e-mail já
cadastrado.

**Importante:** este contrato reflete o backend atual. Ele não
corresponde ao envio multipart `foto` realizado pelo JavaScript da
interface. Para habilitar upload de arquivo, atualize o controller para
receber `MultipartFile` e alinhe o serviço e a entidade.

### Login --- `POST /user/login`

Recebe `email` e `password` como parâmetros de formulário.

Exemplo:

``` text
email=ana@example.com
password=SenhaForte123
```

Respostas previstas: - `200 OK`: autenticação realizada; -
`401 Unauthorized`: credenciais inválidas.

A autenticação é mantida em sessão HTTP. O cliente deve preservar o
cookie de sessão para as próximas requisições protegidas.

### Logout --- `POST /user/logout`

Invalida a sessão HTTP atual e encerra a sessão do usuário.

Resposta prevista: - `200 OK`: sessão encerrada.

## 🐳 Execução com Docker

O repositório possui um `Dockerfile` em duas etapas: a primeira compila
o projeto com Maven e Java 17; a segunda utiliza uma imagem JRE para
executar o artefato gerado.

Construir a imagem:

``` bash
docker build -t housebook .
```

Executar o contêiner:

``` bash
docker run --rm \
  -p 8080:8080 \
  -e DB_USERNAME="seu_usuario" \
  -e DB_PASSWORD="sua_senha" \
  housebook
```

A aplicação utiliza a porta `8080` por padrão no contêiner, respeitando
a variável `PORT` quando definida.

## ☁️ Publicação

O projeto contém um `Dockerfile` compatível com plataformas que aceitam
deploy por contêiner. Para publicar:

1.  disponibilize o código em um repositório Git;
2.  configure o serviço de deploy para construir a imagem pelo
    `Dockerfile`;
3.  cadastre `DB_USERNAME` e `DB_PASSWORD` como variáveis secretas no
    provedor;
4.  configure a URL do PostgreSQL para o ambiente de produção;
5.  confirme que a porta e o endereço de escuta estão corretos;
6.  valide o cadastro, login, logout e acesso a rotas protegidas após a
    publicação.

Não inclua credenciais do banco no `Dockerfile`, no `application.yaml`
ou no histórico do Git.

## 🧭 Próximas etapas de desenvolvimento

A evolução do HouseBook pode ser organizada em fases independentes,
mantendo cada módulo com responsabilidade própria.

### Fase 1 --- Consolidar autenticação

-   alinhar o contrato do formulário com o controller;
-   concluir o fluxo de upload opcional de foto;
-   implementar rate limiting;
-   revisar CSRF e configuração de sessão;
-   criar testes de cadastro, login e logout.

### Fase 2 --- Perfil do usuário

-   disponibilizar consulta dos dados do usuário autenticado;
-   apresentar nome e foto de perfil;
-   permitir atualização dos dados;
-   permitir substituição ou remoção da foto;
-   garantir que cada conta altere somente os próprios dados.

### Fase 3 --- Catálogo de livros

-   criar entidade e repositório de livros;
-   definir cadastro, edição, consulta e remoção;
-   organizar os dados do catálogo sem misturar responsabilidades com
    autenticação.

### Fase 4 --- Favoritos

-   permitir que cada usuário mantenha sua própria lista;
-   validar a propriedade dos registros;
-   impedir duplicidade de favoritos.

### Fase 5 --- Comentários

-   associar cada comentário ao usuário e ao livro correspondente;
-   validar conteúdo e autoria;
-   definir regras de edição e exclusão.

### Fase 6 --- Histórico e auditoria

-   registrar eventos relevantes da conta;
-   limitar a consulta ao próprio usuário;
-   preservar informações necessárias para rastreabilidade;
-   definir política de retenção de dados.

### Fase 7 --- Qualidade e publicação

-   ampliar testes automatizados;
-   revisar logs e tratamento de erros;
-   documentar contratos da API;
-   aplicar migrações de banco versionadas;
-   monitorar disponibilidade e desempenho.

## 🧪 Testes

Os testes automatizados ficam em `src/test`. Execute a suíte com:

``` bash
./mvnw test
```

A presença de testes no projeto não significa que todos os fluxos
estejam cobertos. Recomenda-se criar cenários específicos para cadastro
válido, e-mail duplicado, senha incorreta, sessão, autorização e upload
de imagens.

## 📌 Estado atual do projeto

  -----------------------------------------------------------------------
Área                                Situação identificada no código
  ----------------------------------- -----------------------------------
Estrutura Spring Boot               Implementada

Persistência PostgreSQL/JPA         Configurada

Cadastro de usuário                 Implementado

Login com BCrypt                    Implementado

Contexto de autenticação em sessão  Implementado

Logout por invalidação da sessão    Implementado

Interface de login e cadastro       Implementada

Personalização e acessibilidade da  Implementadas no frontend
interface

Prévia e validação inicial da foto  Implementadas no frontend

Armazenamento binário da foto no    Pendente de alinhamento
PostgreSQL

Rate limiting                       Pendente

Catálogo, favoritos, comentários e  Etapas futuras
histórico
  -----------------------------------------------------------------------

## 🤝 Contribuição

Contribuições são bem-vindas. Para manter a evolução organizada:

1.  crie uma branch específica para a alteração;
2.  mantenha cada mudança concentrada em uma responsabilidade;
3.  preserve a separação entre controller, service, model e DTO;
4.  adicione ou atualize testes;
5.  descreva no commit o objetivo da alteração;
6.  abra um pull request com contexto, impacto e evidências de teste.

## 📜 Licença

Defina e adicione um arquivo `LICENSE` ao repositório antes de declarar
uma licença de distribuição. Até lá, não presuma permissões de
reutilização do código.

------------------------------------------------------------------------

::: {align="center"}
**HouseBook --- cada livro é um mundo. Cada leitor, uma jornada.**

*Construído com organização, segurança e paixão por histórias.*
:::
