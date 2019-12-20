package com.starcultural.comet;

public class Channel {
    /**
     * 渠道名称
     */
    public String cname;

    /**
     * 接口调用口令，由服务端下发
     */
    public String token;

    /**
     * 消息读取的位置，该值应该被递增，如果传入 0 则不收取历史消息，传入 1 将收取所有历史消息
     * 注：服务端只会保留最近 10 条消息
     */
    public int seq;

    public Channel() {
        this("");
    }

    public Channel(String cname) {
        this(cname, "", 0);
    }

    public Channel(String cname, String token, int seq) {
        this.cname = cname;
        this.token = token;
        this.seq = seq;
    }
}