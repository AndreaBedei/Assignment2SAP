package sap.ass2.usergui.domain;

import sap.ass2.usergui.library.EbikeState;

public record Ebike(String id, EbikeState state, double locX, double locY, double dirX, double dirY, double speed, int batteryLevel) {
    
}
