package sap.ass2.rides.application;

import java.util.Optional;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import sap.ass2.rides.domain.Ebike.EbikeState;

public interface EbikesManagerRemoteAPI {
    Future<Optional<JsonObject>> getBikeByID(String bikeID);
    Future<Void> updateBike(String bikeID, Optional<EbikeState> state, Optional<Double> locationX, Optional<Double> locationY, Optional<Double> directionX, Optional<Double> directionY, Optional<Double> speed, Optional<Integer> batteryLevel);
    Future<Optional<JsonObject>> subscribeForEbikeEvents(String bikeID, EbikeEventObserver observer);
    void unsubscribeForEbikeEvents(String bikeID, EbikeEventObserver observer);
}