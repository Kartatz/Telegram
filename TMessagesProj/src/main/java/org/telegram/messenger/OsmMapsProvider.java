package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.DashPathEffect;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.util.Consumer;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer;
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;

public class OsmMapsProvider implements IMapsProvider {

    @Override
    public void initializeMaps(Context context) {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context));
        Configuration.getInstance().setUserAgentValue(context.getPackageName());
    }

    @Override
    public IMapView onCreateMapView(Context context) {
        return new OsmMapView(context);
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLng(LatLng latLng) {
        return new OsmCameraUpdate(latLng, -1);
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLngZoom(LatLng latLng, float zoom) {
        return new OsmCameraUpdate(latLng, zoom);
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLngBounds(ILatLngBounds bounds, int padding) {
        return new OsmCameraBoundsUpdate(((OsmLatLngBounds) bounds).bounds, padding);
    }

    @Override
    public ILatLngBoundsBuilder onCreateLatLngBoundsBuilder() {
        return new OsmLatLngBoundsBuilder();
    }

    @Override
    public IMapStyleOptions loadRawResourceStyle(Context context, int resId) {
        return new OsmMapStyleOptions();
    }

    @Override
    public String getMapsAppPackageName() {
        String appId = ApplicationLoader.getApplicationId();
        if (appId == null && ApplicationLoader.applicationContext != null) {
            appId = ApplicationLoader.applicationContext.getPackageName();
        }
        return appId;
    }

    @Override
    public int getInstallMapsString() {
        return 0;
    }

    @Override
    public IMarkerOptions onCreateMarkerOptions() {
        return new OsmMarkerOptions();
    }

    @Override
    public ICircleOptions onCreateCircleOptions() {
        return new OsmCircleOptions();
    }

    public final static class OsmMap implements IMap {
        private final MapView mapView;
        private final OsmMyLocationProvider locationProvider;
        private MyLocationNewOverlay myLocationOverlay;
        private Runnable cameraMoveListener;
        private Runnable cameraIdleListener;
        private boolean cameraIdlePosted;
        private OnMarkerClickListener markerClickListener;

        private OsmMap(MapView mapView) {
            this.mapView = mapView;
            this.mapView.setMultiTouchControls(true);
            this.mapView.setTilesScaledToDpi(true);
            this.locationProvider = new OsmMyLocationProvider(mapView.getContext());
            this.mapView.addMapListener(new MapListener() {
                @Override
                public boolean onScroll(ScrollEvent event) {
                    notifyCameraMoved();
                    return false;
                }

                @Override
                public boolean onZoom(ZoomEvent event) {
                    notifyCameraMoved();
                    return false;
                }
            });
        }

        private void notifyCameraMoved() {
            if (cameraMoveListener != null) {
                cameraMoveListener.run();
            }
            if (cameraIdleListener != null) {
                if (cameraIdlePosted) {
                    return;
                }
                cameraIdlePosted = true;
                mapView.postDelayed(() -> {
                    cameraIdlePosted = false;
                    if (cameraIdleListener != null) {
                        cameraIdleListener.run();
                    }
                }, 250);
            }
        }

        public void onGestureStarted() {
            if (cameraMoveStartedListener != null) {
                cameraMoveStartedListener.onCameraMoveStarted(OnCameraMoveStartedListener.REASON_GESTURE);
            }
        }

        private OnCameraMoveStartedListener cameraMoveStartedListener;

        @Override
        public void setOnCameraMoveStartedListener(OnCameraMoveStartedListener onCameraMoveStartedListener) {
            this.cameraMoveStartedListener = onCameraMoveStartedListener;
        }

        @Override
        public void setMapType(int mapType) {
            switch (mapType) {
                case MAP_TYPE_SATELLITE:
                case MAP_TYPE_HYBRID:
                    mapView.setTileSource(TileSourceFactory.USGS_SAT);
                    break;
                default:
                    mapView.setTileSource(TileSourceFactory.MAPNIK);
                    break;
            }
        }

        @Override
        public float getMaxZoomLevel() {
            return (float) mapView.getMaxZoomLevel();
        }

        @Override
        public float getMinZoomLevel() {
            return (float) mapView.getMinZoomLevel();
        }

        @SuppressLint("MissingPermission")
        @Override
        public void setMyLocationEnabled(boolean enabled) {
            if (enabled) {
                if (myLocationOverlay == null) {
                    myLocationOverlay = new MyLocationNewOverlay(locationProvider, mapView);
                    mapView.getOverlays().add(myLocationOverlay);
                }
                myLocationOverlay.enableMyLocation();
                myLocationOverlay.enableFollowLocation();
            } else if (myLocationOverlay != null) {
                myLocationOverlay.disableFollowLocation();
                myLocationOverlay.disableMyLocation();
                mapView.getOverlays().remove(myLocationOverlay);
                myLocationOverlay = null;
            }
        }

        @Override
        public IUISettings getUiSettings() {
            return new OsmUISettings(mapView);
        }

        @Override
        public void setOnCameraIdleListener(Runnable callback) {
            cameraIdleListener = callback;
        }

        @Override
        public CameraPosition getCameraPosition() {
            GeoPoint center = (GeoPoint) mapView.getMapCenter();
            return new CameraPosition(new LatLng(center.getLatitude(), center.getLongitude()), (float) mapView.getZoomLevelDouble());
        }

        @Override
        public void setOnMapLoadedCallback(Runnable callback) {
            mapView.addOnFirstLayoutListener((v, left, top, right, bottom) -> callback.run());
        }

        @Override
        public IProjection getProjection() {
            return latLng -> mapView.getProjection().toPixels(new GeoPoint(latLng.latitude, latLng.longitude), null);
        }

        @Override
        public void setPadding(int left, int top, int right, int bottom) {
            mapView.setPadding(left, top, right, bottom);
        }

        @Override
        public void setMapStyle(IMapStyleOptions style) {
        }

        @Override
        public IMarker addMarker(IMarkerOptions markerOptions) {
            OsmMarkerOptions options = (OsmMarkerOptions) markerOptions;
            Marker marker = new Marker(mapView);
            marker.setPosition(new GeoPoint(options.position.latitude, options.position.longitude));
            if (options.iconBitmap != null) {
                marker.setIcon(new BitmapDrawable(mapView.getResources(), options.iconBitmap));
            } else if (options.iconResId != 0) {
                Drawable drawable = mapView.getContext().getResources().getDrawable(options.iconResId, null);
                if (drawable != null) {
                    marker.setIcon(drawable);
                }
            }
            if (options.anchorSet) {
                marker.setAnchor(options.anchorLat, options.anchorLng);
            }
            marker.setTitle(options.title);
            marker.setSnippet(options.snippet);
            marker.setFlat(options.flat);
            marker.setOnMarkerClickListener((m, view) -> markerClickListener == null || markerClickListener.onClick(new OsmMarker(mapView, m)));
            mapView.getOverlays().add(marker);
            return new OsmMarker(mapView, marker);
        }

        @Override
        public void setOnMyLocationChangeListener(Consumer<Location> callback) {
            locationProvider.callback = callback;
        }

        @Override
        public void setOnMarkerClickListener(OnMarkerClickListener markerClickListener) {
            this.markerClickListener = markerClickListener;
        }

        @Override
        public void setOnCameraMoveListener(Runnable callback) {
            cameraMoveListener = callback;
        }

        @Override
        public ICircle addCircle(ICircleOptions circleOptions) {
            OsmCircleOptions options = (OsmCircleOptions) circleOptions;
            Polygon polygon = new Polygon(mapView);
            polygon.getOutlinePaint().setColor(options.strokeColor);
            polygon.getOutlinePaint().setStrokeWidth(options.strokeWidth);
            polygon.getFillPaint().setColor(options.fillColor);
            if (options.patternItems != null && !options.patternItems.isEmpty()) {
                List<Float> intervals = new ArrayList<>();
                boolean dash = false;
                for (IMapsProvider.PatternItem item : options.patternItems) {
                    if (item instanceof IMapsProvider.PatternItem.Dash) {
                        intervals.add((float) ((IMapsProvider.PatternItem.Dash) item).length);
                        dash = true;
                    } else if (item instanceof IMapsProvider.PatternItem.Gap) {
                        intervals.add((float) ((IMapsProvider.PatternItem.Gap) item).length);
                    }
                }
                if (dash && !intervals.isEmpty()) {
                    float[] array = new float[intervals.size()];
                    for (int i = 0; i < intervals.size(); i++) {
                        array[i] = intervals.get(i);
                    }
                    polygon.getOutlinePaint().setPathEffect(new DashPathEffect(array, 0));
                }
            }
            polygon.setPoints(circlePoints(options.center, options.radius));
            mapView.getOverlays().add(polygon);
            return new OsmCircle(mapView, polygon);
        }

        @Override
        public void animateCamera(ICameraUpdate update) {
            applyUpdate(update, true, 0, null);
        }

        @Override
        public void animateCamera(ICameraUpdate update, ICancelableCallback callback) {
            applyUpdate(update, true, 400, callback);
        }

        @Override
        public void animateCamera(ICameraUpdate update, int duration, ICancelableCallback callback) {
            applyUpdate(update, true, duration, callback);
        }

        @Override
        public void moveCamera(ICameraUpdate update) {
            applyUpdate(update, false, 0, null);
        }

        private void applyUpdate(ICameraUpdate update, boolean animate, long duration, ICancelableCallback callback) {
            if (update instanceof OsmCameraUpdate) {
                OsmCameraUpdate cameraUpdate = (OsmCameraUpdate) update;
                GeoPoint geoPoint = new GeoPoint(cameraUpdate.latLng.latitude, cameraUpdate.latLng.longitude);
                if (animate) {
                    if (cameraUpdate.zoom < 0) {
                        mapView.getController().animateTo(geoPoint);
                    } else {
                        mapView.getController().animateTo(geoPoint, (double) cameraUpdate.zoom, null);
                    }
                } else {
                    mapView.getController().setCenter(geoPoint);
                    if (cameraUpdate.zoom >= 0) {
                        mapView.getController().setZoom(cameraUpdate.zoom);
                    }
                }
            } else if (update instanceof OsmCameraBoundsUpdate) {
                OsmCameraBoundsUpdate boundsUpdate = (OsmCameraBoundsUpdate) update;
                mapView.zoomToBoundingBox(boundsUpdate.bounds, animate, boundsUpdate.padding);
            }
            if (callback != null) {
                if (duration <= 0) {
                    duration = 400;
                }
                mapView.postDelayed(callback::onFinish, duration);
            }
        }
    }

    private static List<GeoPoint> circlePoints(LatLng center, double radius) {
        List<GeoPoint> points = new ArrayList<>();
        if (center == null || radius <= 0) {
            return points;
        }
        final int segments = 100;
        double latRadiansToMeters = 111132.92;
        for (int i = 0; i < segments; i++) {
            double angle = 2 * Math.PI * i / segments;
            double lat = center.latitude + (radius / latRadiansToMeters) * Math.sin(angle);
            double lng = center.longitude + (radius / (latRadiansToMeters * Math.cos(Math.toRadians(center.latitude)))) * Math.cos(angle);
            points.add(new GeoPoint(lat, lng));
        }
        return points;
    }

    public final static class OsmMyLocationProvider implements IMyLocationProvider, IMyLocationConsumer {
        private final GpsMyLocationProvider gps;
        private IMyLocationConsumer consumer;
        public Consumer<Location> callback;

        private OsmMyLocationProvider(Context context) {
            gps = new GpsMyLocationProvider(context);
        }

        @Override
        public boolean startLocationProvider(IMyLocationConsumer myLocationConsumer) {
            consumer = myLocationConsumer;
            return gps.startLocationProvider(this);
        }

        @Override
        public void stopLocationProvider() {
            gps.stopLocationProvider();
        }

        @Override
        public Location getLastKnownLocation() {
            return gps.getLastKnownLocation();
        }

        @Override
        public void destroy() {
            gps.destroy();
        }

        @Override
        public void onLocationChanged(Location location, IMyLocationProvider source) {
            if (consumer != null) {
                consumer.onLocationChanged(location, source);
            }
            if (callback != null) {
                callback.accept(location);
            }
        }
    }

    public final static class OsmMarker implements IMarker {
        private final MapView mapView;
        private final Marker marker;

        private OsmMarker(MapView mapView, Marker marker) {
            this.mapView = mapView;
            this.marker = marker;
        }

        @Override
        public Object getTag() {
            return marker.getRelatedObject();
        }

        @Override
        public void setTag(Object tag) {
            marker.setRelatedObject(tag);
        }

        @Override
        public LatLng getPosition() {
            GeoPoint position = marker.getPosition();
            return new LatLng(position.getLatitude(), position.getLongitude());
        }

        @Override
        public void setPosition(LatLng latLng) {
            marker.setPosition(new GeoPoint(latLng.latitude, latLng.longitude));
        }

        @Override
        public void setRotation(int rotation) {
            marker.setRotation(rotation);
        }

        @Override
        public void setIcon(Bitmap bitmap) {
            marker.setIcon(new BitmapDrawable(mapView.getResources(), bitmap));
        }

        @Override
        public void setIcon(int resId) {
            Drawable drawable = mapView.getContext().getResources().getDrawable(resId, null);
            if (drawable != null) {
                marker.setIcon(drawable);
            }
        }

        @Override
        public void remove() {
            mapView.getOverlays().remove(marker);
        }
    }

    public final static class OsmCircle implements ICircle {
        private final MapView mapView;
        private final Polygon polygon;

        private OsmCircle(MapView mapView, Polygon polygon) {
            this.mapView = mapView;
            this.polygon = polygon;
        }

        @Override
        public void setStrokeColor(int color) {
            polygon.getOutlinePaint().setColor(color);
        }

        @Override
        public void setFillColor(int color) {
            polygon.getFillPaint().setColor(color);
        }

        @Override
        public void setRadius(double radius) {
            LatLng center = getCenter();
            polygon.setPoints(circlePoints(center, radius));
        }

        @Override
        public double getRadius() {
            return polygon.getPoints().size() > 0 ? Math.abs(polygon.getPoints().get(0).getLatitude() - ((GeoPoint) mapView.getMapCenter()).getLatitude()) * 111132.92 : 0;
        }

        @Override
        public void setCenter(LatLng latLng) {
            double radius = getRadius();
            polygon.setPoints(circlePoints(latLng, radius));
        }

        @Override
        public void remove() {
            mapView.getOverlays().remove(polygon);
        }

        private LatLng getCenter() {
            List<GeoPoint> points = polygon.getPoints();
            double minLat = 90, maxLat = -90, minLng = 180, maxLng = -180;
            for (GeoPoint point : points) {
                minLat = Math.min(minLat, point.getLatitude());
                maxLat = Math.max(maxLat, point.getLatitude());
                minLng = Math.min(minLng, point.getLongitude());
                maxLng = Math.max(maxLng, point.getLongitude());
            }
            return new LatLng((minLat + maxLat) / 2, (minLng + maxLng) / 2);
        }
    }

    public final static class OsmMarkerOptions implements IMarkerOptions {
        private LatLng position;
        private Bitmap iconBitmap;
        private int iconResId;
        private boolean anchorSet;
        private float anchorLat, anchorLng;
        private String title, snippet;
        private boolean flat;

        @Override
        public IMarkerOptions position(LatLng latLng) {
            position = latLng;
            return this;
        }

        @Override
        public IMarkerOptions icon(Bitmap bitmap) {
            iconBitmap = bitmap;
            return this;
        }

        @Override
        public IMarkerOptions icon(int resId) {
            iconResId = resId;
            return this;
        }

        @Override
        public IMarkerOptions anchor(float lat, float lng) {
            anchorSet = true;
            anchorLat = lat;
            anchorLng = lng;
            return this;
        }

        @Override
        public IMarkerOptions title(String title) {
            this.title = title;
            return this;
        }

        @Override
        public IMarkerOptions snippet(String snippet) {
            this.snippet = snippet;
            return this;
        }

        @Override
        public IMarkerOptions flat(boolean flat) {
            this.flat = flat;
            return this;
        }
    }

    public final static class OsmCircleOptions implements ICircleOptions {
        private LatLng center;
        private double radius;
        private int strokeColor, fillColor;
        private int strokeWidth;
        private List<IMapsProvider.PatternItem> patternItems;

        @Override
        public ICircleOptions center(LatLng latLng) {
            center = latLng;
            return this;
        }

        @Override
        public ICircleOptions radius(double radius) {
            this.radius = radius;
            return this;
        }

        @Override
        public ICircleOptions strokeColor(int color) {
            strokeColor = color;
            return this;
        }

        @Override
        public ICircleOptions fillColor(int color) {
            fillColor = color;
            return this;
        }

        @Override
        public ICircleOptions strokePattern(List<IMapsProvider.PatternItem> patternItems) {
            this.patternItems = patternItems;
            return this;
        }

        @Override
        public ICircleOptions strokeWidth(int width) {
            strokeWidth = width;
            return this;
        }
    }

    public final static class OsmLatLngBoundsBuilder implements ILatLngBoundsBuilder {
        private final List<GeoPoint> geoPoints = new ArrayList<>();

        @Override
        public ILatLngBoundsBuilder include(LatLng latLng) {
            geoPoints.add(new GeoPoint(latLng.latitude, latLng.longitude));
            return this;
        }

        @Override
        public ILatLngBounds build() {
            return new OsmLatLngBounds(BoundingBox.fromGeoPoints(geoPoints));
        }
    }

    public final static class OsmLatLngBounds implements ILatLngBounds {
        private final BoundingBox bounds;

        private OsmLatLngBounds(BoundingBox bounds) {
            this.bounds = bounds;
        }

        @Override
        public LatLng getCenter() {
            GeoPoint center = bounds.getCenter();
            return new LatLng(center.getLatitude(), center.getLongitude());
        }
    }

    public final static class OsmMapView implements IMapView {
        private final MapView mapView;
        private final List<Runnable> mapReadyCallbacks = new ArrayList<>();
        private ITouchInterceptor dispatchInterceptor;
        private ITouchInterceptor interceptInterceptor;
        private Runnable onLayoutListener;
        private OsmMap map;

        private OsmMapView(Context context) {
            mapView = new MapView(context) {
                @Override
                public boolean dispatchTouchEvent(MotionEvent ev) {
                    if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && map != null) {
                        map.onGestureStarted();
                    }
                    if (dispatchInterceptor != null) {
                        return dispatchInterceptor.onInterceptTouchEvent(ev, super::dispatchTouchEvent);
                    }
                    return super.dispatchTouchEvent(ev);
                }

                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
                    if (interceptInterceptor != null) {
                        return interceptInterceptor.onInterceptTouchEvent(ev, super::onInterceptTouchEvent);
                    }
                    return super.onInterceptTouchEvent(ev);
                }

                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    super.onLayout(changed, left, top, right, bottom);
                    if (onLayoutListener != null) {
                        onLayoutListener.run();
                    }
                    if (!mapReadyCallbacks.isEmpty() && getWidth() > 0 && getHeight() > 0) {
                        List<Runnable> callbacks = new ArrayList<>(mapReadyCallbacks);
                        mapReadyCallbacks.clear();
                        for (Runnable callback : callbacks) {
                            callback.run();
                        }
                    }
                }
            };
        }

        @Override
        public View getView() {
            return mapView;
        }

        @Override
        public void getMapAsync(Consumer<IMap> callback) {
            if (map == null) {
                map = new OsmMap(mapView);
            }
            if (mapView.getWidth() > 0 && mapView.getHeight() > 0) {
                callback.accept(map);
                return;
            }
            mapReadyCallbacks.add(() -> callback.accept(map));
        }

        @Override
        public void onResume() {
            mapView.onResume();
        }

        @Override
        public void onPause() {
            mapView.onPause();
        }

        @Override
        public void onCreate(Bundle savedInstance) {
        }

        @Override
        public void onDestroy() {
            mapView.onDetach();
        }

        @Override
        public void onLowMemory() {
        }

        @Override
        public void setOnDispatchTouchEventInterceptor(ITouchInterceptor touchInterceptor) {
            dispatchInterceptor = touchInterceptor;
        }

        @Override
        public void setOnInterceptTouchEventInterceptor(ITouchInterceptor touchInterceptor) {
            interceptInterceptor = touchInterceptor;
        }

        @Override
        public void setOnLayoutListener(Runnable callback) {
            onLayoutListener = callback;
        }
    }

    public final static class OsmCameraUpdate implements ICameraUpdate {
        private final LatLng latLng;
        private final float zoom;

        private OsmCameraUpdate(LatLng latLng, float zoom) {
            this.latLng = latLng;
            this.zoom = zoom;
        }
    }

    public final static class OsmCameraBoundsUpdate implements ICameraUpdate {
        private final BoundingBox bounds;
        private final int padding;

        private OsmCameraBoundsUpdate(BoundingBox bounds, int padding) {
            this.bounds = bounds;
            this.padding = padding;
        }
    }

    public final static class OsmMapStyleOptions implements IMapStyleOptions {
    }

    public final static class OsmUISettings implements IUISettings {
        private final MapView mapView;

        private OsmUISettings(MapView mapView) {
            this.mapView = mapView;
        }

        @Override
        public void setZoomControlsEnabled(boolean enabled) {
            mapView.setBuiltInZoomControls(enabled);
        }

        @Override
        public void setMyLocationButtonEnabled(boolean enabled) {
        }

        @Override
        public void setCompassEnabled(boolean enabled) {
        }
    }
}
