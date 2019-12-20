package com.starcultural.comet;

public interface IChannelAllocator {
    /**
     * 该方法必须返回 Channel 的实例
     *
     * @return Channel channel
     */
    Channel allocate();
}