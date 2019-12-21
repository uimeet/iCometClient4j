package com.starcultural.comet;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.google.gson.JsonSyntaxException;
import com.starcultural.comet.message.Message;
import com.starcultural.comet.message.MessageInputStream;

public class ICometClient {

    /**
     * 客户端状态枚举
     */
    public interface State {
        /**
         * client 刚创建时的状态
         */
        int STATE_NEW = 0;
        // status for a prepared client
        /**
         * client 已就绪
         */
        int STATE_READY = 1;
        /**
         * client 已成功连接服务端
         */
        int STATE_CONNECTED = 2;
        /**
         * client 工作中状态（发送/接受消息）
         */
        int STATE_COMET = 3;
        /**
         * client 被主动停止的装塔提
         */
        int STATE_STOP = 4;
        /**
         * 停止刮起状态
         */
        int STATE_STOP_PENDING = 5;
        /**
         * 由服务端断开连接时的状态，此时通常发生了错误
         */
        int STATE_DISCONNECT = 6;
    }

    private static final String TAG = "Comet.Client";

    /**
     * 与服务端断开后的重连间隔
     */
    private static final int[] DELAY = {5, 30, 60};

    // 连接服务端使用的完整 url
    private String finalUrl;
    // 记录重连次数
    private int mReconnTimes = 0;
    // 频道对象实例
    private Channel mChannel;

    private static ICometClient mClient = new ICometClient();
    private ICometCallback mICometCallback;
    private IConnectionCallback mIConnCallback;

    private HttpURLConnection mConn;
    private MessageInputStream mInput;

    private ICometConf mConf;

    // 日志类
    private Logger mLogger = Logger.getLogger("ICometClient");

    // 当前状态
    private int mStatus = State.STATE_NEW;

    private ICometClient() {

    }

    /**
     * 获取 ICometClient 的实例
     *
     * @return
     */
    public static ICometClient getInstance() {
        if (mClient == null) {
            mClient = new ICometClient();
        }
        return mClient;
    }

    /**
     * 准备连接
     *
     * @param conf
     */
    public void prepare(ICometConf conf) {
        mLogger.info("[prepare]" + conf);

        if (conf.channelAllocator == null) {
            conf.channelAllocator = new DefaultChannelAllocator();
            mLogger.info("[prepare]use DefaultChannelAllocator");
        }
        mConf = conf;
        if (mReconnTimes == 0) {
            this.mChannel = conf.channelAllocator.allocate();
        }
        this.finalUrl = buildURL(conf.url);
        this.mICometCallback = conf.iCometCallback;
        this.mIConnCallback = conf.iConnCallback;
        this.mStatus = State.STATE_READY;

        mLogger.info("[prepare]status change to [READY], finalUrl: " + this.finalUrl);
    }

    /**
     * 连接服务端
     * 请在子线程中调用该方法
     */
    public void connect() {
        mLogger.info("[connect]STATUS:" + this.mStatus);
        if (this.mStatus != State.STATE_READY) {
            return;
        }
        try {
            mConn = (HttpURLConnection) new URL(this.finalUrl).openConnection();
            mConn.setRequestMethod("GET");
            mConn.setConnectTimeout(3 * 60 * 1000);
            mConn.setReadTimeout(3 * 60 * 1000);
            mConn.setDoInput(true);
            mConn.connect();
            mInput = new MessageInputStream(mConn.getInputStream());

        } catch (Exception e) {
            mLogger.severe("[connect]" + e.getMessage());
            if (mConn != null) {
                mConn.disconnect();
            }
            if (mIConnCallback != null) {
                mIConnCallback.onFail(e.getMessage());
            }
            reconnect();
            return;
        }

        this.mStatus = State.STATE_CONNECTED;
        mLogger.info("[connect]status change to [CONNECTED]");

        if (mIConnCallback != null) {
            if (mReconnTimes == 0) {
                mIConnCallback.onSuccess();
            } else {
                mIConnCallback.onReconnectSuccess(mReconnTimes);
                mReconnTimes = 0;
            }
        }

    }

    /**
     * 开启一个子线程进行数据传输
     */
    public void comet() {
        mLogger.info("[comet]STATUS: " + this.mStatus);
        if (this.mStatus != State.STATE_CONNECTED) {
            return;
        }
        this.mStatus = State.STATE_COMET;
        mLogger.info("[comet]status change to [COMET]");
        new SubThread().start();

    }

    /**
     * 停止 comet 操作
     */
    public void stopComet() {
        mStatus = State.STATE_STOP_PENDING;
        mLogger.info("[stopComet]status change to [STOP_PENDING]");
    }

    /**
     * 断开与服务端的连接
     */
    public void stopConnect() {
        if (mConn != null) {
            mLogger.info("[stopConnect]disconnect");
            mConn.disconnect();
            mConn = null;
        }
    }

    /**
     * 获取客户端当前状态
     *
     * @return status
     */
    public int getStatus() {
        return mStatus;
    }

