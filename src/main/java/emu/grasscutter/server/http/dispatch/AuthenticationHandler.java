package emu.grasscutter.server.http.dispatch;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.auth.AuthenticationSystem;
import emu.grasscutter.auth.MaPassportAuthenticator;
import emu.grasscutter.auth.OAuthAuthenticator.ClientType;
import emu.grasscutter.server.http.Router;
import emu.grasscutter.server.http.objects.*;
import emu.grasscutter.server.http.objects.ComboTokenReqJson.LoginTokenData;
import emu.grasscutter.utils.*;
import io.javalin.Javalin;
import io.javalin.http.Context;

public final class AuthenticationHandler implements Router {
    private static void clientLogin(Context ctx) {
        String rawBodyData = ctx.body();
        var bodyData = JsonUtils.decode(rawBodyData, LoginAccountRequestJson.class);

        if (bodyData == null) return;

        var responseData =
                Grasscutter.getAuthenticationSystem()
                        .getPasswordAuthenticator()
                        .authenticate(AuthenticationSystem.fromPasswordRequest(ctx, bodyData));
        ctx.json(responseData);

        Grasscutter.getLogger()
                .debug(translate("messages.dispatch.account.login_attempt", Utils.address(ctx)));
    }

    private static void tokenLogin(Context ctx) {
        String rawBodyData = ctx.body();
        var bodyData = JsonUtils.decode(rawBodyData, LoginTokenRequestJson.class);

        if (bodyData == null) return;

        var responseData =
                Grasscutter.getAuthenticationSystem()
                        .getTokenAuthenticator()
                        .authenticate(AuthenticationSystem.fromTokenRequest(ctx, bodyData));
        ctx.json(responseData);

        Grasscutter.getLogger()
                .debug(translate("messages.dispatch.account.login_attempt", Utils.address(ctx)));
    }

    private static void sessionKeyLogin(Context ctx) {
        String rawBodyData = ctx.body();
        var bodyData = JsonUtils.decode(rawBodyData, ComboTokenReqJson.class);

        if (bodyData == null || bodyData.data == null) return;

        var tokenData = JsonUtils.decode(bodyData.data, LoginTokenData.class);

        var responseData =
                Grasscutter.getAuthenticationSystem()
                        .getSessionKeyAuthenticator()
                        .authenticate(AuthenticationSystem.fromComboTokenRequest(ctx, bodyData, tokenData));
        ctx.json(responseData);

        Grasscutter.getLogger()
                .debug(translate("messages.dispatch.account.login_attempt", Utils.address(ctx)));
    }

    private static void maPassportLogin(Context ctx) {
        Grasscutter.getLogger().info("Ma-passport login request from: " + Utils.address(ctx));
        
        try {
            String rawBodyData = ctx.body();
            Grasscutter.getLogger().debug("Ma-passport request body: " + rawBodyData);
            
            var request = JsonUtils.decode(rawBodyData, LoginByPasswordRequestJson.class);
            if (request == null) {
                Grasscutter.getLogger().warn("Failed to parse Ma-Passport login request");
                ctx.status(400).result("{\"retcode\":-1,\"message\":\"Invalid Request\",\"data\":null}");
                return;
            }
            
            var response = MaPassportAuthenticator.appLoginByPassword(request);
            
            ctx.json(response);
            
        } catch (Exception e) {
            Grasscutter.getLogger().error("Error in Ma-Passport login", e);
            e.printStackTrace();
            ctx.status(500).result("{\"retcode\":-1,\"message\":\"Internal server error\",\"data\":null}");
        }
    }

    private static void maPassportVerify(Context ctx) {
        Grasscutter.getLogger().info("Ma-passport token verify request from: " + Utils.address(ctx));
        
        try {
            String rawBodyData = ctx.body();
            Grasscutter.getLogger().debug("Ma-passport verify body: " + rawBodyData);
            
            var request = JsonUtils.decode(rawBodyData, VerifySTokenRequestJson.class);
            if (request == null) {
                Grasscutter.getLogger().warn("Failed to parse Ma-Passport verify request");
                ctx.status(400).result("{\"retcode\":-1,\"message\":\"Invalid Request\",\"data\":null}");
                return;
            }

            var response = MaPassportAuthenticator.verifySToken(request);

            ctx.json(response);
            
        } catch (Exception e) {
            Grasscutter.getLogger().error("Error in Ma-Passport verify", e);
            e.printStackTrace();
            ctx.status(500).result("{\"retcode\":-1,\"message\":\"Internal server error\",\"data\":null}");
        }
    }

