package emu.grasscutter.server.http.objects;

import lombok.Builder;

@Builder
public class LoginTokenRequestJson {
    public String uid;
    public String token;
}
