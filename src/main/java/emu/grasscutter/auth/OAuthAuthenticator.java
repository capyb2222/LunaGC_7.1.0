package emu.grasscutter.auth;

import emu.grasscutter.auth.AuthenticationSystem.AuthenticationRequest;

public interface OAuthAuthenticator {

    void handleLogin(AuthenticationRequest request);

    void handleRedirection(AuthenticationRequest request, ClientType clientType);

    void handleTokenProcess(AuthenticationRequest request);

    enum ClientType {
        DESKTOP,
        MOBILE
    }
}
