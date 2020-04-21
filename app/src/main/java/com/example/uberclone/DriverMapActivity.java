package com.example.uberclone;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.firebase.geofire.GeoFire;
import com.google.android.gms.location.FusedLocationProviderClient;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback, RoutingListener {

    private GoogleMap mMap;
    private Button mLogout,mSettings,mRideStatus,mHistory;
    private Switch mWorkingSwitch;
    private LinearLayout mCustomerInfo;
    private ImageView mCustomerProfileImage;
    private TextView mCustomerName,mcustomerPhone,mCustomerDestination;
    Location mLastLocation;
    private int status=0;
    private String customerId="",destination;
    private Boolean isLoggingOut=false;
    private float rideDistance;
    LocationRequest mLocationRequest;
    private LatLng destinationLatLng,pickupLatLng;
    private FusedLocationProviderClient mFusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mLogout=findViewById(R.id.logout);
        mSettings=findViewById(R.id.settings);
        mRideStatus=findViewById(R.id.rideStatus);
        mHistory=findViewById(R.id.history);
        mWorkingSwitch=findViewById(R.id.workingSwitch);
        mCustomerInfo=findViewById(R.id.customerInfo);
        mCustomerProfileImage=findViewById(R.id.customerProfileImage);
        mCustomerName=findViewById(R.id.customerName);
        mcustomerPhone=findViewById(R.id.customerPhone);
        mCustomerDestination=findViewById(R.id.customerDestination);

        mFusedLocationClient= LocationServices.getFusedLocationProviderClient(this);

        mWorkingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    connectDriver();
                }else{
                    disconnectDriver();
                }
            }
        });

        mRideStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch(status){
                    case 1:
                        status=2;
                        erasePolylines();
                        if(destinationLatLng.latitude!=0 && destinationLatLng.longitude!=0){
                            getRouteToMarker(destinationLatLng);
                        }
                        mRideStatus.setText("deive completed");
                        break;

                    case 2:
                        recordRide();
                        endRide();
                        break;
                }
            }
        });

        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              isLoggingOut=true;
              disconnectDriver();
                FirebaseAuth.getInstance().signOut();
                Intent intent=new Intent(DriverMapActivity.this,MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });

        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(DriverMapActivity.this,DriverSettingsActivity.class);
                startActivity(intent);
                return;
            }
        });

        mHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(DriverMapActivity.this,HistoryActivity.class);
                intent.putExtra("customerOrDriver","Drivers");
                startActivity(intent);
                return;
            }
        });
        getAssignedCustomer();
    }

    private void getAssignedCustomer() {
        String driverId=FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverId).child("customerRequest");
        assignedCustomerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
            if(dataSnapshot.exists()){
                status=1;
                customerId=dataSnapshot.getValue().toString();
                getAssignedCustomerPickupLocation();
                getAssignedCustomerDestination();
                getAssignedCustomerInfo();
            }else{
                endRide();
            }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }
    Marker pickupMarker;
    private  DatabaseReference assignedCustomerPickupLocationRef;
    private ValueEventListener assignedCustomerPickupLocationRefListener;

    private void getAssignedCustomerInfo() {
        mCustomerInfo.setVisibility(View.VISIBLE);
        DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(customerId);
        mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && dataSnapshot.getChildrenCount()>0){
                  Map<String,Object> map= (Map<String, Object>) dataSnapshot.getValue();
                  if(map.get("name")!=null){
                      mCustomerName.setText(map.get("name").toString());
                  }
                  if(map.get("phone")!=null){
                      mcustomerPhone.setText(map.get("phone").toString());
                  }
                  if(map.get("profileImageUrl")!=null){
                      Glide.with(getApplication()).load(map.get("profileImageUrl").toString()).into(mCustomerProfileImage);
                  }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void getAssignedCustomerDestination() {

        String driverId=FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverId).child("customerRequest");
        assignedCustomerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
             if(dataSnapshot.exists()){
                 Map<String,Object> map=(Map<String,Object>) dataSnapshot.getValue();
                 if(map.get("destination")!=null){
                     destination=map.get("destination").toString();
                     mCustomerDestination.setText("Destination" +destination);
                 }else{
                     mCustomerDestination.setText("Destination: --");
                 }

                 Double destinationLat=0.0;
                 Double destinationLng=0.0;
                 if(map.get("destinationLat")!=null){
                     destinationLat=Double.valueOf(map.get("destinationLAt").toString());
                 }
                 if(map.get("destinationLng")!=null){
                     destinationLng=Double.valueOf(map.get("destinationLng").toString());
                 }
                 destinationLatLng=new LatLng(destinationLat,destinationLng);
             }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void getAssignedCustomerPickupLocation() {
        assignedCustomerPickupLocationRef=FirebaseDatabase.getInstance().getReference().child("customerRequest").child(customerId).child("l");
        assignedCustomerPickupLocationRefListener=assignedCustomerPickupLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
             if(dataSnapshot.exists() && !customerId.equals("")){

                 List<Object> map=(List<Object>) dataSnapshot.getValue();
                 double locationLat=0.0;
                 double locationLng=0.0;
                 if(map.get(0)!=null){
                     locationLat=Double.parseDouble(map.get(0).toString());
                 }
                 if(map.get(1)!=null){
                     locationLng=Double.parseDouble(map.get(1).toString());
                 }
                 pickupLatLng=new LatLng(locationLat,locationLng);
                 pickupMarker=mMap.addMarker(new MarkerOptions().position(pickupLatLng).title("Pickup location").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)));
                 getRouteToMarker(pickupLatLng);
             }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void endRide() {
     mRideStatus.setText("picked customer");
     erasePolylines();

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(userId).child("customerRequest");
        driverRef.removeValue();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(customerId);
        customerId="";
        rideDistance = 0;

        if(pickupMarker != null){
            pickupMarker.remove();
        }
        if (assignedCustomerPickupLocationRefListener != null){
            assignedCustomerPickupLocationRef.removeEventListener(assignedCustomerPickupLocationRefListener);
        }
        mCustomerInfo.setVisibility(View.GONE);
        mCustomerName.setText("");
        mcustomerPhone.setText("");
        mCustomerDestination.setText("Destination: --");
        mCustomerProfileImage.setImageResource(R.mipmap.ic_default_user);
    }

    private void recordRide() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(userId).child("history");
        DatabaseReference customerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(customerId).child("history");
        DatabaseReference historyRef = FirebaseDatabase.getInstance().getReference().child("history");
        String requestId = historyRef.push().getKey();
        driverRef.child(requestId).setValue(true);
        customerRef.child(requestId).setValue(true);

        HashMap map = new HashMap();
        map.put("driver", userId);
        map.put("customer", customerId);
        map.put("rating", 0);
        map.put("timestamp", getCurrentTimestamp());
        map.put("destination", destination);
        map.put("location/from/lat", pickupLatLng.latitude);
        map.put("location/from/lng", pickupLatLng.longitude);
        map.put("location/to/lat", destinationLatLng.latitude);
        map.put("location/to/lng", destinationLatLng.longitude);
        map.put("distance", rideDistance);
        historyRef.child(requestId).updateChildren(map);
    }

    private Long getCurrentTimestamp() {
        Long timestamp = System.currentTimeMillis()/1000;
        return timestamp;
    }

    private void getRouteToMarker(LatLng pickupLatLng) {
         if(pickupLatLng!=null && mLastLocation!=null){
             Routing routing=new Routing.Builder()
             .travelMode(AbstractRouting.TravelMode.DRIVING)
             .withListener(this)
             .alternativeRoutes(false)
             .waypoints(new LatLng(mLastLocation.getLatitude(),mLastLocation.getLongitude()),pickupLatLng)
             .build();
         routing.execute();
         }
    }

    private void erasePolylines() {

    }

    private void connectDriver() {

    }

    private void disconnectDriver() {

    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){

            }else{
                checkLocationPermission();
            }
        }
    }

    private void checkLocationPermission() {

    }

    @Override
    public void onRoutingFailure(RouteException e) {

    }

    @Override
    public void onRoutingStart() {

    }

    @Override
    public void onRoutingSuccess(ArrayList<Route> arrayList, int i) {

    }

    @Override
    public void onRoutingCancelled() {

    }
}
