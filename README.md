# HouseBook

**Biblioteca pessoal e ambiente de leitura digital.**

O HouseBook é uma aplicação web desenvolvida em Java e Spring Boot para cadastro e autenticação de usuários, pesquisa de livros no catálogo Gutendex/Project Gutenberg, organização de uma biblioteca individual e acompanhamento da leitura por capítulos.

> **Proprietário e desenvolvedor:** João.  
> **Apoio de desenvolvimento:** ferramentas de inteligência artificial, incluindo Claude (Anthropic) e ChatGPT/GPT (OpenAI), foram utilizadas como apoio durante etapas de planejamento, consulta técnica, análise de alternativas, depuração e elaboração da documentação. As ferramentas atuaram como assistentes; as decisões técnicas, integração, validação e responsabilidade pelo projeto permanecem com o proprietário/desenvolvedor.

---

## 1. Visão geral

O sistema oferece:

- Cadastro de conta com nome, e-mail e senha.
- Autenticação baseada em sessão HTTP.
- Consulta e atualização dos dados do perfil.
- Foto de perfil opcional.
- Pesquisa de livros por meio da API Gutendex.
- Inclusão e remoção de livros na biblioteca individual.
- Listagem de livros populares.
- Abertura de livros e consulta de capítulos.
- Importação de conteúdo em arquivo TXT.
- Salvamento do capítulo e parágrafo atuais.
- Marcação de leitura como concluída.

O frontend estático utiliza HTML, CSS e JavaScript. O backend expõe endpoints REST e utiliza Spring Security, Spring Data JPA e PostgreSQL.

## 2. Tecnologias

| Tecnologia | Uso |
|---|---|
| Java 17 | Linguagem da aplicação |
| Spring Boot 4.1.2 | Estrutura e inicialização do backend |
| Spring WebMVC | Endpoints HTTP/REST |
| Spring Security | Autenticação, sessão e autorização |
| Spring Data JPA / Hibernate | Persistência e ORM |
| PostgreSQL | Banco de dados relacional |
| Supabase | Serviço PostgreSQL configurado para o ambiente |
| BCrypt | Hash de senhas |
| Jakarta Validation | Validação de parâmetros e DTOs |
| Maven | Dependências e build |
| springdoc-openapi 3.0.1 | Geração da especificação OpenAPI e interface Swagger UI |
| HTML5, CSS3 e JavaScript | Interface web |
| Gutendex / Project Gutenberg | Catálogo externo e origem de textos |

## 3. Arquitetura

A aplicação segue uma organização em camadas:

```text
src/main/java/HouseBook/company/
├── config/       # Segurança, tratamento de exceções e OpenAPI
├── controller/   # Endpoints HTTP
├── dto/          # Objetos de resposta e transferência
├── enums/        # Estados de leitura
├── model/        # Entidades e repositórios JPA
├── Request/      # Estruturas de entrada
├── service/      # Regras de negócio e integrações externas
└── util/         # Processamento de texto e capas
```

### Fluxo simplificado

1. O cliente envia uma requisição HTTP.
2. O Spring Security verifica a sessão e as regras de acesso.
3. O controller valida e encaminha a operação.
4. A camada de serviço executa as regras de negócio.
5. Os repositórios JPA acessam o PostgreSQL quando necessário.
6. O controller devolve o status HTTP e o corpo da resposta.

## 4. Documentação Swagger / OpenAPI

A documentação interativa é gerada automaticamente a partir dos controllers e das anotações OpenAPI.

Com a aplicação em execução:

- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`
- **OpenAPI YAML:** `http://localhost:8080/v3/api-docs.yaml`

Os caminhos `/swagger-ui/**` e `/v3/api-docs/**` estão liberados para consulta, sem exigir login. As operações que trabalham com a biblioteca e a leitura estão identificadas como protegidas por sessão. O Swagger também documenta a resposta HTTP `429` gerada pelo Rate Limiting.

### Autenticação no Swagger

O login é realizado por `POST /user/login`. Quando as credenciais são válidas, o servidor cria uma sessão e retorna o cookie `JSESSIONID`.

Para testar as demais rotas:

1. Execute `GET /user/csrf` e obtenha o token CSRF.
2. Execute `POST /user/login` com `email` e `password`.
3. Preserve o cookie `JSESSIONID` retornado pelo servidor.
4. Nas operações protegidas por CSRF, envie o token no cabeçalho `X-CSRF-TOKEN`.
5. Envie o cookie de sessão nas chamadas seguintes.

O Swagger UI pode não compartilhar automaticamente a sessão com todos os navegadores ou ambientes de proxy. Se a sessão não for mantida na interface, utilize um cliente HTTP que preserve cookies, como Bruno, Insomnia ou Postman.

## 5. Referência dos endpoints

### 5.1 Conta e autenticação

