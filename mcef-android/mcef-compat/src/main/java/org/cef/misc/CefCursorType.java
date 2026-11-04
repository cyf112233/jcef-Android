package org.cef.misc;

/**
 * cef_cursor_type_t。WebDisplays 会用 CefCursorType.fromId(id) 和 CefCursorType.POINTER，
 * 所以这里必须是带 id 的枚举，不能用一堆 int 常量。
 */
public enum CefCursorType {
    POINTER(0), CROSS(1), HAND(2), IBEAM(3), WAIT(4), HELP(5),
    EASTRESIZE(6), NORTHRESIZE(7), NORTHEASTRESIZE(8), NORTHWESTRESIZE(9),
    SOUTHRESIZE(10), SOUTHEASTRESIZE(11), SOUTHWESTRESIZE(12), WESTRESIZE(13),
    NORTHSOUTHRESIZE(14), EASTWESTRESIZE(15), NORTHEASTSOUTHWESTRESIZE(16),
    NORTHWESTSOUTHEASTRESIZE(17), COLUMNRESIZE(18), ROWRESIZE(19),
    MIDDLEPANNING(20), EASTPANNING(21), NORTHPANNING(22), NORTHEASTPANNING(23),
    NORTHWESTPANNING(24), SOUTHPANNING(25), SOUTHEASTPANNING(26),
    SOUTHWESTPANNING(27), WESTPANNING(28), MOVE(29), VERTICALTEXT(30),
    CELL(31), CONTEXTMENU(32), ALIAS(33), NODROP(34), COPY(35),
    NOTALLOWED(36), ZOOMIN(37), ZOOMOUT(38), GRAB(39), GRABBING(40), CUSTOM(41);

    private final int id;

    CefCursorType(int id) { this.id = id; }

    public int getId() { return id; }

    public static CefCursorType fromId(int id) {
        for (CefCursorType t : values()) {
            if (t.id == id) return t;
        }
        return POINTER;
    }
}
