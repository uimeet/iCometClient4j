package com.starcultural.comet;

/**
 * 连接回调接口
 */
public interface IConnectionCallback {

    /**
     * 连接 Comet 服务端失败时回调
     * @param msg
     */
    void onFail(String msg);

    /**
     * 连接网络发生变化时，读取数据异常后回调
     */
    void onNetworkChanged();

    /**
     * 超时回调
     */
    void onTimeout();

    /**
     * 连接 Comet 服务端成功时回调
     */
    void onSuccess();

    /**
     * 连接被服务端断开或发送链路错误时回调
     */
    void onDisconnect();

    /**
     * 连接由用户主动断开时回调
     */
    void onStop();

    /**
     * 当客户端需要重连服务端时回调
     * @param times 显示当前是第几次重连
     * @return 返回一个布尔值，true 表示停止重连，false 表示继续重连
     */
    boolean onReconnect(int times);

    /**
     * 重连服务端成功时回调
     * @param times
     */
    void onReconnectSuccess(int times);
}