package sap.ass2.rides;

import java.net.URL;

import sap.ass2.rides.application.RidesManagerAPI;
import sap.ass2.rides.application.RidesManagerImpl;
import sap.ass2.rides.infrastructure.RidesManagerController;

public class RidesManagerService {
    private RidesManagerAPI ridesManager;
    private RidesManagerController ridesController;
    private URL localAddress;

    public RidesManagerService(URL localAddress) {
        this.localAddress = localAddress;
        this.ridesManager = new RidesManagerImpl();
    }

    public void launch(){
        this.ridesController = new RidesManagerController(this.localAddress.getPort());
        this.ridesController.init(this.ridesManager);
    }
}