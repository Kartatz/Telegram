package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import java.util.HashMap;
import java.util.Map;

@SuppressLint("MissingPermission")
public class LocationManagerProvider implements ILocationServiceProvider {
    private LocationManager locationManager;
    private final Map<ILocationListener, LocationListener> listeners = new HashMap<>();

    @Override
    public void init(Context context) {
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public ILocationRequest onCreateLocationRequest() {
        return new SimpleLocationRequest();
    }

    @Override
    public void getLastLocation(Consumer<Location> callback) {
        Location best = null;
        for (String provider : new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
            try {
                Location location = locationManager.getLastKnownLocation(provider);
                if (location != null && (best == null || location.getTime() > best.getTime())) {
                    best = location;
                }
            } catch (Exception ignore) {
            }
        }
        callback.accept(best);
    }

    @Override
    public void requestLocationUpdates(ILocationRequest request, ILocationListener locationListener) {
        SimpleLocationRequest simpleRequest = (SimpleLocationRequest) request;
        long interval = simpleRequest.interval > 0 ? simpleRequest.interval / 1000 : 1;
        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(@Nullable Location location) {
                if (location != null) {
                    locationListener.onLocationChanged(location);
                }
            }

            @Override
            public void onProviderDisabled(String provider) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }
        };
        synchronized (listeners) {
            listeners.put(locationListener, listener);
        }
        for (String provider : getProviders(simpleRequest)) {
            try {
                locationManager.requestLocationUpdates(provider, interval * 1000, 0, listener, Looper.getMainLooper());
            } catch (Exception ignore) {
            }
        }
    }

    @Override
    public void removeLocationUpdates(ILocationListener locationListener) {
        LocationListener listener;
        synchronized (listeners) {
            listener = listeners.remove(locationListener);
        }
        if (listener != null) {
            try {
                locationManager.removeUpdates(listener);
            } catch (Exception ignore) {
            }
        }
    }

    @Override
    public void checkLocationSettings(ILocationRequest request, Consumer<Integer> callback) {
        SimpleLocationRequest simpleRequest = (SimpleLocationRequest) request;
        boolean enabled = false;
        for (String provider : getProviders(simpleRequest)) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    enabled = true;
                }
            } catch (Exception ignore) {
            }
        }
        if (enabled) {
            callback.accept(STATUS_SUCCESS);
        } else {
            callback.accept(STATUS_SETTINGS_CHANGE_UNAVAILABLE);
        }
    }

    @Override
    public IMapApiClient onCreateLocationServicesAPI(Context context, IAPIConnectionCallbacks connectionCallbacks, IAPIOnConnectionFailedListener failedListener) {
        return new IMapApiClient() {
            @Override
            public void connect() {
                AndroidUtilities.runOnUIThread(() -> connectionCallbacks.onConnected(null));
            }

            @Override
            public void disconnect() {
            }
        };
    }

    @Override
    public boolean checkServices() {
        return true;
    }

    private String[] getProviders(SimpleLocationRequest request) {
        switch (request.priority) {
            case PRIORITY_HIGH_ACCURACY:
                return new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER};
            case PRIORITY_BALANCED_POWER_ACCURACY:
            case PRIORITY_LOW_POWER:
                return new String[] {LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER};
            default:
                return new String[] {LocationManager.PASSIVE_PROVIDER};
        }
    }

    public final static class SimpleLocationRequest implements ILocationRequest {
        private int priority = PRIORITY_HIGH_ACCURACY;
        private long interval;

        @Override
        public void setPriority(int priority) {
            this.priority = priority;
        }

        @Override
        public void setInterval(long interval) {
            this.interval = interval;
        }

        @Override
        public void setFastestInterval(long interval) {
        }
    }
}
