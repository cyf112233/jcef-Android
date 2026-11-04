package org.cef.callback;

/** 上游是接口；Android 上没有原生菜单，实现见 CefMenuModelImpl。 */
public interface CefMenuModel {
    int MENUITEMTYPE_NONE = 0;
    int MENUITEMTYPE_COMMAND = 1;
    int MENUITEMTYPE_CHECK = 2;
    int MENUITEMTYPE_RADIO = 3;
    int MENUITEMTYPE_SEPARATOR = 4;
    int MENUITEMTYPE_SUBMENU = 5;

    boolean clear();
    int getCount();
    boolean addSeparator();
    boolean addItem(int commandId, String label);
    boolean addCheckItem(int commandId, String label);
    boolean addRadioItem(int commandId, String label, int groupId);
    boolean remove(int commandId);
    int getCommandIdAt(int index);
    int getType(int commandId);
    String getLabel(int commandId);
    boolean setLabel(int commandId, String label);
    boolean isChecked(int commandId);
    boolean setChecked(int commandId, boolean checked);
    boolean isVisible(int commandId);
    boolean setVisible(int commandId, boolean visible);
}
