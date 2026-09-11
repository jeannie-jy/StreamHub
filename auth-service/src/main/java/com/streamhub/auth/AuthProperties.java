package com.streamhub.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "streamhub.auth")
public class AuthProperties {
    private Duration accessTtl = Duration.ofMinutes(15);
    private Duration refreshTtl = Duration.ofDays(30);
    private Duration wsTicketTtl = Duration.ofSeconds(30);
    private String refreshCookieName = "streamhub_refresh";
    private boolean cookieSecure;
    private String cookieSameSite = "Lax";

    public Duration getAccessTtl() {
        return accessTtl;
    }

    public void setAccessTtl(Duration accessTtl) {
        this.accessTtl = accessTtl;
    }

    public Duration getRefreshTtl() {
        return refreshTtl;
    }

    public void setRefreshTtl(Duration refreshTtl) {
        this.refreshTtl = refreshTtl;
    }

    public Duration getWsTicketTtl() {
        return wsTicketTtl;
    }

    public void setWsTicketTtl(Duration wsTicketTtl) {
        this.wsTicketTtl = wsTicketTtl;
    }

    public String getRefreshCookieName() {
        return refreshCookieName;
    }

    public void setRefreshCookieName(String refreshCookieName) {
        this.refreshCookieName = refreshCookieName;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public String getCookieSameSite() {
        return cookieSameSite;
    }

    public void setCookieSameSite(String cookieSameSite) {
        this.cookieSameSite = cookieSameSite;
    }
}
