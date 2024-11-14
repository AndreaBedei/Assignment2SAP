package sap.ass2.rides.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import sap.ass2.rides.application.EbikesManagerRemoteAPI;
import sap.ass2.rides.application.UsersManagerRemoteAPI;
import sap.ass2.rides.domain.Ebike;
import sap.ass2.rides.domain.EbikeState;
import sap.ass2.rides.domain.RideEventObserver;
import sap.ass2.rides.domain.User;

public class RidesExecutionVerticle extends AbstractVerticle {
    private static String RIDES_STEP = "rides-step";
    private static String RIDE_STOP = "ride-stop";

    private RideEventObserver observer;
    private EbikesManagerRemoteAPI ebikesManager;
    private UsersManagerRemoteAPI usersManager;

    // The key is rideID for both.
    Map<String, MessageConsumer<String>> rides;
    Map<String, TimeVariables> timeVars;
    Map<String, RideStopReason> stopRideRequested;

    private MessageConsumer<Object> loopConsumer;

    public RidesExecutionVerticle(RideEventObserver observer, UsersManagerRemoteAPI usersManager, EbikesManagerRemoteAPI ebikesManager) {
        this.observer = observer;
        this.usersManager = usersManager;
        this.ebikesManager = ebikesManager;

        this.rides = new ConcurrentHashMap<>();
        this.timeVars = new ConcurrentHashMap<>();
        this.stopRideRequested = new ConcurrentHashMap<>();

        this.loopConsumer = null;
    }

    public void launch() {
		Vertx.vertx().deployVerticle(this);
	}

    public void start() {
        // Consumer for events called from outside, specifically for stopping rides.
        this.vertx.eventBus().<Pair<String, RideStopReason>>consumer(RIDE_STOP, pair -> {
            this.stopRideRequested.put(pair.body().first(), pair.body().second());
        });
    
        var eventBus = this.vertx.eventBus();

        this.loopConsumer = eventBus.consumer(RIDES_STEP);
        this.loopConsumer.handler(msg -> {
            this.vertx.executeBlocking(() -> {
                Thread.sleep(500);
                return null;
            }).onComplete(h -> {
                if (!rides.isEmpty()) {
                    eventBus.publish(RIDES_STEP, null);
                } else {
                    this.loopConsumer.unregister();         // The loop stops when there are no active rides.
                }
            });
        });
    }

    private static User jsonObjToUser(JsonObject obj) {
        return new User(obj.getString("userId"), obj.getInteger("credit"));
    }

    private static Ebike jsonObjToEbike(JsonObject obj) {
        return new Ebike(obj.getString("ebikeId"), EbikeState.valueOf(obj.getString("state")), obj.getDouble("x"),
                obj.getDouble("y"), obj.getDouble("dirX"), obj.getDouble("dirY"), obj.getDouble("speed"),
                obj.getInteger("batteryLevel"));
    }

    /**
     * Starts the cycle of step events when a new ride is added (if there was none before).
     */
    private void beginLoopOfEventsIfNecessary() {
        if (this.loopConsumer.isRegistered()) {
            return;
        }
        this.loopConsumer.resume();
        this.vertx.eventBus().publish(RIDES_STEP, null);
    }

    private List<Double> rotate(double x, double y, double degrees) {
        var rad = degrees * Math.PI / 180; // Converts degrees to radians.
        var cs = Math.cos(rad); // Calculates the cosine of the angle.
        var sn = Math.sin(rad); // Calculates the sine of the angle.
        var xN = x * cs - y * sn; // New x component after rotation.
        var yN = x * sn + y * cs; // New y component after rotation.
        var module = (double) Math.sqrt(xN * xN + yN * yN);
        return List.of(xN / module, yN / module); // Returns the rotated and normalized vector.
    }

