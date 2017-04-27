package com.example.android.walkmyandroid;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
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

public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.OnConnectionFailedListener {

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static final String LAST_LOCATION_KEY = "last_location";
    private static final String TAG = MainActivity.class.getSimpleName();
    private GoogleApiClient mGoogleApiClient;
    private Button mLocationButton;
    private TextView mLocationTextView;
    private Location mLastLocation;

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
            mLastLocation = savedInstanceState.getParcelable(LAST_LOCATION_KEY);
            if(mLastLocation != null) {
                mLocationTextView.setText(getString(R.string.location_text,
                        mLastLocation.getLatitude(), mLastLocation.getLongitude(),
                        mLastLocation.getTime()));
            }
        }

        mLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getLocation();
            }
        });
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
                mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                        mGoogleApiClient);
                if (mLastLocation != null) {
                    mLocationTextView.setText(getString(R.string.location_text,
                            mLastLocation.getLatitude(), mLastLocation.getLongitude(),
                            mLastLocation.getTime()));
                } else {
                    mLocationTextView.setText(R.string.no_location);
                }
            }
        } else {
            Toast.makeText(MainActivity.this, R.string.google_api_client_not_connected,
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Save the last location on configuration change
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(LAST_LOCATION_KEY, mLastLocation);
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
}
