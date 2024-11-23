package sap.ass2.admingui.library;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public interface EbikesAPI {
    Future<JsonArray> getAllEbikes();
    Future<JsonObject> createEbike(String ebikeID, double locationX, double locationY);
    Future<Void> removeEbike(String ebikeID);
    Future<JsonArray> subscribeToEbikeEvents(EbikeEventObserver observer);
}