    public void launchRide(String rideID, String userID, String ebikeID) {
        this.timeVars.put(rideID, TimeVariables.now());

        var eventBus = this.vertx.eventBus();

        MessageConsumer<String> consumer = eventBus.<String>consumer(RIDES_STEP);
        consumer.handler(msg -> {
            var ebikeFuture = this.ebikesManager.getBikeByID(ebikeID);
            var userFuture = this.usersManager.getUserByID(userID);

            Future.all(ebikeFuture, userFuture)
                .onSuccess(cf -> {
                    var stopRequestedOpt = Optional.of(this.stopRideRequested.get(rideID));
                    if (stopRequestedOpt.isPresent()) {
                        this.stopRideRequested.remove(rideID);
                        this.rides.remove(rideID);
                        this.observer.rideEnded(rideID, stopRequestedOpt.get().reason);
                        
                        consumer.unregister();
                        return;
                    }

                    List<JsonObject> results = cf.list();
                    Ebike ebike = jsonObjToEbike(results.get(0));
                    User user = jsonObjToUser(results.get(1));

                    TimeVariables timeVar = this.timeVars.get(rideID);

                    if (ebike.batteryLevel() > 0 && user.credit() > 0) {
                        // Get current location and direction of the bike.
                        var oldX = ebike.locX();
                        var oldY = ebike.locY();
                        var dirX = ebike.dirX();
                        var dirY = ebike.dirY();
                        var s = 1; // Speed of the bike

                        // Calculate new location based on direction.
                        var newX = oldX + dirX * s;
                        var newY = oldY + dirY * s;

                        var newDirX = dirX;
                        var newDirY = dirY;

                        var newBatteryLevel = ebike.batteryLevel(); // Battery level.

                        // Handle boundary conditions for bike's location.
                        if (newX > 200 || newX < -200) {
                            newDirX = -newDirX;
                            newX = newX > 200 ? 200 : -200;
                        }
                        if (newY > 200 || newY < -200) {
                            newDirY = -newDirY;
                            newY = newY > 200 ? 200 : -200;
                        }

                        // Change direction randomly every 500 milliseconds.
                        var elapsedTimeSinceLastChangeDir = System.currentTimeMillis() - timeVar.lastTimeChangedDir();
                        if (elapsedTimeSinceLastChangeDir > 500) {
                            double angle = Math.random() * 60 - 30; // Random angle between -30 and 30 degrees.
                            var newDir = rotate(dirX, dirY, angle);
                            newDirX = newDir.get(0);
                            newDirY = newDir.get(1);

                            timeVar = timeVar.updateLastTimeChangedDir(System.currentTimeMillis());
                        }

                        // Update user credits every 2000 milliseconds.
                        var elapsedTimeSinceLastDecredit = System.currentTimeMillis() - timeVar.lastTimeDecreasedCredit();
                        if (elapsedTimeSinceLastDecredit > 2000) {
                            usersManager.decreaseCredit(userID, 1);

                            timeVar = timeVar.updateLastTimeDecreasedCredit(System.currentTimeMillis());
                        }

                        // Decrease battery level every 1500 milliseconds.
                        var elapsedTimeSinceLastBatteryDecreased = System.currentTimeMillis() - timeVar.lastTimeBatteryDecreased();
                        if (elapsedTimeSinceLastBatteryDecreased > 1500) {
                            newBatteryLevel--;

                            timeVar = timeVar.updateLastTimeBatteryDecreased(System.currentTimeMillis());
                        }

                        // Notify observer about the current ride status.
                        this.observer.rideStep(rideID, newX, newY, newDirX, newDirY, 1, newBatteryLevel);

                        this.ebikesManager.updateBike(ebikeID, Optional.of(ebike.state()),
                            Optional.of(newX), Optional.of(newY),
                            Optional.of(newDirX), Optional.of(newDirY),
                            Optional.of(1.0), Optional.of(newBatteryLevel));

                        this.timeVars.put(rideID, timeVar);
                    } else {
                        this.ebikesManager.updateBike(ebikeID, Optional.of(ebike.batteryLevel() > 0 ? EbikeState.AVAILABLE : EbikeState.MAINTENANCE),
                            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

                        this.rides.remove(rideID);
                        this.observer.rideEnded(rideID, (ebike.batteryLevel() > 0 ? RideStopReason.USER_RAN_OUT_OF_CREDIT : RideStopReason.EBIKE_RAN_OUT_OF_BATTERY).reason);
                        consumer.unregister();
                    }
                })
                .onFailure(ex -> {
                    this.observer.rideEnded(rideID, RideStopReason.SERVICE_ERROR.reason);
                    consumer.unregister();
                });
        });

        this.rides.put(rideID, consumer);
        this.beginLoopOfEventsIfNecessary();;
    }

    public void stopRide(String rideID) {
        if (this.rides.containsKey(rideID)) {
            this.vertx.eventBus().publish(RIDE_STOP, Pair.of(rideID, RideStopReason.RIDE_STOPPED));
        }
    }
}