Base: `/user`

| Método | Endpoint | Acesso | Descrição |
|---|---|---|---|
| POST | `/user/register` | Público | Cadastra usuário via `multipart/form-data` |
| POST | `/user/login` | Público | Autentica e cria sessão |
| GET | `/user/csrf` | Público | Obtém token CSRF |
| GET | `/user/me` | Sessão | Consulta os dados da conta autenticada |
| GET | `/user/foto` | Sessão | Retorna a foto de perfil |
| POST | `/user/profile` | Sessão | Atualiza nome e foto |
| POST | `/user/logout` | Sessão | Invalida a sessão |

#### Cadastro — `POST /user/register`

Content-Type: `multipart/form-data`

| Campo | Tipo | Obrigatório | Regra |
|---|---|---:|---|
| `name` | string | Sim | Não vazio; até 100 caracteres |
| `email` | string | Sim | E-mail válido; até 254 caracteres |
| `password` | string | Sim | De 8 a 72 caracteres |
| `foto` | arquivo | Não | JPG, PNG ou WEBP; até 2 MB |

Respostas principais: `201 Created`, `400 Bad Request` e `409 Conflict`.

#### Login — `POST /user/login`

Parâmetros de formulário:

| Campo | Tipo | Obrigatório |
|---|---|---:|
| `email` | string | Sim |
| `password` | string | Sim |

Respostas: `200 OK` com criação da sessão ou `401 Unauthorized` para credenciais inválidas.

#### Consultar conta — `GET /user/me`

Resposta de sucesso:

```json
{
  "id": 12,
  "name": "Nome do usuário",
  "email": "usuario@exemplo.com",
  "hasPhoto": true
}
```

Os valores acima são ilustrativos.

#### Atualizar perfil — `POST /user/profile`

Content-Type: `multipart/form-data`

- `name`: nome atualizado, obrigatório.
- `foto`: nova imagem, opcional.

#### Foto — `GET /user/foto`

Retorna os bytes da imagem com o tipo MIME armazenado. Respostas possíveis: `200 OK`, `401 Unauthorized` ou `404 Not Found`.

### 5.2 Livros e biblioteca

Base: `/api/books`

As operações deste grupo exigem sessão autenticada.

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/api/books/mine` | Lista os livros da biblioteca do usuário |
| GET | `/api/books/search` | Pesquisa no catálogo Gutendex |
| POST | `/api/books/acquire/{googleBookId}` | Adiciona um livro à biblioteca |
| POST | `/api/books/history/{userBookId}` | Marca o livro como concluído |
| DELETE | `/api/books/{userBookId}` | Remove o livro da biblioteca |
| GET | `/api/books/new` | Retorna livros populares da Gutendex |

#### Pesquisa — `GET /api/books/search`

| Parâmetro | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `q` | string | Sim | Título, autor ou termo de pesquisa |
| `language` | string | Não | Filtro de idioma |
| `topic` | string | Não | Filtro de assunto |
| `mimeType` | string | Não | Filtro de formato MIME |

Exemplo:

```http
GET /api/books/search?q=sherlock&language=en
```

#### Adicionar livro — `POST /api/books/acquire/{googleBookId}`

O identificador deve ser numérico e corresponder a um registro disponível na Gutendex. A operação cria ou reutiliza o registro global do livro e cria o vínculo com a biblioteca do usuário autenticado.

Respostas principais: `201 Created`, `400 Bad Request`, `404 Not Found` e `409 Conflict` quando o livro já pertence à biblioteca.

#### Remover livro — `DELETE /api/books/{userBookId}`

Remove somente o vínculo do livro com a biblioteca do usuário autenticado. Resposta de sucesso: `204 No Content`.

### 5.3 Leitura e capítulos

Base: `/api/reader`

Todas as operações exigem sessão autenticada e verificam se o livro pertence à biblioteca do usuário.

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/api/reader/{userBookId}` | Abre o livro e retorna o estado da leitura |
| GET | `/api/reader/{userBookId}/chapters` | Lista os capítulos |
| GET | `/api/reader/{userBookId}/chapters/{number}` | Retorna o conteúdo de um capítulo |
| POST | `/api/reader/{userBookId}/content` | Importa conteúdo TXT |
| PATCH | `/api/reader/{userBookId}/progress` | Atualiza o progresso |
| POST | `/api/reader/{userBookId}/complete` | Marca a leitura como concluída |

#### Importar conteúdo — `POST /api/reader/{userBookId}/content`

Content-Type: `multipart/form-data`

| Campo | Tipo | Obrigatório | Regra |
|---|---|---:|---|
| `file` | arquivo | Sim | Arquivo `.txt`, até 5 MB |

O processador tenta reconhecer capítulos, remove elementos editoriais comuns do Project Gutenberg e divide textos longos em partes. O caractere de formulário (`\f`) pode ser usado para separar capítulos manualmente.