    private void reconnect() {
        reconnect(false);
    }

    /**
     * 当连接丢失或发生错误时重连服务端
     * @param immediate 是否立即重连
     */
    private void reconnect(boolean immediate) {
        mLogger.info("[reconnect]call");
        if (mIConnCallback == null) {
            return;
        }

        TimerTask task = new TimerTask() {

            @Override
            public void run() {
                mReconnTimes++;
                if (!mIConnCallback.onReconnect(mReconnTimes)) {
                    if (mStatus != State.STATE_READY) {
                        prepare(mConf);
                    }
                    mLogger.info("[reconnect]start");
                    connect();
                }
            }
        };

        if (immediate) {
            task.run();
        } else {
            Timer timer = new Timer();
            timer.schedule(task, DELAY[mReconnTimes > 2 ? 2 : mReconnTimes] * 1000);
        }

    }

    /**
     * 断开连接
     */
    private void disconnect() {
        mStatus = ICometClient.State.STATE_DISCONNECT;
        mIConnCallback.onDisconnect();
    }

    /**
     * 创建完整的请求地址
     *
     * @param url
     * @return URL
     */
    private String buildURL(String url) {
        StringBuilder sb = new StringBuilder();
        if (!this.mConf.host.startsWith("http")) {
            sb.append(mConf.enableSSL ? "https://" : "http://");
        }
        sb.append(this.mConf.host);
        if (this.mConf.port > 0) {
            sb.append(":").append(this.mConf.port);
        }
        if (!isEmpty(url)) {
            sb.append("/").append(url);
        }
        if (mChannel == null) {
            return sb.toString();
        }

        sb.append("?");
        sb.append("cname=").append(mChannel.cname);
        sb.append("&").append("seq=").append(mChannel.seq);
        sb.append("&").append("token=").append(mChannel.token);
        return sb.toString();
    }

    /**
     * 接受服务端消息的子线程
     *
     * @author keyleduo
     */
    private class SubThread extends Thread {

        private Gson gson = new Gson();

        @Override
        public void run() {
            super.run();

            if (mICometCallback == null) {
                throw new IllegalArgumentException("There always should be an ICometCallback to deal with the coming data");
            }

            try {
                // 强行退出循环标记
                boolean exitLoop = false;
                while (mStatus == ICometClient.State.STATE_COMET && !exitLoop) {
                    // 这里会发生阻塞
                    Message msg = mInput.readMessage();

                    if (msg != null) {

                        mLogger.info("[SubThread]" + msg);
                        // 消息到达回调
                        mICometCallback.onMsgArrived(msg);

                        switch (msg.type) {
                            case Message.Type.TYPE_BROADCAST:
                            case Message.Type.TYPE_DATA:
                                // 递增消息位置
                                mChannel.seq++;

                                try {
                                    // 解析消息内容
                                    Message.Content content = gson.fromJson(msg.content, Message.Content.class);// 数据消息回调
                                    mICometCallback.onDataMsgArrived(content);
                                } catch (JsonSyntaxException jse) {
                                    mICometCallback.onMsgFormatError(msg);
                                }
                                break;
                            case Message.Type.TYPE_NEXT_SEQ:
                            case Message.Type.TYPE_NOOP:
                                // 心跳消息，不需要做任何处理
                                break;
                            case Message.Type.TYPE_401:
                                mLogger.warning("[SubThread]token expired, renew...");
                                // TOKEN 无效错误
                                String token = mICometCallback.onUnAuthorizedErrorMsgArrived();
                                if (!isEmpty(token)) {
                                    // 设置新的token
                                    mChannel.token = token;
                                    // 抛出一个 TokenRefresh 异常以激活重连
                                    throw new TokenRefreshException();
                                } else {
                                    // 强制退出循环了
                                    exitLoop = true;
                                }
                                break;
                            default:
                                // 错误消息回调
                                mICometCallback.onErrorMsgArrived(msg);
                                break;
                        }

                    } else {
                        // TODO error data

                    }
                }
            } catch (TokenRefreshException tre) {
                mLogger.info("[SubThread]Token Refreshed!");
                mClient.disconnect();
                reconnect(true);
                return;
            }catch (Exception e) {
                e.printStackTrace();
                mClient.disconnect();
                mLogger.info("[SubThread]status change to [DISCONNECT], reconnecting...");
                reconnect();
                return;
            }

            mStatus = ICometClient.State.STATE_STOP;
            mLogger.info("[SubThread]status change to [STOP]");
            if (mIConnCallback != null) {
                mIConnCallback.onStop();
            }
        }
    }

    /**
     * 判断给定字符串是否为空
     *
     * @param source
     * @return
     */
    public boolean isEmpty(String source) {
        if (source == null || source.length() < 1) {
            return true;
        }
        return false;
    }


    /**
     * Token 刷新异常
     */
    public static class TokenRefreshException extends Exception {
        public TokenRefreshException() {
            super("Token refresh");
        }
    }

}