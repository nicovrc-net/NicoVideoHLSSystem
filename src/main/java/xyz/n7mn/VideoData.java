package xyz.n7mn;

import java.util.Date;
import java.util.UUID;

public class VideoData {
    private long ExpiryDate;
    private String ID;
    private String CookieID;
    private String MainM3u8;
    private String VideoM3u8;
    private String AudioM3u8;
    private String ProxyIP;
    private int ProxyPort;
    private String CookieNicosid;
    private String CookieDomand_bid;

    public VideoData(){
        this.ExpiryDate = new Date().getTime() + 86400000;
        this.ID = new Date().getTime() +  "_" + UUID.randomUUID().toString().split("-")[0];
        this.CookieID = null;
        this.MainM3u8 = null;
        this.VideoM3u8 = null;
        this.AudioM3u8 = null;
        this.ProxyIP = null;
        this.ProxyPort = 3128;
        this.CookieNicosid = null;
        this.CookieDomand_bid = null;
    }

    public VideoData(long expiryDate, String id, String cookieID, String mainM3u8, String videoM3u8, String audioM3u8, String proxyIP, int proxyPort, String cookieNicosid, String cookieDomand_bid){
        this.ExpiryDate = expiryDate;
        this.ID = id;
        this.CookieID = cookieID;
        this.MainM3u8 = mainM3u8;
        this.VideoM3u8 = videoM3u8;
        this.AudioM3u8 = audioM3u8;
        this.ProxyIP = proxyIP;
        this.ProxyPort = proxyPort;
        this.CookieNicosid = cookieNicosid;
        this.CookieDomand_bid = cookieDomand_bid;
    }

    public long getExpiryDate() {
        return ExpiryDate;
    }

    public void setExpiryDate(long expiryDate) {
        ExpiryDate = expiryDate;
    }

    public String getID() {
        return ID;
    }

    public void setID(String ID) {
        this.ID = ID;
    }

    public String getCookieID() {
        return CookieID;
    }

    public void setCookieID(String cookieID) {
        CookieID = cookieID;
    }

    public String getMainM3u8() {
        return MainM3u8;
    }

    public void setMainM3u8(String mainM3u8) {
        MainM3u8 = mainM3u8;
    }

    public String getVideoM3u8() {
        return VideoM3u8;
    }

    public void setVideoM3u8(String videoM3u8) {
        VideoM3u8 = videoM3u8;
    }

    public String getAudioM3u8() {
        return AudioM3u8;
    }

    public void setAudioM3u8(String audioM3u8) {
        AudioM3u8 = audioM3u8;
    }

    public String getProxyIP() {
        return ProxyIP;
    }

    public void setProxyIP(String proxyIP) {
        ProxyIP = proxyIP;
    }

    public int getProxyPort() {
        return ProxyPort;
    }

    public void setProxyPort(int proxyPort) {
        ProxyPort = proxyPort;
    }

    public String getCookieNicosid() {
        return CookieNicosid;
    }

    public void setCookieNicosid(String cookieNicosid) {
        CookieNicosid = cookieNicosid;
    }

    public String getCookieDomand_bid() {
        return CookieDomand_bid;
    }

    public void setCookieDomand_bid(String cookieDomand_bid) {
        CookieDomand_bid = cookieDomand_bid;
    }
}
