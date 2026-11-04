package org.cef.handler;

import org.cef.callback.CefCallback;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

/**
 * 自定义协议的资源处理器。
 *
 * <p>Android 侧没有 cef_resource_handler 的完整回调路径，host 会一次性把
 * 整个 body 读走再回传给引擎；readResponse 的分块语义在这种实现下仍然成立
 * （数据是同一个缓冲区），只是没有 backpressure。</p>
 */
public interface CefResourceHandler {
    boolean processRequest(CefRequest request, CefCallback callback);

    void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl);

    boolean readResponse(byte[] dataOut, int bytesToRead, IntRef bytesRead, CefCallback callback);

    void cancel();
}
