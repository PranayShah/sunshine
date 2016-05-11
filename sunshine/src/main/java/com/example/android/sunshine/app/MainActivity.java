package com.example.android.sunshine.app;

import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.BoxInsetLayout;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends WearableActivity  implements DataApi.DataListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener{

    private static final SimpleDateFormat AMBIENT_DATE_FORMAT =
            new SimpleDateFormat("HH:mm", Locale.US);
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_RESOLVE_ERROR = 1000;
    private static final int TIMEOUT_MS = 5000;
    private static final String START_ACTIVITY_PATH = "/ask";
    private GoogleApiClient mGoogleApiClient;
    private boolean mResolvingError = false;
    private BoxInsetLayout mContainerView;
    private TextView mTextViewHigh;
    private TextView mTextViewLow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate: ");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setAmbientEnabled();

        mContainerView = (BoxInsetLayout) findViewById(R.id.container);
        mTextViewHigh = (TextView) findViewById(R.id.high);
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        DateFormat df = DateFormat.getDateInstance();
        ((TextView) findViewById(R.id.date)).setText(df.format(new Date()));
    }
    private class StartWearableActivityTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... args) {
            Collection<String> nodes = getNodes();
            for (String node : nodes) {
                sendStartActivityMessage(node);
            }
            return null;
        }
    }
    private Collection<String> getNodes() {
        HashSet<String> results = new HashSet<>();
        NodeApi.GetConnectedNodesResult nodes =
                Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
        Log.i(TAG, "getNodes: "+ String.valueOf(nodes.getNodes().size()));
        for (Node node : nodes.getNodes()) {
            Log.i(TAG, "getNodes: "+ node.getDisplayName() + " : " + node.getId() );
            results.add(node.getId());
        }

        return results;
    }
    private void sendStartActivityMessage(String node) {
        Wearable.MessageApi.sendMessage(
                mGoogleApiClient, node, START_ACTIVITY_PATH, new byte[0]).setResultCallback(
                new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                        if (!sendMessageResult.getStatus().isSuccess()) {
                            Log.e(TAG, "Failed to send message with status code: "
                                    + sendMessageResult.getStatus().getStatusCode());
                        }
                        else {
                            Log.i(TAG, "onResult: sent initial message");
                        }
                    }
                }
        );
    }
    @Override
    protected void onPause() {
        Log.i(TAG, "onPause: ");
        super.onPause();
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume: ");
        super.onResume();
        if (!mResolvingError) {
            mGoogleApiClient.connect();
            new StartWearableActivityTask().execute();
        }
    }
    @Override
    protected void onStart() {
        Log.i(TAG, "onStart: ");
        super.onStart();
        if (!mResolvingError) {
            mGoogleApiClient.connect();
        }
    }
    @Override
    protected void onStop() {
        Log.i(TAG, "onStop: ");
        if (!mResolvingError) {
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            //Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        Log.i(TAG, "onEnterAmbient: ");
        super.onEnterAmbient(ambientDetails);
        updateDisplay();
    }

    @Override
    public void onUpdateAmbient() {
        Log.i(TAG, "onUpdateAmbient: ");
        super.onUpdateAmbient();
        updateDisplay();
    }

    @Override
    public void onExitAmbient() {
        Log.i(TAG, "onExitAmbient: ");
        updateDisplay();
        super.onExitAmbient();
    }

    private void updateDisplay() {
        if (isAmbient()) {
            mContainerView.setBackgroundColor(getResources().getColor(android.R.color.black));
            mTextViewHigh.setTextColor(getResources().getColor(android.R.color.white));

        } else {
            mContainerView.setBackground(null);
            mTextViewHigh.setTextColor(getResources().getColor(android.R.color.black));

        }
    }

    private class loadBitmapAsyncClass extends AsyncTask<Asset, Void, Bitmap>
    {

        @Override
        protected Bitmap doInBackground(Asset... params) {
            if (params[0] == null) {
                throw new IllegalArgumentException("Asset must be non-null");
            }
            ConnectionResult result =
                    mGoogleApiClient.blockingConnect(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!result.isSuccess()) {
                return null;
            }
            // convert asset into a file descriptor and block until it's ready
            InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                    mGoogleApiClient, params[0]).await().getInputStream();
            mGoogleApiClient.disconnect();

            if (assetInputStream == null) {
                Log.w(TAG, "Requested an unknown Asset.");
                return null;
            }
            // decode the stream into a bitmap
            return BitmapFactory.decodeStream(assetInputStream);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            ImageView imageView = (ImageView) findViewById(R.id.icon);
            imageView.setImageBitmap(bitmap);
        }
    }
    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.i(TAG, "onDataChanged: ");
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                // DataItem changed
                DataItem item = event.getDataItem();
                if (item.getUri().getPath().compareTo("/update") == 0) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    TextView high = (TextView)findViewById(R.id.high);
                    Log.i(TAG, "onDataChanged: HIGH: "+ String.valueOf(dataMap.getDouble("HIGH")));
                    high.setText (String.valueOf(dataMap.getDouble("HIGH")));
                    TextView low = (TextView)findViewById(R.id.low);
                    low.setText (String.valueOf(dataMap.getDouble("LOW")));

                    Asset profileAsset = dataMap.getAsset("ICON");
                    new loadBitmapAsyncClass().execute(profileAsset);
//                    Bitmap bitmap = loadBitmapFromAsset(profileAsset);


                }
            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mResolvingError = false;
        Log.i(TAG, "Connected to Google Api Service sunshine");
        Wearable.DataApi.addListener(mGoogleApiClient, this);

    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "onConnectionSuspended: ");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
        Log.i(TAG, "onConnectionFailed: ");
        if (mResolvingError) {
            // Already attempting to resolve an error.
            return;
        } else if (result.hasResolution()) {
            try {
                mResolvingError = true;
                result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                mGoogleApiClient.connect();
            }
        } else {
            Log.e(TAG, "Connection to Google API client has failed");
            mResolvingError = false;

            Wearable.DataApi.removeListener(mGoogleApiClient, this);

        }
    }
}