    @Override
    public void applyRoutes(Javalin javalin) {
        javalin.post("/hk4e_global/mdk/shield/api/login", AuthenticationHandler::clientLogin);
        javalin.post("/hk4e_global/mdk/shield/api/verify", AuthenticationHandler::tokenLogin);
        javalin.post(
                "/hk4e_global/combo/granter/login/v2/login", AuthenticationHandler::sessionKeyLogin);
        javalin.post("/hk4e_global/account/ma-passport/api/appLoginByPassword", AuthenticationHandler::maPassportLogin);
        javalin.post("/hk4e_global/account/ma-passport/token/verifySToken", AuthenticationHandler::maPassportVerify);

        javalin.post("/hk4e_cn/mdk/shield/api/login", AuthenticationHandler::clientLogin);
        javalin.post("/hk4e_cn/mdk/shield/api/verify", AuthenticationHandler::tokenLogin);
        javalin.post("/hk4e_cn/combo/granter/login/v2/login", AuthenticationHandler::sessionKeyLogin);
        javalin.post("/hk4e_cn/account/ma-passport/api/appLoginByPassword", AuthenticationHandler::maPassportLogin);
        javalin.post("/hk4e_cn/account/ma-passport/token/verifySToken", AuthenticationHandler::maPassportVerify);
        javalin.post("/account/ma-cn-passport/app/loginByPassword", AuthenticationHandler::maPassportLogin);
        javalin.post("/account/ma-cn-passport/token/verifySToken", AuthenticationHandler::maPassportVerify);

        javalin.get(
                "/authentication/type",
                ctx -> ctx.result(Grasscutter.getAuthenticationSystem().getClass().getSimpleName()));
        javalin.post(
                "/authentication/login",
                ctx ->
                        Grasscutter.getAuthenticationSystem()
                                .getExternalAuthenticator()
                                .handleLogin(AuthenticationSystem.fromExternalRequest(ctx)));
        javalin.post(
                "/authentication/register",
                ctx ->
                        Grasscutter.getAuthenticationSystem()
                                .getExternalAuthenticator()
                                .handleAccountCreation(AuthenticationSystem.fromExternalRequest(ctx)));
        javalin.post(
                "/authentication/change_password",
                ctx ->
                        Grasscutter.getAuthenticationSystem()
                                .getExternalAuthenticator()
                                .handlePasswordReset(AuthenticationSystem.fromExternalRequest(ctx)));

        javalin.post(
                "/hk4e_global/mdk/shield/api/loginByThirdparty",
                ctx ->
                        Grasscutter.getAuthenticationSystem()
                                .getOAuthAuthenticator()
                                .handleLogin(AuthenticationSystem.fromExternalRequest(ctx)));
        javalin.get(
                "/authentication/openid/redirect",
                ctx ->
                        Grasscutter.getAuthenticationSystem()
                                .getOAuthAuthenticator()
                                .handleTokenProcess(AuthenticationSystem.fromExternalRequest(ctx)));
        javalin.get(
                "/sdkFacebookLogin.html",
                ctx ->
                        Grasscutter.getAuthenticationSystem()
                                .getOAuthAuthenticator()
                                .handleRedirection(
                                        AuthenticationSystem.fromExternalRequest(ctx), ClientType.DESKTOP));
        javalin.get(
                "/Api/twitter_login",
                ctx ->
                        Grasscutter.getAuthenticationSystem()
                                .getOAuthAuthenticator()
                                .handleRedirection(
                                        AuthenticationSystem.fromExternalRequest(ctx), ClientType.DESKTOP));
        javalin.get(
                "/sdkTwitterLogin.html",
                ctx ->
                        Grasscutter.getAuthenticationSystem()
                                .getOAuthAuthenticator()
                                .handleRedirection(
                                        AuthenticationSystem.fromExternalRequest(ctx), ClientType.MOBILE));
    }
}