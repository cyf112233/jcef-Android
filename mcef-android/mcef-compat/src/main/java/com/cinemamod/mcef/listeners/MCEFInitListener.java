package com.cinemamod.mcef.listeners;

/**
 * 初始化回调。
 *
 * <p>必须保持"只有一个抽象方法"——消费方（WebDisplays）是用 lambda 实现的，
 * 多一个抽象方法就会在运行时抛 AbstractMethodError。</p>
 */
public interface MCEFInitListener {
    void onInit();

    default void onError(String message) {
        System.err.println("[MCEF-Android] init error: " + message);
    }
}
