package com.go.ski.auth.oauth.client;

import com.go.ski.auth.oauth.config.KakaoOauthConfig;
import com.go.ski.auth.oauth.dto.KakaoMemberResponse;
import com.go.ski.auth.oauth.dto.KakaoToken;
import com.go.ski.auth.oauth.type.OauthServerType;
import com.go.ski.user.core.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoMemberClient implements OauthMemberClient {
    private final KakaoApiClient kakaoApiClient;
    private final KakaoOauthConfig kakaoOauthConfig;

    @Override
    public OauthServerType supportServer() {
        return OauthServerType.kakao;
    }

    @Override
    public User fetch(String authCode, String accessToken) {
        if (authCode != null) {
            KakaoToken tokenInfo = kakaoApiClient.fetchToken(tokenRequestParams(authCode));
            accessToken = tokenInfo.accessToken();
        }
        KakaoMemberResponse kakaoMemberResponse = kakaoApiClient.fetchMember("Bearer " + accessToken);
        return kakaoMemberResponse.toDomain();
    }

    private MultiValueMap<String, String> tokenRequestParams(String authCode) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakaoOauthConfig.clientId());
        params.add("redirect_uri", kakaoOauthConfig.redirectUri());
        params.add("code", authCode);
        params.add("client_secret", kakaoOauthConfig.clientSecret());
        log.info("{}", params);
        return params;
    }
}