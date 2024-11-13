package sap.ass2.admingui.domain;

import sap.ass2.admingui.library.EbikeState;

public record Ebike(String id, EbikeState state, double locX, double locY, double dirX, double dirY, double speed, int batteryLevel) {
    
}
