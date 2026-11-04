package org.cef.callback;

/** 右键菜单上下文参数，只保留 WebDisplays 用得到的部分。 */
public class CefContextMenuParams {
    public static final int CM_TYPEFLAG_NONE = 0;
    public static final int CM_TYPEFLAG_PAGE = 1;
    public static final int CM_TYPEFLAG_FRAME = 1 << 1;
    public static final int CM_TYPEFLAG_LINK = 1 << 2;
    public static final int CM_TYPEFLAG_MEDIA = 1 << 3;
    public static final int CM_TYPEFLAG_SELECTION = 1 << 4;
    public static final int CM_TYPEFLAG_EDITABLE = 1 << 5;

    private final int x, y, typeFlags;
    private final String linkUrl, pageUrl, selectionText;

    public CefContextMenuParams(int x, int y, int typeFlags,
                                String linkUrl, String pageUrl, String selectionText) {
        this.x = x; this.y = y; this.typeFlags = typeFlags;
        this.linkUrl = linkUrl; this.pageUrl = pageUrl; this.selectionText = selectionText;
    }

    public int getXCoord() { return x; }
    public int getYCoord() { return y; }
    public int getTypeFlags() { return typeFlags; }
    public String getLinkUrl() { return linkUrl; }
    public String getSourceUrl() { return pageUrl; }
    public String getPageUrl() { return pageUrl; }
    public String getSelectionText() { return selectionText; }
    public boolean isEditable() { return (typeFlags & CM_TYPEFLAG_EDITABLE) != 0; }
}
