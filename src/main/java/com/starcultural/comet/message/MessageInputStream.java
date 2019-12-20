package com.starcultural.comet.message;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.google.gson.Gson;

public class MessageInputStream {
    private InputStream input;
    private BufferedReader reader;

    public MessageInputStream(InputStream input) {
        this.input = input;
        reader = new BufferedReader(new InputStreamReader(this.input));
    }

    private String read() throws Exception {
        return reader.readLine();
    }

    /**
     * 从子连接的 Stream 中读取消息内容
     *
     * @return 返回一个通用的消息格式，业务代码可根据消息类型做相应的业务处理
     * @throws Exception
     */
    public Message readMessage() throws Exception {
        Message msg = null;

        Gson gson = new Gson();
        String jsonData = this.read();
        if (jsonData != null) {
            msg = gson.fromJson(jsonData, Message.class);
        }
        return msg;

    }
}