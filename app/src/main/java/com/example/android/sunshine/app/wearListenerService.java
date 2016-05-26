package com.example.android.sunshine.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.concurrent.ExecutionException;

public class wearListenerService extends WearableListenerService {
    public static final String TAG = wearListenerService.class.getSimpleName();
    private static final String START_ACTIVITY_PATH = "/ask";
    private static final String[] NOTIFY_WEATHER_PROJECTION = new String[] {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC
    };
    // these indices must match the projection
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;
    GoogleApiClient mGoogleApiClient;
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate: ");
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.i(TAG, "onMessageReceived: "+ messageEvent);

        // Check to see if the message is to start an activity
        if (messageEvent.getPath().equals(START_ACTIVITY_PATH)) {
            Context context = this;
// Last sync was more than 1 day ago, let's send a notification with the weather.
            String locationQuery = Utility.getPreferredLocation(context);

            Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());

            // we'll query our contentProvider, as always
            Cursor cursor = context.getContentResolver().query(weatherUri, NOTIFY_WEATHER_PROJECTION, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                int weatherId = cursor.getInt(INDEX_WEATHER_ID);
                double high = cursor.getDouble(INDEX_MAX_TEMP);
                double low = cursor.getDouble(INDEX_MIN_TEMP);

                Resources resources = context.getResources();
                int artResourceId = Utility.getArtResourceForWeatherCondition(weatherId);
                String artUrl = Utility.getArtUrlForWeatherCondition(context, weatherId);

                // On Honeycomb and higher devices, we can retrieve the size of the large icon
                // Prior to that, we use a fixed size
                @SuppressLint("InlinedApi")
                int largeIconWidth = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB
                        ? resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
                        : resources.getDimensionPixelSize(R.dimen.notification_large_icon_default);
                @SuppressLint("InlinedApi")
                int largeIconHeight = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB
                        ? resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height)
                        : resources.getDimensionPixelSize(R.dimen.notification_large_icon_default);

                // Retrieve the large icon
                Bitmap largeIcon = null;
                try {
                    largeIcon = Glide.with(context)
                            .load(artUrl)
                            .asBitmap()
                            .error(artResourceId)
                            .fitCenter()
                            .into(largeIconWidth, largeIconHeight).get();
                } catch (InterruptedException | ExecutionException e) {
                    Log.e(TAG, "Error retrieving large icon from " + artUrl, e);
                    largeIcon = BitmapFactory.decodeResource(resources, artResourceId);
                } finally {
                    /*mGoogleApiClient = new GoogleApiClient.Builder(context)
                            .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                                @Override
                                public void onConnected(Bundle connectionHint) {
                                    Log.d(TAG, "onConnected: " + connectionHint);
                                    // Now you can use the Data Layer API
                                }

                                @Override
                                public void onConnectionSuspended(int cause) {
                                    Log.d(TAG, "onConnectionSuspended: " + cause);
                                }
                            })
                            .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                                @Override
                                public void onConnectionFailed(@NonNull ConnectionResult result) {
                                    Log.d(TAG, "onConnectionFailed: " + result);
                                }
                            })
                            // Request access only to the Wearable API
                            .addApi(Wearable.API)
                            .build();*/
                    Asset asset = Utility.createAssetFromBitmap(largeIcon);
                    PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/update");
                    putDataMapReq.getDataMap().putDouble("HIGH", high);
                    putDataMapReq.getDataMap().putDouble("LOW", low);
                    putDataMapReq.getDataMap().putAsset("ICON", asset);
                    putDataMapReq.getDataMap().putLong("Time",System.currentTimeMillis());
                    PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
                    putDataReq.setUrgent();
                    PendingResult<DataApi.DataItemResult> pendingResult =
                            Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
                    pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                        @Override
                        public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                            Log.i(TAG, "onResult: "+ String.valueOf(dataItemResult.getStatus().isSuccess()));
                            Log.i(TAG, "onResult: "+ dataItemResult.getStatus().getStatusMessage());
                        }
                    });
                }
            }
            assert cursor != null;
            cursor.close();

        }
    }
}
