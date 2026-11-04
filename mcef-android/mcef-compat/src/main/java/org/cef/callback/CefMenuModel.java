package org.cef.callback;

import java.util.ArrayList;
import java.util.List;

/** 右键菜单模型。Android 上没有原生菜单，这里只作为数据容器。 */
public class CefMenuModel {
    public static final int MENUITEMTYPE_NONE = 0;
    public static final int MENUITEMTYPE_COMMAND = 1;
    public static final int MENUITEMTYPE_CHECK = 2;
    public static final int MENUITEMTYPE_RADIO = 3;
    public static final int MENUITEMTYPE_SEPARATOR = 4;
    public static final int MENUITEMTYPE_SUBMENU = 5;

    private final List<Integer> ids = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();

    public int getCount() { return ids.size(); }
    public boolean clear() { ids.clear(); labels.clear(); return true; }
    public boolean addItem(int commandId, String label) { ids.add(commandId); labels.add(label); return true; }
    public boolean addSeparator() { ids.add(-1); labels.add(null); return true; }
    public boolean addCheckItem(int commandId, String label) { return addItem(commandId, label); }
    public boolean addRadioItem(int commandId, String label, int groupId) { return addItem(commandId, label); }
    public boolean remove(int commandId) {
        int i = ids.indexOf(commandId);
        if (i < 0) return false;
        ids.remove(i); labels.remove(i);
        return true;
    }
    public String getLabel(int commandId) {
        int i = ids.indexOf(commandId);
        return i < 0 ? null : labels.get(i);
    }
    public int getCommandIdAt(int index) { return ids.get(index); }
    public int getType(int commandId) { return MENUITEMTYPE_COMMAND; }
    public boolean isChecked(int commandId) { return false; }
    public boolean setChecked(int commandId, boolean checked) { return true; }
    public boolean isVisible(int commandId) { return true; }
    public boolean setVisible(int commandId, boolean visible) { return true; }
    public boolean setLabel(int commandId, String label) {
        int i = ids.indexOf(commandId);
        if (i < 0) return false;
        labels.set(i, label);
        return true;
    }
}
