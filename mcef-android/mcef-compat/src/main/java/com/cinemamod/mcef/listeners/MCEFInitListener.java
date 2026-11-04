package com.cinemamod.mcef.listeners;

/**
 * 初始化回调。签名必须和上游一致：onInit(boolean)。
 * 消费方（WebDisplays）是用 lambda 实现的，参数个数不对会抛 AbstractMethodError。
 */
public interface MCEFInitListener {
    void onInit(boolean success);

    default void onError(String message) {
        System.err.println("[MCEF-Android] init error: " + message);
    }
}
