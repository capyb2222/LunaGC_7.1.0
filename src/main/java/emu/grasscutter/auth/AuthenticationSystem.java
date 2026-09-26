package emu.grasscutter.auth;

import emu.grasscutter.game.Account;
import emu.grasscutter.server.http.objects.*;
import emu.grasscutter.utils.DispatchUtils;
import io.javalin.http.Context;
import javax.annotation.Nullable;
import lombok.*;

public interface AuthenticationSystem {

    static AuthenticationRequest fromPasswordRequest(Context ctx, LoginAccountRequestJson jsonData) {
        return AuthenticationRequest.builder().context(ctx).passwordRequest(jsonData).build();
    }

    static AuthenticationRequest fromTokenRequest(Context ctx, LoginTokenRequestJson jsonData) {
        return AuthenticationRequest.builder().context(ctx).tokenRequest(jsonData).build();
    }

    static AuthenticationRequest fromComboTokenRequest(
            Context ctx, ComboTokenReqJson jsonData, ComboTokenReqJson.LoginTokenData tokenData) {
        return AuthenticationRequest.builder()
                .context(ctx)
                .sessionKeyRequest(jsonData)
                .sessionKeyData(tokenData)
                .build();
    }

    static AuthenticationRequest fromExternalRequest(Context ctx) {
        return AuthenticationRequest.builder().context(ctx).build();
    }

    void createAccount(String username, String password);

    void resetPassword(String username);

    Account verifyUser(String details);

    Authenticator<LoginResultJson> getPasswordAuthenticator();

    Authenticator<LoginResultJson> getTokenAuthenticator();

    Authenticator<ComboTokenResJson> getSessionKeyAuthenticator();

    Authenticator<Account> getSessionTokenValidator();

    ExternalAuthenticator getExternalAuthenticator();

    OAuthAuthenticator getOAuthAuthenticator();

    HandbookAuthenticator getHandbookAuthenticator();

    @Builder
    @AllArgsConstructor
    @Getter
    class AuthenticationRequest {
        @Nullable private final Context context;

        @Nullable private final LoginAccountRequestJson passwordRequest;
        @Nullable private final LoginTokenRequestJson tokenRequest;
        @Nullable private final ComboTokenReqJson sessionKeyRequest;
        @Nullable private final ComboTokenReqJson.LoginTokenData sessionKeyData;
    }
}
