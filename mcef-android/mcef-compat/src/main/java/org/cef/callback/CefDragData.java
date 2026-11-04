package org.cef.callback;

public class CefDragData {
    public static final int DRAG_OPERATION_NONE = 0;
    public static final int DRAG_OPERATION_COPY = 1;
    public static final int DRAG_OPERATION_LINK = 2;
    public static final int DRAG_OPERATION_MOVE = 4;

    private final String text;
    private final String linkUrl;
    public CefDragData(String text, String linkUrl) { this.text = text; this.linkUrl = linkUrl; }
    public String getFragmentText() { return text; }
    public String getLinkURL() { return linkUrl; }
    public boolean isLink() { return linkUrl != null && !linkUrl.isEmpty(); }
}
