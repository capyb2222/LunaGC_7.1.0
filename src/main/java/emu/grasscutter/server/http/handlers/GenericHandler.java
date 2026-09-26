package emu.grasscutter.server.http.handlers;

import static emu.grasscutter.config.Configuration.ACCOUNT;

import emu.grasscutter.*;
import emu.grasscutter.server.http.Router;
import emu.grasscutter.server.http.objects.*;
import io.javalin.Javalin;
import io.javalin.http.Context;

public final class GenericHandler implements Router {
    private static void serverStatus(Context ctx) {
        int playerCount = Grasscutter.getGameServer().getPlayers().size();
        int maxPlayer = ACCOUNT.maxPlayer;
        String version = GameConstants.VERSION;

        ctx.result(
                "{\"retcode\":0,\"status\":{\"playerCount\":"
                        + playerCount
                        + ",\"maxPlayer\":"
                        + maxPlayer
                        + ",\"version\":\""
                        + version
                        + "\"}}");
    }

    @Override
    public void applyRoutes(Javalin javalin) {
        javalin.get(
                "/hk4e_global/mdk/agreement/api/getAgreementInfos",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"marketing_agreements\":[]}}"));
        this.allRoutes(
                javalin,
                "/hk4e_global/combo/granter/api/compareProtocolVersion",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"modified\":true,\"protocol\":{\"id\":0,\"app_id\":4,\"language\":\"en\",\"user_proto\":\"\",\"priv_proto\":\"\",\"major\":7,\"minimum\":0,\"create_time\":\"0\",\"teenager_proto\":\"\",\"third_proto\":\"\"}}}"));

        javalin.post(
                "/account/risky/api/check",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"id\":\"none\",\"action\":\"ACTION_NONE\",\"geetest\":null}}"));

        javalin.get(
                "/combo/box/api/config/sdk/combo",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"vals\":{\"disable_email_bind_skip\":\"false\",\"email_bind_remind_interval\":\"7\",\"email_bind_remind\":\"true\"}}}"));
        javalin.get(
                "/hk4e_global/combo/granter/api/getConfig",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"protocol\":true,\"qr_enabled\":false,\"log_level\":\"INFO\",\"announce_url\":\"https://webstatic-sea.hoyoverse.com/hk4e/announcement/index.html?sdk_presentation_style=fullscreen\\u0026sdk_screen_transparent=true\\u0026game_biz=hk4e_global\\u0026auth_appid=announcement\\u0026game=hk4e#/\",\"push_alias_type\":2,\"disable_ysdk_guard\":false,\"enable_announce_pic_popup\":true}}"));
        javalin.get(
                "/hk4e_global/mdk/shield/api/loadConfig",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"id\":6,\"game_key\":\"hk4e_global\",\"client\":\"PC\",\"identity\":\"I_IDENTITY\",\"guest\":false,\"ignore_versions\":\"\",\"scene\":\"S_NORMAL\",\"name\":\"原神海外\",\"disable_regist\":false,\"enable_email_captcha\":false,\"thirdparty\":[\"fb\",\"tw\"],\"disable_mmt\":false,\"server_guest\":false,\"thirdparty_ignore\":{\"tw\":\"\",\"fb\":\"\"},\"enable_ps_bind_account\":false,\"thirdparty_login_configs\":{\"tw\":{\"token_type\":\"TK_GAME_TOKEN\",\"game_token_expires_in\":604800},\"fb\":{\"token_type\":\"TK_GAME_TOKEN\",\"game_token_expires_in\":604800}}}}"));
        javalin.post(
                "/data_abtest_api/config/experiment/list",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"success\":true,\"message\":\"\",\"data\":[{\"code\":1000,\"type\":2,\"config_id\":\"14\",\"period_id\":\"6036_99\",\"version\":\"1\",\"configs\":{\"cardType\":\"old\"}}]}"));

        this.allRoutes(javalin, "/log/sdk/upload", new HttpJsonResponse("{\"code\":0}"));
        this.allRoutes(javalin, "/sdk/upload", new HttpJsonResponse("{\"code\":0}"));
        javalin.post("/sdk/dataUpload", new HttpJsonResponse("{\"code\":0}"));
        this.allRoutes(javalin, "/perf/config/verify", new HttpJsonResponse("{\"code\":0}"));

        javalin.get("/admin/mi18n/plat_oversea/*", new WebStaticVersionResponse());

        javalin.get("/admin/mi18n/plat_os/*", ctx -> ctx.result("{}"));

        this.allRoutes(javalin, "/hk4e_global/account/ma-passport/api/getConfig",
                new HttpJsonResponse("{\"retcode\":0,\"message\":\"OK\",\"data\":{\"support_reactivate_account\":false,\"enable_ps_bind_account\":false,\"login_mode\":\"account_login\",\"guest_mode\":\"close\",\"realperson_mode\":\"none\",\"safeguard_type\":\"none\",\"apple_login_enabled\":false,\"facebook_login_enabled\":false,\"google_login_enabled\":false,\"twitter_login_enabled\":false}}"));
        this.allRoutes(javalin, "/hk4e_cn/account/ma-passport/api/getConfig",
                new HttpJsonResponse("{\"retcode\":0,\"message\":\"OK\",\"data\":{\"support_reactivate_account\":false,\"enable_ps_bind_account\":false,\"login_mode\":\"account_login\",\"guest_mode\":\"close\",\"realperson_mode\":\"none\",\"safeguard_type\":\"none\",\"apple_login_enabled\":false,\"facebook_login_enabled\":false,\"google_login_enabled\":false,\"twitter_login_enabled\":false}}"));

        javalin.get("/device-fp/api/getExtList",
                new HttpJsonResponse("{\"retcode\":0,\"message\":\"OK\",\"data\":{\"ext_list\":[],\"pkg_list\":[]}}"));
        javalin.get("/combo/box/api/config/sw/precache",
                new HttpJsonResponse("{\"retcode\":0,\"message\":\"OK\",\"data\":{}}"));

        javalin.get("/status/server", GenericHandler::serverStatus);
    }
}
