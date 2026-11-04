package com.cinemamod.mcef;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_CLAMP;

/**
 * OSR 像素 → GL 纹理。
 *
 * <p>只在 {@link #getTextureID()} 里做上传：那一刻 GL 正处于"准备画这张纹理"的完整状态。
 * 试过在 render tick 起点上传，MobileGlues 会在 glTexImage2D 里段错误。</p>
 */
public class MCEFRenderer {

    private int textureID = -1;
    private int width = 0;
    private int height = 0;
    private final boolean transparent;

    private ByteBuffer frame;
    private boolean dirty;

    public MCEFRenderer(boolean transparent) { this.transparent = transparent; }

    public void initialize() {
        if (textureID != -1) return;
        textureID = glGenTextures();
    }

    public boolean isTransparent() { return transparent; }

    /** native 线程调用：只拷贝，不碰 GL。 */
    public synchronized void upload(ByteBuffer rgba, int w, int h) {
        int need = w * h * 4;
        if (rgba.remaining() < need) return;
        if (frame == null || frame.capacity() < need) {
            frame = ByteBuffer.allocateDirect(need);
        }
        frame.clear();
        ByteBuffer src = rgba.duplicate();
        src.limit(src.position() + need);
        frame.put(src);
        frame.flip();
        width = w;
        height = h;
        dirty = true;
    }

    /** 渲染线程调用。 */
    public synchronized int getTextureID() {
        if (textureID == -1) initialize();
        if (dirty && frame != null) {
            glBindTexture(GL_TEXTURE_2D, textureID);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);
            // Cefrium 给的是 RGBA，和 GL_RGBA 直接对齐，不需要桌面 CEF 那个 BGRA 换序
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                    GL_RGBA, GL_UNSIGNED_BYTE, frame);
            dirty = false;
        }
        return textureID;
    }

    public void destroy() {
        if (textureID != -1) {
            glDeleteTextures(textureID);
            textureID = -1;
        }
    }
}
