package com.example.lljsm.mymapproject;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.ar.core.Anchor;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.PendingResult;
import com.google.maps.internal.PolylineEncoding;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.TransitMode;
import com.google.maps.model.TravelMode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.security.auth.login.LoginException;

/**
 * This activity is the main activity
 * It deals with the location data
 * and holds the map fragment and AR
 */
public class MainActivity extends BaseActivity implements
        LocationListener, OnMapReadyCallback, View.OnClickListener, Scene.OnUpdateListener{

    private Context context;

    private LocationManager locationManager = null;
    private Location location;
    private static int RESULT_LOCATION_SOURCE = 11;
    private TextView show;
    private TextView textview_result;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private String locationProvider = LocationManager.GPS_PROVIDER;
    private LocationRequest locationRequest = null;
    private LocationCallback locationCallback = null;
    private SupportMapFragment supportMapFragment = null;
    protected GoogleMap myMap = null;
    private MarkerOptions targetMarker = null;
    private Location oldLocation;
    private Location newLocation;
    private Address destinationAddress = null;
    private static boolean isInitial = true;
    private EditText streetEditText;
    private Button buttonSerch;

    private ArFragment arFragment;
    private ModelRenderable cubeRenderable;
    private ModelRenderable markerRenderable;

    private Location[] locationTest = new Location[3];
    // anchorNodeTest stores two anchorNodes that used to calculate the rotation matrix
    private AnchorNode[] anchorNodeTest = new AnchorNode[2];
    private BigDecimal[] matrix;
    private int count = 0;

    private boolean isMatrixReady = false;
    private boolean isPathReady = false;
    private boolean isRoutingPathReady = false;
    private boolean isDestinationReady = false;

    // the anchorNodes of the corresponding geo-points
    private ArrayList<AnchorNode> anchorNodes;
    // connection lines between markers
    private ArrayList<AnchorNode> connectionLines;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this;

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_arfragment);
        arFragment.getArSceneView().getScene().addOnUpdateListener(this);

        ModelRenderable.builder().setSource(this, R.raw.cube)
                .build()
                .thenAccept(renderable -> cubeRenderable = renderable)
                .exceptionally( throwable -> {
                    Toast toast = Toast.makeText(this, "Unable to load Cube", Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                    return null;
                });

        ModelRenderable.builder().setSource(this, R.raw.marker)
                .build()
                .thenAccept(renderable -> markerRenderable = renderable)
                .exceptionally( throwable -> {
                    Toast toast = Toast.makeText(this, "Unable to load Cube", Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                    return null;
                });

        show = (TextView) findViewById(R.id.tv_show);
        textview_result = (TextView) findViewById(R.id.tv_result);
        streetEditText = (EditText) findViewById(R.id.edittext_address);

        buttonSerch = (Button) findViewById(R.id.button_search);
        buttonSerch.setOnClickListener(this);
        // prepare map fragment
        supportMapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_support_map);
        supportMapFragment.getMapAsync(this);
    }

    // --------------------------
    // using new APIs
    private void getCurrentLocationWithNewAPI(){
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_FINE_LOCATION);
            return;
        }
        fusedLocationProviderClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if(null != newLocation){
                    oldLocation = newLocation;
                }
                newLocation = location;
                updateUI(location);
            }
        });
        createLocationRequest();
        createLocationCallback();
    }

    @Override
    protected void onResume() {
        super.onResume();
        count = 0;
        startLocationTracking();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(null != fusedLocationProviderClient){
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        }
    }

    private void createLocationRequest() {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void createLocationCallback() {
        if (null == locationCallback) {
            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    super.onLocationResult(locationResult);
                    if (null == locationResult) {
                        return;
                    }
                    // if location not null
                    for (Location location : locationResult.getLocations()) {
                        if(null != newLocation){
                            oldLocation = newLocation;
                        }
                        newLocation = location;
                        updateUI(location);
                    }
                }
            };
        }
    }

    private void startLocationTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if(fusedLocationProviderClient == null){
                Toast.makeText(this, "fusedLocationProviderClient null", Toast.LENGTH_SHORT).show();
            }else
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
        }

    }

    //-----------------------------
    //using old APIs
    private void getCurrentLocation() {
        // To use the the location API
        // location permission should be granted

        getGPSReady_old();

        // Once the permissions are granted and the GPS function is activated
        // we can get the location data
        // the using GPS is the most accuracy one
        // then is the network

        // because we get the GPS ready
        // we can use either GPS or network function
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_FINE_LOCATION);
                return;
            }
            locationProvider = LocationManager.GPS_PROVIDER;
        } else {
            if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ){
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_COARSE_LOCATION);
            }
            locationProvider = LocationManager.NETWORK_PROVIDER;

        }
        locationManager.requestLocationUpdates(locationProvider, 2000, 0, this );
        location = locationManager.getLastKnownLocation(locationProvider);

        if(null != location){
            if(null != newLocation){
                oldLocation = newLocation;
            }
            newLocation = location;
        }
        if(null == locationManager){
            Toast.makeText(this, "Manager Null", Toast.LENGTH_SHORT).show();
        }
        // Once the location is gained
        // update the UI
        updateUI(location);
    }

    private void updateUI(Location location){
        if(null != myMap && isInitial){
            LatLng place = new LatLng(location.getLatitude(), location.getLongitude());
            //myMap.addMarker(new MarkerOptions().position(place));
            myMap.moveCamera(CameraUpdateFactory.newLatLng(place));
            myMap.animateCamera(CameraUpdateFactory.newLatLngZoom(place, 15));
            isInitial = false;
        }
        if(count < 2 && !isMatrixReady){
            if(arFragment != null){
                if(arFragment.getArSceneView().getScene() != null && arFragment.getArSceneView().getArFrame() != null){
                    if(arFragment.getArSceneView().getArFrame().getCamera() == null){
                        return;
                    }
                    if(arFragment.getArSceneView().getArFrame().getCamera().getTrackingState() == TrackingState.TRACKING){
                        //Pose centerPose = ARHelper.getCameraPose(arFragment);
                        Pose centerPose = ARHelper.getPosefromPlanes(arFragment);
                        float[] center = null;
                        if(centerPose != null){
                            center = centerPose.getTranslation();
                            locationTest[count] = location;
                            anchorNodeTest[count] = new AnchorNode(arFragment.getArSceneView().getSession().createAnchor(Pose.makeTranslation(center)));
                            anchorNodeTest[count].setParent(arFragment.getArSceneView().getScene());
                            count++;
                            show.setText("Get " + count + " anchor");
                        }
                    }
                }
            }
        }
        if(count == 2 && !isMatrixReady){
            for (int i = 0; i < 2; i++){
                float[] center = anchorNodeTest[i].getAnchor().getPose().getTranslation();
                Log.i("220", (i+1) + ": " + locationTest[i].getLatitude() + ", " + locationTest[i].getLongitude() + " | " + center[0] + ", " + center[1] + ", " + center[2]);
            }
            matrix = ARHelper.calculatePosition(
                    new com.google.maps.model.LatLng(locationTest[0].getLatitude(), locationTest[0].getLongitude()),
                    new com.google.maps.model.LatLng(locationTest[1].getLatitude(), locationTest[1].getLongitude()),
                    anchorNodeTest[0].getAnchor().getPose(),
                    anchorNodeTest[1].getAnchor().getPose());

            isMatrixReady = true;
            show.setText("Ready");
            count++;
        }
        if(isMatrixReady && isDestinationReady){
            getDirectionPath(
                    new com.google.maps.model.LatLng(location.getLatitude(), location.getLongitude()),
                    new com.google.maps.model.LatLng(destinationAddress.getLatitude(), destinationAddress.getLongitude()));
        }
    }

    private void updateSelectLocation(Double latitude, Double longitude, String name){
        if(null != myMap){
            LatLng place = new LatLng(latitude, longitude);
            if(targetMarker != null){
                myMap.clear();
            }
            targetMarker = new MarkerOptions().position(place);
            myMap.addMarker(targetMarker);
            myMap.moveCamera(CameraUpdateFactory.newLatLngZoom(place, 15));
        }

    }

    //Using the Old version API
    private void getGPSReady_old(){
        if(null == locationManager){
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        }
        if(!(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) ||
                !(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))){
            // If GPS or network is not enable
            // let user activate those functions
            Toast.makeText(this, "Please enable network or GPS location", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivityForResult(intent, RESULT_LOCATION_SOURCE);
        }
    }

    /**
     * get the direction data from direction api
     * @param origin user's current location in LatLng
     * @param destination where user want to go in LatLng
     */
    private void getDirectionPath(com.google.maps.model.LatLng origin, com.google.maps.model.LatLng destination){
        // create the GeoApiContext to calculate the direction
        GeoApiContext geoApiContext = new GeoApiContext.Builder()
                .apiKey(getString(R.string.google_map_api_key))
                .build();
        // create DirectionApiRequest to get the data from service
        DirectionsApiRequest directionsApiRequest = new DirectionsApiRequest(geoApiContext);
        directionsApiRequest.alternatives(false); // return only one path
        directionsApiRequest.origin(origin);
        directionsApiRequest.mode(TravelMode.WALKING);
        directionsApiRequest.destination(destination).setCallback(new PendingResult.Callback<DirectionsResult>() {
            @Override
            public void onResult(DirectionsResult result) {
                Log.i("222", "result[0]: " + result.routes[0].toString());
                Log.i("222", "result[0]: " + result.routes[0].legs[0].distance);
                Log.i("222", "result[0]: " + result.routes[0].legs[0].toString());
                showDirectionPath(result);
            }

            @Override
            public void onFailure(Throwable e) {
                Log.e("222", "calculate direction error: " + e.getMessage());
            }
        });
    }

    /**
     * show the direction path in the google map
     * @param result the direction api respond on the getDirectionPath() method
     */
    private void showDirectionPath(DirectionsResult result){
        if(myMap == null) {
            Toast.makeText(this, "Google hasn't ready yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                for (DirectionsRoute route: result.routes) {
                    // 2D map, show the path
                    List<com.google.maps.model.LatLng> paths = PolylineEncoding.decode(route.overviewPolyline.getEncodedPath());
                    List<LatLng> androidPaths = new ArrayList<LatLng>();
                    for (com.google.maps.model.LatLng point : paths) {
                        // convert com.google.maps.model.LatLng to android one
                        androidPaths.add(new LatLng(point.lat, point.lng));
                    }
                    Polyline polyline = myMap.addPolyline(new PolylineOptions().addAll(androidPaths));
                    polyline.setColor(getColor(R.color.colorPrimaryDark));
                    polyline.setClickable(true);

                    // AR map, show the markers
                    ArrayList<Pose> ArPoses = LatLngs2Poses(paths);
                    int length = ArPoses.size();
                    if(anchorNodes == null){
                        anchorNodes = new ArrayList<AnchorNode>(length);
                    }
                    if(arFragment.getArSceneView().getSession() != null && arFragment.getArSceneView().getScene() != null && cubeRenderable != null){
                        for (int i = 0; i < length; i++) {
                            // the placeRenderableToPose method put the markers to the AR
                            // and the anchorNodes holds all the anchors for further use
                            anchorNodes.add(ARHelper.placeRenderableToPose(arFragment.getArSceneView().getSession(), arFragment.getArSceneView().getScene(), markerRenderable, ArPoses.get(i)));
                        }
                    }

                    // add connection line between markers
                    //connectionLines = ARHelper.getDirectionLines(getParent(), arFragment.getArSceneView().getSession(), arFragment.getArSceneView().getScene(), anchorNodes);
                    /*ARHelper.getDirectionLines(context,
                            arFragment.getArSceneView().getSession(),
                            arFragment.getArSceneView().getScene(),
                            anchorNodes);*/
                    textview_result.setText("Total nodes: " + anchorNodes.size());
                    isMatrixReady = false;
                    isDestinationReady = false;
                }

            }
        });
    }

    private ArrayList<Pose> LatLngs2Poses(List<com.google.maps.model.LatLng> paths){
        int length = paths.size();
        ArrayList<Pose> poses = new ArrayList<Pose>(length);

        Pose oldPose = anchorNodeTest[1].getAnchor().getPose(); // the end point of Pose coordinate
        com.google.maps.model.LatLng oldPosition = new com.google.maps.model.LatLng(locationTest[1].getLatitude(), locationTest[1].getLongitude()); // the end point of geo coordinate
        for (int i = 0; i < length; i++) {
            Pose newPose = ARHelper.getTranslationPose(matrix[0], matrix[1], oldPosition, paths.get(i), oldPose);
            poses.add(newPose);
            oldPose = newPose;
            oldPosition = paths.get(i);
        }

        return poses;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == RESULT_LOCATION_SOURCE){
            getGPSReady_old(); // to check if the GPS function is activate or not.
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == PERMISSION_COARSE_LOCATION || requestCode == PERMISSION_FINE_LOCATION){
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                getCurrentLocation();
            } else {
                Toast.makeText(this, "Permission Dinied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    //----------------- Divider ----------------------
    // LocationListener's methods
    @Override
    public void onLocationChanged(Location location) {
        updateUI(location);
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) { }

    @Override
    public void onProviderEnabled(String s) { }

    @Override
    public void onProviderDisabled(String s) { }

    // OnMapCallBack Listener
    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_FINE_LOCATION);
            return;
        }
        myMap = googleMap;
        myMap.setMyLocationEnabled(true);
        if(null != this.location){
            updateUI(location);
        } else {
            //Toast.makeText(this, "Cannot get location", Toast.LENGTH_SHORT).show();
            //getCurrentLocationWithNewAPI();
            getCurrentLocation();
        }
    }

    // search button click
    @Override
    public void onClick(View view) {
        buttonSerch.requestFocus();
        this.count = 0;
        String name = streetEditText.getText().toString().trim();
        Address tmp = locationTools.getAddressFromName(name);
        isMatrixReady = false;
        if(null != tmp){
            // show the marker on the map
            updateSelectLocation(tmp.getLatitude(), tmp.getLongitude(), name);

            // once the terminal location gained
            // get the direction path from direction api
            /*getDirectionPath(
                    new com.google.maps.model.LatLng(newLocation.getLatitude(), newLocation.getLongitude()),
                    new com.google.maps.model.LatLng(tmp.getLatitude(), tmp.getLongitude()));*/
            destinationAddress = tmp;
            isDestinationReady = true;
        } else {
            Toast.makeText(this, "Cannot find this address", Toast.LENGTH_SHORT).show();
            destinationAddress = null;
            isDestinationReady = false;
        }
    }
    //---------------- ARcore method, on scene update ------------------
    @Override
    public void onUpdate(FrameTime frameTime) {

    }
    //-----------------------------------------------

}
