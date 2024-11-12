package sap.ass2.usergui.library;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public interface EbikesManagerRemoteAPI {
    Future<JsonArray> getAllAvailableEbikesIDs();
    Future<JsonObject> subscribeForEbikeEvents(String ebikeID, EbikeEventObserver observer);
    void unsubscribeForEbikeEvents(String ebikeID, EbikeEventObserver observer);    //FIXME: rimuovere parametri da tutte le unsubscribe remote (non sembra siano necessari)
}