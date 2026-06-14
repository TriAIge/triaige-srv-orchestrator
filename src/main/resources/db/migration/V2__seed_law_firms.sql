-- Escritório jurídico de exemplo para uso em ambiente de homologação/testes
INSERT INTO law_firms (id, nome, cnpj, email_contato, telefone, status, created_at, updated_at)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'Escritório Demo',
    '00000000000100',
    'contato@escritoriodemo.com.br',
    '+5511999990000',
    'ATIVO',
    NOW(6),
    NOW(6)
);
