package br.com.triaige.orchestrator.application.service;

import lombok.Value;

import java.util.UUID;

@Value
public class AuthenticatedCredential {
    UUID lawFirmId;
    UUID apiCredentialId;
}
