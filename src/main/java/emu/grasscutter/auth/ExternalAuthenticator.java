package emu.grasscutter.auth;

import emu.grasscutter.auth.AuthenticationSystem.AuthenticationRequest;

public interface ExternalAuthenticator {

    void handleLogin(AuthenticationRequest request);

    void handleAccountCreation(AuthenticationRequest request);

    void handlePasswordReset(AuthenticationRequest request);
}
