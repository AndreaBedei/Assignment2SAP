package sap.ass2.admingui.library;

import java.net.URL;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RidesManagerProxy implements RidesManagerRemoteAPI {
    private HttpClient client;
	private Vertx vertx;
	private URL ridesManagerAddress;
	
	public RidesManagerProxy(URL ridesManagerAddress) {
		vertx = Vertx.vertx();
		this.ridesManagerAddress = ridesManagerAddress;
		HttpClientOptions options = new HttpClientOptions()
            .setDefaultHost(ridesManagerAddress.getHost())
            .setDefaultPort(ridesManagerAddress.getPort());
		client = vertx.createHttpClient(options);
	}

    @Override
    public Future<JsonArray> getAllRides() {
        Promise<JsonArray> p = Promise.promise();
		client
		.request(HttpMethod.GET, "/api/rides")
		.onSuccess(req -> {
			req.response().onSuccess(response -> {
				response.body().onSuccess(buf -> {
					JsonObject obj = buf.toJsonObject();
					p.complete(obj.getJsonArray("rides"));
				});
			});
			req.send();
		})
		.onFailure(f -> {
			p.fail(f.getMessage());
		});
		return p.future();
    }

    @Override
    public Future<JsonArray> subscribeForRideEvents(RideEventObserver observer) {
        Promise<JsonArray> p = Promise.promise();
		
		WebSocketConnectOptions wsoptions = new WebSocketConnectOptions()
				  .setHost(this.ridesManagerAddress.getHost())
				  .setPort(this.ridesManagerAddress.getPort())
				  .setURI("/api/rides/events")
				  .setAllowOriginHeader(false);
		
		client
		.webSocket(wsoptions)
		.onComplete(res -> {
            if (res.succeeded()) {
                WebSocket ws = res.result();
                System.out.println("Connected!");
                ws.textMessageHandler(data -> {
                    JsonObject obj = new JsonObject(data);
                    String evType = obj.getString("event");
                    if (evType.equals("subscription-started")) {
                        JsonArray ebikes = obj.getJsonArray("ebikes");
                        p.complete(ebikes);
                    } else if (evType.equals("ride-update")) {
                        String rideID = obj.getString("rideId");
                        String userID = obj.getString("userId");
                        String ebikeID = obj.getString("ebikeId");
                        
                        observer.rideStarted(rideID, userID, ebikeID);
                    } else if (evType.equals("ride-end")) {
                        String rideID = obj.getString("rideId");
                        
                        observer.rideEnded(rideID);
                    }
                });
            } else {
                p.fail(res.cause());
            }
		});
		
		return p.future();
    }

}