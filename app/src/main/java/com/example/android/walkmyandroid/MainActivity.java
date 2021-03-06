package com.example.android.walkmyandroid;

import android.Manifest;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceLikelihood;
import com.google.android.gms.location.places.PlaceLikelihoodBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;

import java.util.Date;

public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks,
        LocationListener {

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static final int REQUEST_CHECK_SETTINGS = 2;
    private static final int REQUEST_PICK_PLACE = 3;
    private static final String LAST_DATE_KEY = "last_date";
    private static final String LAST_ADDRESS_KEY = "last_address";
    private static final String LAST_PLACE_NAME_KEY = "last_place";
    private static final String TRACKING_LOCATION_KEY = "tracking_location";
    private static final String TAG = MainActivity.class.getSimpleName();
    private AddressResultReceiver mResultReceiver;
    private GoogleApiClient mGoogleApiClient;
    private Button mLocationButton;
    private TextView mLocationTextView;
    private ImageView mAndroidImageView;
    private String mLastAddress;
    private long mLastUpdateDate;
    private boolean mTrackingLocation;
    private String mLastPlaceName;
    private AnimatorSet mRotateAnim;
    private PlaceAutocompleteFragment mAutocompleteFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLocationButton = (Button) findViewById(R.id.button_location);
        mLocationTextView = (TextView) findViewById(R.id.textview_location);
        mAndroidImageView = (ImageView) findViewById(R.id.imageview_android);

        // Create an instance of GoogleApiClient
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .enableAutoManage(this, this)
                    .addOnConnectionFailedListener(this)
                    .addConnectionCallbacks(this)
                    .addApi(Places.PLACE_DETECTION_API)
                    .addApi(LocationServices.API)
                    .build();
        }

        // Restore the state if the activity is recreated
        if (savedInstanceState != null) {
            mLastAddress = savedInstanceState.getString(LAST_ADDRESS_KEY);
            mLastPlaceName = savedInstanceState.getString(LAST_PLACE_NAME_KEY);
            mLastUpdateDate = savedInstanceState.getLong(LAST_DATE_KEY);
            mTrackingLocation = savedInstanceState.getBoolean(TRACKING_LOCATION_KEY);
            if(mTrackingLocation || mLastUpdateDate > 0) {
                mLocationTextView.setText(getString(R.string.address_text,
                        mLastPlaceName, mLastAddress, mLastUpdateDate));
            }
        }

        // Toggle the tracking state.
        mLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mTrackingLocation) {
                    startTrackingLocation();
                    mTrackingLocation = true;
                    // Set a loading text while you wait for the address to be returned
                    mLocationTextView.setText(getString(R.string.address_text,
                            getString(R.string.loading),
                            getString(R.string.loading),
                            new Date()));
                } else {
                    stopTrackingLocation();
                    mTrackingLocation = false;
                }
            }
        });

        // Set up the Place Autocomplete search bar
        mAutocompleteFragment = (PlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);

        // Get the result from the Autocomplete search bar
        mAutocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                mLastAddress = place.getAddress().toString();
                mLastPlaceName = place.getName().toString();
                mLastUpdateDate = System.currentTimeMillis();
                setAndroidType(place);
                mLocationTextView.setText(
                        getString(R.string.address_text, place.getName(),
                                place.getAddress(), mLastUpdateDate));
            }

            @Override
            public void onError(Status status) {
                Log.e(TAG, "An error occurred: " + status);
            }
        });

        // Create a Result Receiver object and associate it with the current thread
        mResultReceiver = new AddressResultReceiver(new Handler());

        // Set up the animation
        mRotateAnim = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.animator.rotate);
        mRotateAnim.setTarget(mAndroidImageView);
    }

    /**
     * Request location permissions and gets the location using the FusedLocationApi if
     * the permission is granted and the GoogleApiClient is connected, and checks the user's
     * location settings.
     */
    private void startTrackingLocation() {
        if (mGoogleApiClient.isConnected()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]
                        {Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);

            } else {

                // Create the location request and check the device settings
                final LocationRequest locationRequest = getLocationRequest();
                LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                        .addLocationRequest(locationRequest);
                PendingResult<LocationSettingsResult> result =
                        LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient,
                                builder.build());
                result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
                    @Override
                    public void onResult(
                            @NonNull LocationSettingsResult locationSettingsResult) {
                        Status status = locationSettingsResult.getStatus();
                        switch (status.getStatusCode()) {
                            case LocationSettingsStatusCodes.SUCCESS:
                                // If the settings are correct, update the button, play the
                                // animation and request updates
                                mLocationButton.setText(R.string.stop_tracking_location);
                                mRotateAnim.start();
                                try {
                                    LocationServices.FusedLocationApi.requestLocationUpdates(
                                            mGoogleApiClient, locationRequest,
                                            MainActivity.this);
                                } catch (SecurityException e) {
                                    Log.e(TAG, "onResult: ", e);
                                }
                                break;
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                // Location settings are not satisfied, but this can be fixed
                                // by showing the user a dialog
                                try {
                                    // Show the dialog by calling startResolutionForResult()
                                    status.startResolutionForResult(
                                            MainActivity.this,
                                            REQUEST_CHECK_SETTINGS);
                                } catch (IntentSender.SendIntentException e) {
                                    // Ignore the error.
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                // Location settings are not satisfied. However, we have no way
                                // to fix the settings so we won't show the dialog
                                break;
                        }
                    }
                });
            }

            mAutocompleteFragment.setText("");
        } else {
            Toast.makeText(MainActivity.this, R.string.google_api_client_not_connected,
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Stop location updates, and stop the animation.
     */
    private void stopTrackingLocation() {
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(
                    mGoogleApiClient, this);
        }
        mLocationButton.setText(R.string.start_tracking_location);
        mLocationTextView.setText(R.string.textview_hint);
        mAndroidImageView.setImageResource(R.drawable.android_plain);
        mRotateAnim.end();
    }

    /**
     * Set up the location request.
     *
     * @return The LocationRequest object containing the desired parameters.
     */
    private LocationRequest getLocationRequest() {
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return locationRequest;
    }


    /**
     * Method for starting the service to fetch the address from the set of coordinates.
     * Passes in the ResultsReceiver object and the location.
     */
    private void startIntentService(Location location) {
        Intent intent = new Intent(this, FetchAddressIntentService.class);
        intent.putExtra(Constants.RECEIVER, mResultReceiver);
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, location);
        startService(intent);
    }

    /**
     * Callback that is invoked by the FusedLocationApi, which delivers location updates with the
     * parameters specified by the location request.
     *
     * @param location The new location.
     */
    @Override
    public void onLocationChanged(Location location) {
        mLastUpdateDate = location.getTime();
        startIntentService(location);
    }

    /**
     * Pause the location tracking when the Activity is paused.
     */
    @Override
    protected void onPause() {
        super.onPause();
        stopTrackingLocation();
    }


    /**
     * Save the last location on configuration change
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(TRACKING_LOCATION_KEY, mTrackingLocation);
        outState.putLong(LAST_DATE_KEY, mLastUpdateDate);
        outState.putString(LAST_ADDRESS_KEY, mLastAddress);
        outState.putString(LAST_PLACE_NAME_KEY, mLastPlaceName);
        super.onSaveInstanceState(outState);
    }

    /**
     * Callback that is invoked when the user responds to the permissions dialog.
     *
     * @param requestCode  Request code representing the permission request issued by the app.
     * @param permissions  An array that contains the permissions that were requested.
     * @param grantResults An array with the results of the request for each permission requested.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_LOCATION_PERMISSION:

                // If the permission is granted, get the location, otherwise, show a Toast
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startTrackingLocation();
                } else {
                    Toast.makeText(this,
                            R.string.location_permission_denied,
                            Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }


    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e(TAG, connectionResult.getErrorMessage());
    }

    /**
     * This method is called when the device's location settings do not match the location request,
     * and the user must choose to change them from a dialog, or when the user returns from the
     * place picker dialog.
     *
     * @param requestCode The integer that was specified in the request for result, in this case it
     *                    is REQUEST_CHECK_SETTINGS or REQUEST_PICK_PLACE.
     * @param resultCode  RESULT_OK if the user accepts the dialog, RESULT_CANCELED otherwise
     * @param data        The Intent passed into the caller, used to carry extras. If the
     *                    request was for the place picker, this will contain the Place object.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode){
            case REQUEST_CHECK_SETTINGS:
                if (resultCode == RESULT_CANCELED) {
                    Log.d(TAG, "onActivityResult: cancelled");
                } else if (resultCode == RESULT_OK) {
                    startTrackingLocation();
                }
                break;
            case REQUEST_PICK_PLACE:
                if (resultCode == RESULT_OK) {
                    Place place = PlacePicker.getPlace(this, data);
                    mLastAddress = place.getAddress().toString();
                    mLastPlaceName = place.getName().toString();
                    mLastUpdateDate = System.currentTimeMillis();
                    setAndroidType(place);
                    mLocationTextView.setText(
                            getString(R.string.address_text, place.getName(),
                                    place.getAddress(), mLastUpdateDate));
                } else {
                    mLocationTextView.setText(R.string.no_place);
                }
                break;
        }

    }

    /**
     * Callback that is invoked when the GoogleAPiClient connects, and starts tracking
     * if the user turned it on before the connection.
     *
     * @param connectionHint Bundle of data provided to clients by Google Play services.
     *                       May be null if no content is provided by the service.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        if (mTrackingLocation) {
            startTrackingLocation();
        }
    }

    @Override
    public void onConnectionSuspended(int cause) {
    }

    /**
     * Set the Android image to reflect the Place type if it is it a school gym, restaurant
     * or library, otherwise reset it to the plain Android.
     *
     * @param currentPlace The current place.
     */
    private void setAndroidType(Place currentPlace) {
        int drawableID = -1;
        for (Integer placeType : currentPlace.getPlaceTypes()) {
            switch (placeType) {
                case Place.TYPE_SCHOOL:
                    drawableID = R.drawable.android_school;
                    break;
                case Place.TYPE_GYM:
                    drawableID = R.drawable.android_gym;
                    break;
                case Place.TYPE_RESTAURANT:
                    drawableID = R.drawable.android_restaurant;
                    break;
                case Place.TYPE_LIBRARY:
                    drawableID = R.drawable.android_library;
                    break;
            }
        }

        if (drawableID < 0) {
            drawableID = R.drawable.android_plain;
        }
        mAndroidImageView.setImageResource(drawableID);
    }


    /**
     * Extension of the ResultReceiver class for receiving the results from the reverse geocode
     * service.
     */
    private class AddressResultReceiver extends ResultReceiver {
        AddressResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) throws SecurityException {
            if (mTrackingLocation) {
                // Get the data from the service, either the address or an error message
                mLastAddress = resultData.getString(Constants.RESULT_DATA_KEY);

                // Get the current place from the Places API
                PendingResult<PlaceLikelihoodBuffer> placeResult = Places.PlaceDetectionApi
                        .getCurrentPlace(mGoogleApiClient, null);
                placeResult.setResultCallback(new ResultCallback<PlaceLikelihoodBuffer>() {
                    @Override
                    public void onResult(@NonNull PlaceLikelihoodBuffer likelyPlaces) {
                        // If a you get a result, get the most likely place and update the place
                        // name and picture
                        if (likelyPlaces.getStatus().isSuccess()) {
                            float maxLikelihood = 0;
                            Place currentPlace = null;
                            for (PlaceLikelihood placeLikelihood : likelyPlaces) {
                                if (maxLikelihood < placeLikelihood.getLikelihood()) {
                                    maxLikelihood = placeLikelihood.getLikelihood();
                                    currentPlace = placeLikelihood.getPlace();
                                }
                            }

                            if (currentPlace != null) {
                                mLastPlaceName = currentPlace.getName().toString();
                                setAndroidType(currentPlace);
                                mLocationTextView.setText(
                                        getString(R.string.address_text, mLastPlaceName,
                                                mLastAddress, mLastUpdateDate));
                            }
                            // Otherwise, show an error
                        } else {
                            mLocationTextView.setText(
                                    getString(R.string.address_text,
                                            getString(R.string.no_place),
                                            mLastAddress, mLastUpdateDate));
                        }
                        likelyPlaces.release();
                    }
                });
            }
        }
    }
}
