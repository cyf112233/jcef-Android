package org.cef.callback;

import java.util.ArrayList;
import java.util.List;

public class CefMenuModelImpl implements CefMenuModel {

    private final List<Integer> ids = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();

    @Override public boolean clear() { ids.clear(); labels.clear(); return true; }
    @Override public int getCount() { return ids.size(); }
    @Override public boolean addSeparator() { ids.add(-1); labels.add(null); return true; }
    @Override public boolean addItem(int commandId, String label) { ids.add(commandId); labels.add(label); return true; }
    @Override public boolean addCheckItem(int commandId, String label) { return addItem(commandId, label); }
    @Override public boolean addRadioItem(int commandId, String label, int groupId) { return addItem(commandId, label); }

    @Override
    public boolean remove(int commandId) {
        int i = ids.indexOf(commandId);
        if (i < 0) return false;
        ids.remove(i); labels.remove(i);
        return true;
    }

    @Override public int getCommandIdAt(int index) { return ids.get(index); }
    @Override public int getType(int commandId) { return MENUITEMTYPE_COMMAND; }

    @Override
    public String getLabel(int commandId) {
        int i = ids.indexOf(commandId);
        return i < 0 ? null : labels.get(i);
    }

    @Override
    public boolean setLabel(int commandId, String label) {
        int i = ids.indexOf(commandId);
        if (i < 0) return false;
        labels.set(i, label);
        return true;
    }

    @Override public boolean isChecked(int commandId) { return false; }
    @Override public boolean setChecked(int commandId, boolean checked) { return true; }
    @Override public boolean isVisible(int commandId) { return true; }
    @Override public boolean setVisible(int commandId, boolean visible) { return true; }
}
