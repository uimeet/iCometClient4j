package com.starcultural.comet;

/**
 * 配置类
 */
public class ICometConf {
    public String host;
    public int port;
    public String url;
    public boolean enableSSL;

    public int connectTimeout = 30;
    public int readTimeout = 40;
    public int writeTimeout = 30;

    public IChannelAllocator channelAllocator;
    public ICometCallback iCometCallback;
    public IConnectionCallback iConnCallback;

    public ICometConf(String host, int port, String url, ICometCallback iCometCallback) {
        this(host, port, url, iCometCallback, null, null, false);
    }

    public ICometConf(String host, int port, String url, ICometCallback iCometCallback
            , IChannelAllocator channelAllocator
            , IConnectionCallback iConnCallback) {
        this(host, port, url, iCometCallback, channelAllocator, iConnCallback, false);
    }

    public ICometConf(String host, int port, String url
            , ICometCallback iCometCallback
            , IChannelAllocator channelAllocator
            , IConnectionCallback iConnCallback
            , boolean enableSSL) {
        this.host = host;
        this.port = port;
        this.url = url;

        this.channelAllocator = channelAllocator;
        this.iCometCallback = iCometCallback;
        this.iConnCallback = iConnCallback;

        this.enableSSL = enableSSL;
    }

    @Override
    public String toString() {
        return String.format("ICometConf[%s://%s:%s/%s]", this.enableSSL ? "https" : "http", host, port, url);
    }
}