package com.starcultural.comet;

import com.starcultural.comet.message.Message;

public interface ICometCallback {

    /**
     * 收到 TYPE_DATA 消息时回调
     * @param content 消息内容
     */
    void onDataMsgArrived(Message.Content content);

    /**
     * 任意消息到达时回调，可能不是 TYPE_DATA 消息
     * @param msg 消息对象
     */
    void onMsgArrived(Message msg);

    /**
     * 收到错误消息时回调
     * @param msg 消息对象
     */
    void onErrorMsgArrived(Message msg);

    /**
     * 收到 TYPE_401 消息时回调
     * 一般情况需要在此回调中去申请新的token，如果返回null或空字符串连接将退出
     * @return
     */
    String onUnAuthorizedErrorMsgArrived();

    /**
     * 消息格式错误时回调，如客户端收到的消息不是一个json时
     */
    void onMsgFormatError(Message msg);

}