A importação não permite substituir o texto oficial do Project Gutenberg quando ele já está disponível e processado para o livro.

#### Atualizar progresso — `PATCH /api/reader/{userBookId}/progress`

Content-Type: `application/json`

```json
{
  "currentChapter": 2,
  "currentParagraph": 14
}
```

- `currentChapter`: obrigatório, mínimo 1.
- `currentParagraph`: obrigatório, mínimo 0.

Resposta de sucesso: `204 No Content`.

## 6. Estados de leitura

O enum `ReadingStatus` contém:

| Estado | Significado |
|---|---|
| `WANT_TO_READ` | Deseja ler |
| `READING` | Em leitura |
| `COMPLETED` | Concluído |
| `PAUSED` | Pausado |
| `NAO_INICIADO` | Estado legado |
| `EM_ANDAMENTO` | Estado legado |
| `LIDO` | Estado legado |

Os três últimos valores existem para compatibilidade com registros de versões anteriores.

## 7. Modelo de dados

### User

Armazena a conta do usuário:

- identificador;
- nome;
- e-mail único;
- hash da senha;
- bytes da foto e tipo MIME.

### Book

Armazena os metadados compartilhados do catálogo:

- identificador externo Gutendex;
- título, autores e descrição;
- editora e ISBN;
- capa e URL do conteúdo;
- idioma, assuntos e contagem de downloads;
- formatos disponibilizados.

### UserBook

Representa a relação entre uma conta e um livro:

- usuário e livro;
- data de aquisição;
- estado de leitura;
- capítulo e parágrafo atuais;
- datas de início e conclusão;
- página atual e avaliação pessoal.

A restrição única sobre `user_id` e `book_id` impede que o mesmo livro seja adicionado duas vezes à mesma biblioteca.

### BookChapter

Armazena capítulos processados associados a um livro, incluindo número, título e conteúdo.

## 8. Integrações externas

### Gutendex

Utilizada para pesquisar livros, carregar a seleção popular e recuperar metadados. A aplicação consulta `https://gutendex.com`.

### Project Gutenberg

Quando há URL de texto integral disponível, o backend pode baixar o conteúdo para processamento e leitura. O cliente de download aceita apenas hosts pertencentes a `gutenberg.org` via HTTPS, aplica timeout e limita o tamanho do conteúdo.

A disponibilidade dessas operações depende dos serviços externos e da existência de formatos compatíveis no catálogo.

## 9. Segurança

Medidas identificadas no código:

- Senhas armazenadas com BCrypt.
- Autenticação por sessão HTTP.
- Troca do identificador de sessão após login.
- Proteção CSRF do Spring Security, com exceção explícita para login e cadastro.
- Verificação de propriedade do livro antes de operações de leitura.
- Validação de nome, e-mail, senha e arquivo de imagem.
- Restrição de tipo e tamanho para arquivos enviados.
- Validação de host e protocolo na obtenção de textos externos.
- Tratamento centralizado de erros sem exposição deliberada de detalhes internos inesperados.\n- Rate Limiting por IP para reduzir abuso de endpoints de autenticação, cadastro e demais rotas dinâmicas.

**Pontos para revisão antes de produção:** substituir o armazenamento em memória do Rate Limiting por uma solução distribuída caso existam múltiplas instâncias, revisar políticas de cookies `Secure`, `HttpOnly` e `SameSite` conforme o ambiente, avaliar cabeçalhos de segurança, configurar CORS somente se necessário e manter credenciais exclusivamente em variáveis de ambiente/segredos do provedor.

## 10. Rate Limiting

O backend aplica limitação de requisições por endereço IP por meio do filtro `RateLimithing`, implementado com `OncePerRequestFilter` e janelas fixas de tempo. O filtro é executado antes do processamento dos controllers e mantém contadores separados para autenticação, cadastro e demais rotas dinâmicas.

| Grupo | Método e rota | Limite por IP | Janela |
|---|---|---:|---|
| Login | `POST /user/login` | 5 requisições | 1 minuto |
| Cadastro | `POST /user/register` | 3 requisições | 1 hora |
| Demais rotas dinâmicas | Todas as outras rotas não estáticas | 120 requisições | 1 minuto |

Os grupos de login, cadastro e rotas gerais possuem contadores independentes. Portanto, as chamadas de login e cadastro também passam pelo respectivo limite específico, em vez de consumir o contador geral. Recursos estáticos comuns (HTML, CSS, JavaScript, favicon, imagens e assets) não consomem o limite.

### Resposta quando o limite é excedido

Quando o cliente ultrapassa o limite, a API interrompe o processamento e retorna `429 Too Many Requests`, sem executar a operação solicitada.

Exemplo de resposta:

