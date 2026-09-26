package emu.grasscutter.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.Account;
import emu.grasscutter.server.http.objects.*;
import emu.grasscutter.utils.RSADecryptionUtil;

import java.util.ArrayList;

public class MaPassportAuthenticator {
        private static String hashPassword(String password) {
        return BCrypt.withDefaults().hashToString(10, password.toCharArray());
    }

        public static LoginByPasswordResponseJson appLoginByPassword(LoginByPasswordRequestJson request) {
        Grasscutter.getLogger().debug("ma-passport login req detected");
        
        if (request == null) {
            Grasscutter.getLogger().error("Request is null");
            return createLoginErrorResponse(-1, "Invalid request");
        }
        
        if (request.account == null || request.password == null) {
            Grasscutter.getLogger().error("Missing credentials");
            return createLoginErrorResponse(-1, "Missing credentials");
        }
        
        try {
            String username;
            try {
                username = RSADecryptionUtil.decrypt(request.account);
            } catch (Exception e) {
                Grasscutter.getLogger().error("Unable to decrypt account", e);
                return createLoginErrorResponse(-10, "Unable to decrypt account");
            }
            
            String password;
            try {
                password = RSADecryptionUtil.decrypt(request.password);
            } catch (Exception e) {
                Grasscutter.getLogger().error("Unable to decrypt account", e);
                return createLoginErrorResponse(-10, "Unable to decrypt account");
            }
            
            Account account = DatabaseHelper.getAccountByName(username);
            
            if (account == null && emu.grasscutter.config.Configuration.ACCOUNT.autoCreate) {
                account = DatabaseHelper.createAccountWithUid(username, 0);
                Grasscutter.getLogger().info("Auto-created account for: " + username);
            }
            
            if (account == null) {
                Grasscutter.getLogger().info("Account not found: " + username);
                return createLoginErrorResponse(-101, "Account or password error");
            }
            
            if ((account.getPassword() == null || account.getPassword().isEmpty())
                    && password != null && !password.isEmpty()) {
                account.setPassword(hashPassword(password));
                account.save();
                Grasscutter.getLogger().info("Password locked for account: " + username);
            }
            
            if (!account.verifyPassword(password)) {
                Grasscutter.getLogger().info("Password verification failed for: " + username);
                return createLoginErrorResponse(-101, "Account or password error");
            }
            
            
            Grasscutter.getLogger().debug("Generating session key");
            String sessionKey = account.generateV2SessionKey();
            emu.grasscutter.database.DatabaseManager.getGameDatastore().save(account);
            
            Grasscutter.getLogger().info("User " + username + " has successfully logged in");
            return createLoginSuccessResponse(account);
            
        } catch (Exception e) {
            Grasscutter.getLogger().error("Exception: " + e.getClass().getName());
            Grasscutter.getLogger().error("Message: " + e.getMessage());
            e.printStackTrace();
            return createLoginErrorResponse(-1, "Internal server error: " + e.getMessage());
    }
}
    
    public static VerifySTokenResponseJson verifySToken(VerifySTokenRequestJson request) {
        try {
            Grasscutter.getLogger().debug("Ma-passport token verification for mid: " + request.mid);
            
            Account account = DatabaseHelper.getAccountById(request.mid);
            if (account == null) {
                Grasscutter.getLogger().info("Account not found for mid: " + request.mid);
                return createTokenErrorResponse(-101, "For account safety, please log in again");
            }
            
            String accountSessionKey = account.getSessionKey();
            if (accountSessionKey == null || !accountSessionKey.equals(request.stoken)) {
                Grasscutter.getLogger().info(
                        "Adopting stoken for account: " + account.getUsername()
                                + " (old=" + (accountSessionKey == null ? "null" : accountSessionKey.substring(0, Math.min(12, accountSessionKey.length())))
                                + " new=" + (request.stoken == null ? "null" : request.stoken.substring(0, Math.min(12, request.stoken.length())))
                                + ")");
                account.setSessionKey(request.stoken);
                account.save();
            }
            
            Grasscutter.getLogger().debug("Ma-Passport token verification successful for: " + account.getUsername());
            return createTokenSuccessResponse(account);
            
        } catch (Exception e) {
            Grasscutter.getLogger().error("Error in ma-passport token verification", e);
            return createTokenErrorResponse(-1, "Internal server error");
        }
    }
    
