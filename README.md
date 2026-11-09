O projeto vai ser estruturado pelas seguintes pastas.
- 'controllers': Contém os controladores que gerenciam as requisições e respostas da aplicação.
- 'models': Contém os modelos de dados que representam as entidades do sistema.
- 'config': Contém arquivos de configuração da aplicação, como variáveis de ambiente e configurações de banco de dados.
- 'service': Contém os serviços que encapsulam a lógica de negócio da aplicação.
- 'dto': Contém os objetos de transferência de dados (DTOs) que são usados para transportar dados entre as camadas da aplicação.
- 'tests': Contém os testes automatizados da aplicação, garantindo a qualidade e o funcionamento correto do código.

<h1 class="">Segurança</h1>

- login e registro - > ByCript e rate limithing para proteção de senha e prevenção de ataques de força bruta.
- historico -> Armazenamento de histórico de login e ações do usuário para auditoria e monitoramento, so o usario com a verificação de identidade.
- livro favorito - > Lugar onde o usuario podera ver seus livros favoritos, com a verificação via id.
- comentarios - > todos os usuarios poderam fazer comentarios do livro.