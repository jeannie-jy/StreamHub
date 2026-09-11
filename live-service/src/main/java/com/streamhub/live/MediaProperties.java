package com.streamhub.live;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "streamhub.media")
public class MediaProperties {
    private String rtmpPublishBase;
    private String httpFlvPlayBase;
    private String webrtcPlayBase;

    public String getRtmpPublishBase() {
        return rtmpPublishBase;
    }

    public void setRtmpPublishBase(String rtmpPublishBase) {
        this.rtmpPublishBase = rtmpPublishBase;
    }

    public String getHttpFlvPlayBase() {
        return httpFlvPlayBase;
    }

    public void setHttpFlvPlayBase(String httpFlvPlayBase) {
        this.httpFlvPlayBase = httpFlvPlayBase;
    }

    public String getWebrtcPlayBase() {
        return webrtcPlayBase;
    }

    public void setWebrtcPlayBase(String webrtcPlayBase) {
        this.webrtcPlayBase = webrtcPlayBase;
    }
}
