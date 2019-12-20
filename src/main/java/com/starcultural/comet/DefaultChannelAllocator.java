package com.starcultural.comet;

public class DefaultChannelAllocator implements IChannelAllocator {

    @Override
    public Channel allocate() {
        return new Channel();
    }

}