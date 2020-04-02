package com.starcultural.comet.message;

import com.google.gson.JsonObject;

import java.io.Serializable;

public class Message {

    /**
     * 消息类型枚举
     */
    public interface Type {

        /**
         * 数据消息
         */
        String TYPE_DATA = "data";

        /**
         * 广播消息
         */
        String TYPE_BROADCAST = "broadcast";

        /**
         * 心跳消息
         */
        String TYPE_NOOP = "noop";

        /**
         * 频道消息订阅者数量超过服务端限制值时返回消息
         */
        String TYPE_429 = "429";

        /**
         * token 无效时返回消息
         */
        String TYPE_401 = "401";

        /**
         * next_seq 消息通常不需要做处理，该消息只有在客户端第一次连接服务端时才下发
         */
        String TYPE_NEXT_SEQ = "next_seq";
    }

    /**
     * 消息类型
     */
    public String type;

    /**
     * 频道名
     */
    public String cname;

    /**
     * 消息位置
     */
    public String seq;

    /**
     * 消息内容
     */
    public String content;

    public static class Content implements Serializable {

        private static final long serialVersionUID = 4340957908804000989L;

        public int type;
        public JsonObject body;
        public String id;

        @Override
        public String toString() {
            return String.format("Content [id: %s, type: %s, body: %s]", id, type, body);
        }

    }

    @Override
    public String toString() {
        return "Message [type=" + type + ", cname=" + cname + ", seq=" + seq + ", content=" + content + "]";
    }

}