    private static LoginByPasswordResponseJson createLoginSuccessResponse(Account account) {
        LoginByPasswordResponseJson response = new LoginByPasswordResponseJson();
        response.retcode = 0;
        response.message = "OK";
        response.data = new LoginByPasswordResponseJson.LoginData();
        
        response.data.token = new LoginByPasswordResponseJson.TokenData();
        response.data.token.token_type = 1;
        response.data.token.token = account.getSessionKey();
        
        response.data.user_info = new LoginByPasswordResponseJson.UserInfoData();
        response.data.user_info.aid = account.getId();
        response.data.user_info.mid = account.getId();
        response.data.user_info.account_name = "";
        response.data.user_info.email = account.getUsername();
        response.data.user_info.is_email_verify = 0;
        response.data.user_info.area_code = "**";
        response.data.user_info.mobile = "";
        response.data.user_info.safe_area_code = "";
        response.data.user_info.safe_mobile = "";
        response.data.user_info.realname = "";
        response.data.user_info.identity_code = "";
        response.data.user_info.rebind_area_code = "";
        response.data.user_info.rebind_mobile = "";
        response.data.user_info.rebind_mobile_time = "315532800";
        response.data.user_info.links = new ArrayList<>();
        response.data.user_info.country = "US";
        response.data.user_info.password_time = "1762297200";
        response.data.user_info.is_adult = 1;
        response.data.user_info.is_email_verify = 1;
        response.data.user_info.password_time = "1762297200";
        response.data.user_info.unmasked_email = "";
        response.data.user_info.unmasked_email_type = 0;
        
        response.data.ext_user_info = new LoginByPasswordResponseJson.ExtUserInfoData();
        response.data.ext_user_info.guardian_email = "";
        response.data.ext_user_info.birth = "0";
        
        response.data.reactivate_action_ticket = "";
        response.data.bind_email_action_ticket = "";
        
        return response;
    }
    
    private static LoginByPasswordResponseJson createLoginErrorResponse(int retcode, String message) {
        LoginByPasswordResponseJson response = new LoginByPasswordResponseJson();
        response.retcode = retcode;
        response.message = message;
        response.data = null;
        return response;
    }
    
    private static VerifySTokenResponseJson createTokenSuccessResponse(Account account) {
        VerifySTokenResponseJson response = new VerifySTokenResponseJson();
        response.retcode = 0;
        response.message = "OK";
        response.data = new VerifySTokenResponseJson.VerifyData();
        
        response.data.user_info = new VerifySTokenResponseJson.UserInfoData();
        response.data.user_info.aid = account.getId();
        response.data.user_info.mid = account.getId();
        response.data.user_info.account_name = "";
        response.data.user_info.email = account.getUsername();
        response.data.user_info.is_email_verify = 0;
        response.data.user_info.area_code = "**";
        response.data.user_info.mobile = "";
        response.data.user_info.safe_area_code = "";
        response.data.user_info.safe_mobile = "";
        response.data.user_info.realname = "";
        response.data.user_info.identity_code = "";
        response.data.user_info.rebind_area_code = "";
        response.data.user_info.rebind_mobile = "";
        response.data.user_info.rebind_mobile_time = "315532800";
        response.data.user_info.links = new ArrayList<>();
        response.data.user_info.country = "US";
        response.data.user_info.password_time = "1762297200";
        response.data.user_info.is_adult = 1;
        response.data.user_info.is_email_verify = 1;
        response.data.user_info.unmasked_email = "";
        response.data.user_info.unmasked_email_type = 0;
        
        response.data.tokens = new ArrayList<>();
        VerifySTokenResponseJson.TokenData tokenData = new VerifySTokenResponseJson.TokenData();
        tokenData.token_type = 1;
        tokenData.token = account.getSessionKey();
        response.data.tokens.add(tokenData);
        
        response.data.ext_user_info = new VerifySTokenResponseJson.ExtUserInfoData();
        response.data.ext_user_info.guardian_email = "";
        response.data.ext_user_info.birth = "0";
        
        return response;
    }
    
    private static VerifySTokenResponseJson createTokenErrorResponse(int retcode, String message) {
        VerifySTokenResponseJson response = new VerifySTokenResponseJson();
        response.retcode = retcode;
        response.message = message;
        response.data = null;
        return response;
    }
}
