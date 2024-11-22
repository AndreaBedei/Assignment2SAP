package sap.ass2.admingui;

import java.net.MalformedURLException;
import java.net.URI;
import sap.ass2.admingui.gui.AdminGUI;
import sap.ass2.admingui.library.*;

public class AdminGuiLauncher {
    public static void main(String[] args) throws MalformedURLException {
        ApplicationAPI application = new ApplicationImpl(URI.create("http://localhost:10000").toURL());
        AdminGUI gui = new AdminGUI(application);
        gui.display();
    }
}
