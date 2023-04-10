package com.example.mytaxi;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;


import java.sql.Driver;
import java.util.HashMap;
import java.util.List;


public class CustomersMapActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener
{

    private GoogleMap mMap;
    GoogleApiClient googleApiClient;
    Location lastLocation;
    LocationRequest locationRequest;
    Marker driverMarker, PickUpMarker;
    GeoQuery geoQuery;

    private Button customerLogoutButton;
    private Button callTaxiButton;
    private String customerID;
    private LatLng CustomerPosition;
    private int radius = 1;
    private Boolean driverFound = false, requestType;
    private String driverFoundID;

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private DatabaseReference CustomerDatabaseRef;
    private DatabaseReference DriversAvailableRef;
    private DatabaseReference DriversRef;
    private DatabaseReference DriversLocationRef;

    private ValueEventListener DriverLocationRefListener;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customers_map);

        customerLogoutButton = (Button) findViewById(R.id.cuslomer_logout_button);
        callTaxiButton = (Button) findViewById(R.id.cuslomer_order_button);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        customerID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        CustomerDatabaseRef = FirebaseDatabase.getInstance().getReference().child("Customers Requests");
        DriversAvailableRef = FirebaseDatabase.getInstance().getReference().child("Driver Available");
        DriversLocationRef = FirebaseDatabase.getInstance().getReference().child("Driver Working");

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        customerLogoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) 
            {
                mAuth.signOut();
                LogoutCustomer();
    
            }
        });

        callTaxiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {

                if (requestType)
                {
                    requestType = false;
                    geoQuery.removeAllListeners();
                    DriversLocationRef.removeEventListener(DriverLocationRefListener);

                    if (driverFound != null)
                    {
                        DriversRef = FirebaseDatabase.getInstance().getReference()
                                .child("Users").child("Drivers").child(driverFoundID).child("CustomerRideID");

                        DriversRef.removeValue();
                        driverFoundID = null;
                    }

                    driverFound = false;
                    radius = 1;

                    GeoFire geofire = new GeoFire(CustomerDatabaseRef);
                    geofire.removeLocation(customerID);

                    if(PickUpMarker != null)
                    {
                        PickUpMarker.remove();
                    }
                    if(driverMarker != null)
                    {
                        driverMarker.remove();
                    }

                    callTaxiButton.setText("Вызвать такси");

                }
                else
                {
                    requestType = true;

                    GeoFire geofire = new GeoFire(CustomerDatabaseRef);
                    geofire.setLocation(customerID, new GeoLocation(lastLocation.getLatitude(), lastLocation.getLongitude()));

                    CustomerPosition = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
                    PickUpMarker = mMap.addMarker(new MarkerOptions().position(CustomerPosition).title("Я здесь").icon(BitmapDescriptorFactory.fromResource(R.drawable.user)));

                    callTaxiButton.setText("Поиск водителя...");
                    getNearbyDrivers();
                }

            }
        });

            }




    @Override
    public void onMapReady(GoogleMap googleMap)
    {
        mMap = googleMap;

        buildGoogleApiClient();
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mMap.setMyLocationEnabled(true);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle)
    {
        locationRequest = new com.google.android.gms.location.LocationRequest();
        locationRequest.setInterval(100000);
        locationRequest.setFastestInterval(100000);
        locationRequest.setPriority(locationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(@NonNull Location location)
    {
        lastLocation = location;

        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(12));
    }
    protected synchronized void buildGoogleApiClient()
    {
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        googleApiClient.connect();
    }

    @Override
    protected void onStop() {

        super.onStop();
    }

    private void LogoutCustomer()
    {
        Intent welcomeIntent = new Intent(CustomersMapActivity.this, WelcomeActivity.class);
        startActivity(welcomeIntent);
        finish();
    }

    private void getNearbyDrivers()
    {
        GeoFire geoFire = new GeoFire(DriversAvailableRef);
        GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(CustomerPosition.latitude, CustomerPosition.longitude), radius);
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if (!driverFound && requestType)
                {
                    driverFound = true;
                    driverFoundID = key;

                    DriversRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID);
                    HashMap driverMap = new HashMap();
                    driverMap.put("CustomerRideID", customerID);

                    DriversRef.updateChildren(driverMap);

                    GetDriverLocation();
                }
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady()
            {
                if (!driverFound)
                {
                    radius = radius + 1;
                    getNearbyDrivers();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    private void GetDriverLocation()
    {
        DriverLocationRefListener= DriversLocationRef.child(driverFoundID).child("l").
                addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot)
                    {
                        if(dataSnapshot.exists() && requestType)
                        {
                            List<Object> driverLocationMap = (List<Object>) dataSnapshot.getValue();
                            double LocationLat = 0;
                            double LocationLng = 0;
                            callTaxiButton.setText("Водитель найден!");

                            if (driverLocationMap.get(0) != null)
                            {
                                LocationLat = Double.parseDouble(driverLocationMap.get(0).toString());
                            }
                            if (driverLocationMap.get(1) != null)
                            {
                                LocationLng = Double.parseDouble(driverLocationMap.get(1).toString());
                            }

                            LatLng DriverLatLng = new LatLng(LocationLat, LocationLng);

                            if (driverMarker != null)
                            {
                                driverMarker.remove();
                            }

                            Location location1 = new Location("");
                            location1.setLatitude(CustomerPosition.latitude);
                            location1.setLongitude(CustomerPosition.longitude);


                            Location location2 = new Location("");
                            location2.setLatitude(DriverLatLng.latitude);
                            location2.setLongitude(DriverLatLng.longitude);

                            float Distance = location1.distanceTo(location2);
                            if (Distance>100)
                            {
                                callTaxiButton.setText("Ваше такси подъезжает");
                            }
                            else {
                                callTaxiButton.setText("Расстрояние до такси" + String.valueOf(Distance));
                            }

                            driverMarker = mMap.addMarker(new MarkerOptions().position(DriverLatLng).title("Ваше такси тут").icon(BitmapDescriptorFactory.fromResource(R.drawable.car)));

                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }
                });
    }

}