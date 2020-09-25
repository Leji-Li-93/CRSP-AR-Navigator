package com.example.lljsm.mymapproject;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.util.Log;

import com.google.gson.Gson;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.PendingResult;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.LatLng;

import java.io.IOException;
import java.util.List;

public class LocationTools {
    private Context context;
    private Geocoder geocoder;
    private List<Address> addressList = null;
    public LocationTools(Context context){
        this.context = context;
        this.geocoder = new Geocoder(this.context);
    }

    /**
     * accept the name of the street and return the Address object of that street
     * @param streetName the name of the street
     * @return Address object
     */
    public Address getAddressFromName(String streetName){
        if(null == geocoder || !Geocoder.isPresent()){
            return null;
        }
        try {
            addressList = geocoder.getFromLocationName(streetName, 1);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(null != addressList && !addressList.isEmpty()){
            return addressList.get(0);
        } else {
            return null;
        }
    }


}
