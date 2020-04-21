package com.starcultural.comet;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.google.gson.JsonSyntaxException;
import com.starcultural.comet.message.Message;

import javax.net.ssl.SSLException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSource;

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
    private static final int[] DELAY = {1, 2, 2};

    // 连接服务端使用的完整 url
    private String finalUrl;
    // 记录重连次数
    private int mReconnTimes = 0;
    // 频道对象实例
    private Channel mChannel;

    private ICometCallback mICometCallback;
    private IConnectionCallback mIConnCallback;

    private OkHttpClient mHttpClient;
    private Gson gson = new Gson();

    private ICometConf mConf;

    // 日志类
    private Logger mLogger = Logger.getLogger("ICometClient");

    // 当前状态
    private int mStatus = State.STATE_NEW;

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

    public void connect() {
        if (mHttpClient == null) {
            mHttpClient = new OkHttpClient.Builder()
                    .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                    .retryOnConnectionFailure(true)
                    .pingInterval(3, TimeUnit.SECONDS)
                    .connectTimeout(mConf.connectTimeout, TimeUnit.SECONDS)
                    .readTimeout(mConf.readTimeout, TimeUnit.SECONDS)
                    .writeTimeout(mConf.writeTimeout, TimeUnit.SECONDS)
                    .build();
        } else {
            mHttpClient.dispatcher().cancelAll();
        }

        Request request = new Request.Builder().url(this.finalUrl).build();
        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                disconnect();
                mLogger.severe("[connect]" + e.getMessage());
                if (mIConnCallback != null) {
                    mIConnCallback.onFail(e.getMessage());

                    if (e instanceof SocketTimeoutException) {
                        mIConnCallback.onTimeout();
                    }
                }
                if (!(e instanceof UnknownHostException)) {
                    reconnect();
                }

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // 成功回调
                if (mIConnCallback != null) {
                    mStatus = State.STATE_CONNECTED;
                    if (mReconnTimes == 0) {
                        mIConnCallback.onSuccess();
                    } else {
                        mIConnCallback.onReconnectSuccess(mReconnTimes);
                    }
                    mReconnTimes = 0;
                }

                BufferedSource source = response.body().source();
                try {
                    while (!source.exhausted()) {
                        String s = source.readUtf8Line();
                        onMessageArrived(s);
                    }
                } catch (Exception e) {
                    mLogger.severe("[onResponse]Error: " + e.getMessage());
                    if (mIConnCallback != null) {
                        if (e instanceof SSLException) {
                            // 可能是网络变化造成
                            mIConnCallback.onNetworkChanged();
                        } else if (e instanceof SocketTimeoutException) {
                            // 连接超时
                            mIConnCallback.onTimeout();
                        } else if (e instanceof SocketException && e.getMessage() != null && e.getMessage().equals("Socket closed")) {
                            // 不再重连
                            return;
                        } else {
                            mIConnCallback.onFail(e.getMessage());
                        }
                    }
                    disconnect();
                    reconnect();
                } finally {
                    source.close();
                    call.cancel();
                }
            }
        });
    }

    private void onMessageArrived(String messageContent) {
        if (mICometCallback == null) {
            throw new IllegalArgumentException("There always should be an ICometCallback to deal with the coming data");
        }

        try {
            Message msg = gson.fromJson(messageContent, Message.class);

            if (msg != null) {
                mLogger.info("[onMessageArrived]" + msg);
                // 消息到达回调
                mICometCallback.onMsgArrived(msg);

                switch (msg.type) {
                    case Message.Type.TYPE_BROADCAST:
                    case Message.Type.TYPE_DATA:
                        // 递增消息位置
                        //mChannel.seq = Integer.parseInt(msg.seq);

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
                        mLogger.warning("[onMessageArrived]token expired, renew...");
                        // TOKEN 无效错误
                        String token = mICometCallback.onUnAuthorizedErrorMsgArrived();
                        if (!isEmpty(token)) {
                            // 设置新的token
                            mChannel.token = token;
                        }
                        break;
                    default:
                        // 错误消息回调
                        mICometCallback.onErrorMsgArrived(msg);
                        break;
                }

            } else {
                // TODO error data
                mLogger.info("[SubThread]msg is null, reconnect...");
                disconnect();
                reconnect();
            }
        } catch (Exception e) {
            mLogger.info("[SubThread]status change to [DISCONNECT], reconnecting...");
            e.printStackTrace();
            disconnect();
            reconnect();
        }

    }

    private void stopAllRequests() {
        if (mHttpClient != null) {
            mHttpClient.dispatcher().cancelAll();
        }
    }

    public void stopConnect() {
        stopConnect(true);
    }

    /**
     * 断开与服务端的连接
     */
    public void stopConnect(boolean callOnStop) {
        mStatus = State.STATE_STOP_PENDING;
        stopAllRequests();
        mStatus = State.STATE_STOP;
        if (callOnStop && mIConnCallback != null) {
            mIConnCallback.onStop();
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

    public void reconnect() {
        reconnect(false);
    }

    /**
     * 当连接丢失或发生错误时重连服务端
     *
     * @param immediate 是否立即重连
     */
    public void reconnect(boolean immediate) {
        mLogger.info("[reconnect]call");
        if (mIConnCallback == null) {
            mLogger.info("[mIConnCallback == null]exit reconnect");
            return;
        }
        if (mStatus == State.STATE_CONNECTED) {
            mLogger.info("[reconnect]mStatus == State.STATE_CONNECTED");
            return;
        }

        TimerTask task = new TimerTask() {

            @Override
            public void run() {
                if (!mIConnCallback.onReconnect(mReconnTimes)) {
                    mReconnTimes++;
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
        if (mStatus != State.STATE_DISCONNECT) {
            mStatus = ICometClient.State.STATE_DISCONNECT;
            if (mIConnCallback != null) {
                mIConnCallback.onDisconnect();
            }
        }
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
        if (this.mConf.port > 0 && this.mConf.port != 80 && this.mConf.port != 443) {
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

}