```json
{
  "status": 429,
  "message": "Limite de requisições excedido. Tente novamente mais tarde.",
  "retryAfterSeconds": 42
}
```

Cabeçalhos retornados:

| Cabeçalho | Significado |
|---|---|
| `Retry-After` | Segundos que o cliente deve aguardar antes de tentar novamente |
| `X-RateLimit-Limit` | Quantidade máxima de requisições na janela |
| `X-RateLimit-Remaining` | Requisições restantes na janela atual |
| `X-RateLimit-Reset` | Instante Unix, em segundos, em que a janela será reiniciada |

O Swagger identifica a resposta `429` nas operações documentadas. O cliente deve respeitar `Retry-After` e evitar repetir imediatamente uma requisição bloqueada.

### Escopo e observações de implantação

- A identificação do cliente utiliza `HttpServletRequest.getRemoteAddr()`. Em implantação atrás de proxy ou balanceador, configure a infraestrutura para encaminhar a identidade do cliente de forma confiável antes de adaptar essa estratégia. Não confie cegamente em `X-Forwarded-For` enviado pelo próprio cliente.
- Os contadores são mantidos em memória. Em uma implantação com múltiplas instâncias, cada instância terá seus próprios contadores; para um limite global compartilhado, será necessário utilizar armazenamento distribuído, como Redis.
- As janelas são fixas e reiniciam após o período configurado. O controle é por IP, não por conta de usuário.
- Os limites são valores iniciais de proteção e podem ser ajustados conforme o tráfego real e as necessidades do serviço.

## 11. Configuração e execução local

### Pré-requisitos

- JDK 17.
- Maven (ou Maven Wrapper incluído).
- Instância PostgreSQL acessível.

### Variáveis de ambiente

A configuração principal espera:

| Variável | Finalidade |
|---|---|
| `DB_USERNAME` | Usuário do PostgreSQL |
| `DB_PASSWORD` | Senha do PostgreSQL |

A URL JDBC está definida no `application.yaml` para o pooler Supabase do projeto. Para outro ambiente, ajuste a URL por configuração externa ou perfil apropriado.

### Executar

Na raiz do projeto:

```bash
./mvnw clean spring-boot:run
```

Executar testes:

```bash
./mvnw test
```

Empacotar:

```bash
./mvnw clean package
```

Após iniciar, abra `http://localhost:8080/swagger-ui.html`.

## 12. Docker

Construir a imagem:

```bash
docker build -t housebook .
```

Executar:

```bash
docker run --rm -p 8080:8080 \
  -e DB_USERNAME="seu_usuario" \
  -e DB_PASSWORD="sua_senha" \
  housebook
```

O Dockerfile compila a aplicação em uma etapa Maven e executa o JAR em uma imagem JRE.

## 13. Testes automatizados

Os testes existentes cobrem, entre outros pontos:

- inicialização do contexto Spring;
- cadastro válido;
- rejeição de nome vazio, e-mail inválido e senha curta;
- prevenção de e-mail duplicado;
- armazenamento de senha em formato BCrypt;
- autenticação com senha correta/incorreta;
- processamento e divisão de textos em capítulos;
- tratamento de títulos, ilustrações, versos e textos sem capítulos explícitos.

A cobertura não deve ser interpretada como garantia de que todos os fluxos estejam testados. Recomenda-se ampliar os testes de autorização, sessão, CSRF, upload, endpoints de livros e leitura.

## 14. Tratamento de erros

O `ApiExceptionHandler` padroniza respostas de erros para `ResponseStatusException` e excesso de tamanho de upload.

Formato:

```json
{
  "status": 400,
  "message": "Descrição do problema."
}
```

O status e a mensagem variam conforme a operação. Nem todas as exceções da aplicação necessariamente passam pelo mesmo formato.

## 15. Limitações e evolução

A documentação descreve os endpoints encontrados no código desta versão. Não significa que todos os requisitos de produção estejam concluídos.

Evoluções recomendadas:

1. Adicionar monitoramento e alertas para tentativas de autenticação e bloqueios por Rate Limiting.
2. Fortalecer configuração de cookies e cabeçalhos HTTP.
3. Ampliar testes de integração da biblioteca e do leitor.
4. Adotar migrações versionadas de banco de dados.
5. Definir política de backup, logs e observabilidade.
6. Automatizar build, testes e deploy em pipeline CI/CD.

## 16. Créditos e responsabilidade

**HouseBook** — idealizado, desenvolvido e mantido por João.

O uso de Claude e ChatGPT/GPT ocorreu como suporte de inteligência artificial ao processo de desenvolvimento, incluindo auxílio técnico e revisão. Essas ferramentas não são proprietárias do produto e não substituem a autoria, as decisões, a validação ou a responsabilidade do desenvolvedor.

---

*HouseBook — cada livro é um mundo. Cada leitor, uma jornada.*