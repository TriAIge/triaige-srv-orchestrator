-- Seed de conveniência para ambiente local/HO (mesmo padrão do V2 anterior do
-- projeto): um escritório e uma credencial de API já ativa, para permitir
-- testar os endpoints sem precisar de um fluxo de cadastro (fora do escopo
-- desta fase). NÃO aplicar em produção.
--
-- Token de teste: "dev-local-token"
-- (token_hash = SHA-256 hex do token acima)
INSERT INTO law_firms (id, nome, cnpj, email_contato, telefone, status, created_at, updated_at)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'Escritório de Testes TriAIge',
    '00000000000100',
    'contato@escritorio-teste.example.com',
    '+55 11 90000-0000',
    'ACTIVE',
    NOW(6),
    NOW(6)
);

INSERT INTO api_credentials (id, law_firm_id, name, token_hash, status, last_used_at, expires_at, created_at, updated_at)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'Credencial de desenvolvimento local',
    'a8b209467de49495388ff13632517e447d243fb5d2f04534e4c2110047c141a2',
    'ACTIVE',
    NULL,
    NULL,
    NOW(6),
    NOW(6)
);
