package org.cef.event;

public class CefMouseWheelEvent extends CefMouseEvent {
    public int deltaX;
    public int deltaY;

    public CefMouseWheelEvent(int x, int y, int modifiers, int deltaX, int deltaY) {
        super(x, y, modifiers);
        this.deltaX = deltaX;
        this.deltaY = deltaY;
    }
}
