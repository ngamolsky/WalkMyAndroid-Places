package com.example.android.walkmyandroid;

import android.Manifest;
import android.content.Intent;
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
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import java.util.Date;

public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.OnConnectionFailedListener {

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static final String LAST_DATE_KEY = "last_date";
    private static final String LAST_ADDRESS_KEY = "last_address";
    private static final String TAG = MainActivity.class.getSimpleName();
    private AddressResultReceiver mResultReceiver;
    private GoogleApiClient mGoogleApiClient;
    private Button mLocationButton;
    private TextView mLocationTextView;
    private String mLastAddress;
    private long mLastUpdateDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLocationButton = (Button) findViewById(R.id.button_location);
        mLocationTextView = (TextView) findViewById(R.id.textview_location);

        // Create an instance of GoogleApiClient
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .enableAutoManage(this, this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        // Restore the location if the activity is recreated
        if (savedInstanceState != null) {
            mLastAddress = savedInstanceState.getString(LAST_ADDRESS_KEY);
            mLastUpdateDate = savedInstanceState.getLong(LAST_DATE_KEY);
            mLocationTextView.setText(getString(R.string.address_text,
                    mLastAddress, mLastUpdateDate));
        }

        mLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getLocation();
                // Set a loading text while you wait for the address to be returned
                mLocationTextView.setText(getString(R.string.address_text,
                        getString(R.string.loading),
                        new Date()));
            }
        });

        // Create a Result Receiver object and associate it with the current thread
        mResultReceiver = new AddressResultReceiver(new Handler());
    }


    /**
     * Requests location permissions and gets the location using the FusedLocationApi if
     * the permission is granted and the GoogleApiClient is connected.
     */
    private void getLocation() {
        if (mGoogleApiClient.isConnected()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]
                        {Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);

            } else {

                // If the permission exists, get the last location and start the reverse geocode
                Location location = LocationServices.FusedLocationApi.getLastLocation(
                        mGoogleApiClient);

                if (location != null) {
                    mLastUpdateDate = location.getTime();
                    startIntentService(location);
                } else {
                    mLocationTextView.setText(R.string.no_location);
                }


            }
        } else {
            Toast.makeText(MainActivity.this, R.string.google_api_client_not_connected,
                    Toast.LENGTH_SHORT).show();
        }
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
     * Save the last location on configuration change
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putLong(LAST_DATE_KEY, mLastUpdateDate);
        outState.putString(LAST_ADDRESS_KEY, mLastAddress);
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
                    getLocation();
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
     * Extension of the ResultReceiver class for receiving the results from the reverse geocode
     * service.
     */
    private class AddressResultReceiver extends ResultReceiver {
        AddressResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {

            // Get the data from the service, either the address or an error message
            mLastAddress = resultData.getString(Constants.RESULT_DATA_KEY);

            // Display the address string or an error message sent from the intent service
            if (mLastUpdateDate > 0) {
                mLocationTextView.setText(getString(R.string.address_text, mLastAddress,
                        mLastUpdateDate));
            }
        }
    }

}
