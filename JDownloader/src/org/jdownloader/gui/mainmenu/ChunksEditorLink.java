package org.jdownloader.gui.mainmenu;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import javax.swing.JComponent;

import jd.gui.swing.jdgui.menu.ChunksEditor;

import org.jdownloader.actions.AppAction;
import org.jdownloader.controlling.contextmenu.MenuItemData;
import org.jdownloader.controlling.contextmenu.MenuLink;
import org.jdownloader.extensions.ExtensionNotLoadedException;
import org.jdownloader.gui.translate._GUI;

public class ChunksEditorLink extends MenuItemData implements MenuLink {
    @Override
    public JComponent createSettingsPanel() {
        return null;
    }

    @Override
    public List<AppAction> createActionsToLink() {
        return null;
    }

    public ChunksEditorLink() {
        super();
        setName(_GUI._.ChunksEditor_ChunksEditor_());
        setIconKey("chunks");

        //
    }

    public JComponent createItem() throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, SecurityException, ExtensionNotLoadedException {

        ChunksEditor ret = new ChunksEditor();

        return ret;

    }
}
