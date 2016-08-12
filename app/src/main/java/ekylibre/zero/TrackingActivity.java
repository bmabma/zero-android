package ekylibre.zero;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.*;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ekylibre.api.ZeroContract;
import ekylibre.zero.util.AccountTool;
import ekylibre.zero.util.UpdatableActivity;


public class TrackingActivity extends AppCompatActivity implements TrackingListenerWriter
{

    public final static String KEY_ACCOUNT = "account";
    public final static double MAXIMAL_ACCURACY = 4.0;

    private long mMasterDuration, mMasterStart;
    private long mPrecisionModeDuration, mPrecisionModeStart;
    private boolean mRunning, mPrecisionMode;
    private String mLastProcedureNature, mLastProcedureNatureName;
    private Chronometer mMasterChrono, mPrecisionModeChrono;
    private Button mScanButton, mStartButton, mStopButton, mPauseButton, mResumeButton, mPrecisionModeStartButton, mPrecisionModeStopButton, mSyncButton;
    private HorizontalScrollView mDetails;
    private TextView mProcedureNature, mAccuracy, mLatitude, mLongitude, mCrumbsCount, mCoordinates, mBarcode;
    private String mLocationProvider;
    private TrackingListener mTrackingListener;
    private Account mAccount;
    private AlertDialog.Builder mProcedureChooser;
    private SharedPreferences mPreferences;
    private IntentIntegrator mScanIntegrator;
    private final int   REQUEST_CODE = 123;
    public static final String   _interventionID = "intervention_id";

    private LocationManager mLocationManager;
    private NotificationManager mNotificationManager;
    private Notification.Builder mNotificationBuilder;
    private int mNotificationID;
    //private Notification mNotification;

    private Button mMapButton;
    private int    mInterventionID;

    private CrumbsCalculator crumbsCalculator = new CrumbsCalculator();

    private final String TAG = "Tracking Activity";

    @Override
    public void onStart()
    {
        super.onStart();
        if (!AccountTool.isAnyAccountExist(this))
        {
            AccountTool.askForAccount(this, this);
            return;
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        mAccount = AccountTool.getCurrentAccount(this);


        // Set content view
        setContentView(R.layout.tracking);

        // Find view elements
        mDetails                  = (HorizontalScrollView) findViewById(R.id.details);
        mProcedureNature          = (TextView)   findViewById(R.id.procedure_nature);
        mMasterChrono             = (Chronometer)findViewById(R.id.master_chrono);
        mPrecisionModeChrono      = (Chronometer)findViewById(R.id.precision_mode_chrono);
        mAccuracy                 = (TextView)   findViewById(R.id.accuracy);
        mLatitude                 = (TextView)   findViewById(R.id.latitude);
        mLongitude                = (TextView)   findViewById(R.id.longitude);
        mCrumbsCount              = (TextView)   findViewById(R.id.crumbs_count);
        mStartButton              = (Button)     findViewById(R.id.start_intervention_button);
        mStopButton               = (Button)     findViewById(R.id.stop_intervention_button);
        mPauseButton              = (Button)     findViewById(R.id.pause_intervention_button);
        mResumeButton             = (Button)     findViewById(R.id.resume_intervention_button);
        mScanButton               = (Button)     findViewById(R.id.scan_code_button);
        mSyncButton               = (Button)     findViewById(R.id.sync_button);
        mMapButton                = (Button)     findViewById(R.id.map_button);
        mPrecisionModeStartButton = (Button)     findViewById(R.id.start_precision_mode_button);
        mPrecisionModeStopButton  = (Button)     findViewById(R.id.stop_precision_mode_button);


        // Acquire a reference to the system Location Manager
        mTrackingListener = new TrackingListener(this);
        mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        mLocationProvider = LocationManager.GPS_PROVIDER;

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mScanIntegrator = new IntentIntegrator(this);

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotificationID = 1;
        mNotificationBuilder = new Notification.Builder(this)
                .setOngoing(true)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("")
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, TrackingActivity.class), PendingIntent.FLAG_UPDATE_CURRENT))
                .setSmallIcon(R.mipmap.ic_stat_notify);

        createProcedureChooser();
    }

    private void createProcedureChooser()
    {
        mProcedureChooser = new AlertDialog.Builder(this)
                .setTitle(R.string.procedure_nature)
                .setNegativeButton(android.R.string.cancel, null)
                .setItems(R.array.procedures_entries, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mLastProcedureNature = getResources().getStringArray(R.array.procedures_values)[which];
                        mLastProcedureNatureName = getResources().getStringArray(R.array.procedures_entries)[which];
                        Log.d("zero", "Start a new " + mLastProcedureNature);

                        mStartButton.setVisibility(View.GONE);
                        mMasterChrono.setVisibility(View.VISIBLE);
                        mMapButton.setVisibility(View.VISIBLE);
                        mStopButton.setVisibility(View.VISIBLE);
                        mPauseButton.setVisibility(View.VISIBLE);
                        //mScanButton.setVisibility(View.VISIBLE);
                        mSyncButton.setVisibility(View.GONE);
                        //mPrecisionModeStartButton.setVisibility(View.VISIBLE);
                        //mProcedureNature.setVisibility(View.VISIBLE);
                        mProcedureNature.setText(mLastProcedureNatureName);

                        setTitle(mLastProcedureNatureName);

                        mMasterStart = SystemClock.elapsedRealtime();
                        mMasterDuration = 0;
                        mMasterChrono.setBase(mMasterStart);
                        mMasterChrono.start();

                        startTracking();

                        final Bundle metadata = new Bundle();
                        metadata.putString("procedure_nature", mLastProcedureNature);
                        addCrumb("start", metadata);

                        mNotificationBuilder
                                .setSmallIcon(R.mipmap.ic_stat_notify_running)
                                .setContentTitle(mLastProcedureNatureName)
                                .setContentText(getString(R.string.running));
                        mNotificationManager.notify(mNotificationID, mNotificationBuilder.build());
                        createIntervention();
                    }
                });
    }

    private void createIntervention()
    {
        ContentValues values = new ContentValues();

        values.put(ZeroContract.InterventionsColumns.USER, AccountTool.getCurrentAccount(this).name);
        getContentResolver().insert(ZeroContract.Interventions.CONTENT_URI, values);
        Cursor cursor = getContentResolver().query(ZeroContract.Interventions.CONTENT_URI, new String[]{ZeroContract.Interventions._ID}, null, null, null);
        if (cursor == null || !cursor.moveToLast())
            return;
        mInterventionID = cursor.getInt(0);
        cursor.close();
    }

    public void startIntervention(View view) {
        mProcedureChooser.show();
    }

    public void openMap(View view)
    {
        Intent intent = new Intent(this, MapsActivity.class);
        intent.putExtra(this._interventionID, mInterventionID);
        startActivity(intent);
    }

    public void stopIntervention(View view) {
/*        if (mPrecisionMode) {
            stopPrecisionMode(view);
        }*/
        mMasterChrono.stop();
        mMasterChrono.setVisibility(View.INVISIBLE);
        mStopButton.setVisibility(View.GONE);
        mPauseButton.setVisibility(View.GONE);
        mMapButton.setVisibility(View.GONE);
        mScanButton.setVisibility(View.GONE);
        //mPrecisionModeStartButton.setVisibility(View.GONE);
        mDetails.setVisibility(View.GONE);
        mProcedureNature.setVisibility(View.INVISIBLE);
        mStartButton.setVisibility(View.VISIBLE);
        this.stopTracking();
        this.addCrumb("stop");

        setTitle(R.string.new_intervention);
        mNotificationBuilder
                .setSmallIcon(R.mipmap.ic_stat_notify)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("");
        mNotificationManager.cancel(mNotificationID);
    }

/*
    public void startPrecisionMode(View view) {
        mPrecisionMode = true;

        mPrecisionModeStopButton.setVisibility(View.VISIBLE);
        mPrecisionModeStartButton.setVisibility(View.GONE);

        mPrecisionModeStart = SystemClock.elapsedRealtime();
        mPrecisionModeDuration = 0;
        mPrecisionModeChrono.setBase(mPrecisionModeStart);
        mPrecisionModeChrono.start();
        mPrecisionModeChrono.setVisibility(View.VISIBLE);

        this.startTracking(800);
        this.addCrumb("hard_start");

        mNotificationBuilder
                .setSmallIcon(R.mipmap.ic_stat_notify_precision_mode)
                .setContentText(getString(R.string.precision_mode));
        mNotificationManager.notify(mNotificationID, mNotificationBuilder.build());
    }*/


/*    public void stopPrecisionMode(View view) {
        mPrecisionModeStopButton.setVisibility(View.GONE);
        mPrecisionModeStartButton.setVisibility(View.VISIBLE);

        mPrecisionModeChrono.stop();
        mPrecisionModeChrono.setVisibility(View.INVISIBLE);

        this.startTracking();
        this.addCrumb("hard_stop");

        mNotificationBuilder
                .setSmallIcon(R.mipmap.ic_stat_notify_running)
                .setContentText(getString(R.string.running));
        mNotificationManager.notify(mNotificationID, mNotificationBuilder.build());

        mPrecisionMode = false;
    }*/


    public void pauseIntervention(View view) {
        mMasterDuration += SystemClock.elapsedRealtime() - mMasterStart;
        mMasterChrono.stop();
        mPauseButton.setVisibility(View.GONE);
        mStopButton.setVisibility(View.GONE);
        mScanButton.setVisibility(View.GONE);

        mResumeButton.setVisibility(View.VISIBLE);
/*        if (mPrecisionMode) {
            mPrecisionModeStopButton.setVisibility(View.GONE);
            mPrecisionModeDuration += SystemClock.elapsedRealtime() - mPrecisionModeStart;
            mPrecisionModeChrono.stop();
        } else {
            mPrecisionModeStartButton.setVisibility(View.GONE);
        }*/
        this.stopTracking();
        this.addCrumb("pause");
        mNotificationBuilder
                .setSmallIcon(R.mipmap.ic_stat_notify_paused)
                .setContentText(getString(R.string.paused));
        mNotificationManager.notify(mNotificationID, mNotificationBuilder.build());
    }

    public void resumeIntervention(View view) {
        mMasterStart = SystemClock.elapsedRealtime();
        mMasterChrono.setBase(mMasterStart - mMasterDuration);
        mMasterChrono.start();

        mResumeButton.setVisibility(View.GONE);
        mPauseButton.setVisibility(View.VISIBLE);
        // mStartButton.setVisibility(View.VISIBLE);
        mStopButton.setVisibility(View.VISIBLE);
        //mScanButton.setVisibility(View.VISIBLE);
/*        if (mPrecisionMode) {
            mPrecisionModeStart = SystemClock.elapsedRealtime();
            mPrecisionModeChrono.setBase(mPrecisionModeStart - mPrecisionModeDuration);
            mPrecisionModeChrono.start();
            mNotificationBuilder
                    .setSmallIcon(R.mipmap.ic_stat_notify_precision_mode)
                    .setContentText(getString(R.string.precision_mode));
            mPrecisionModeStopButton.setVisibility(View.VISIBLE);
        } else {
            mNotificationBuilder
                    .setSmallIcon(R.mipmap.ic_stat_notify_running)
                    .setContentText(getString(R.string.running));
            mPrecisionModeStartButton.setVisibility(View.VISIBLE);
        }*/
        this.startTracking();
        this.addCrumb("resume");

        mNotificationManager.notify(mNotificationID, mNotificationBuilder.build());
    }


    public void scanCode(View view) {
        mScanIntegrator.initiateScan();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        IntentResult aScanResult = mScanIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (aScanResult != null) {
            final String contents = aScanResult.getContents();
            if (contents != null) {
                // TODO: Ask for quantity
                // mBarcode.setText("CODE: " + contents);

                // handle scan result
                final Bundle metadata = new Bundle();
                metadata.putString("scanned_code", aScanResult.getFormatName() + ":" + contents);
                this.addCrumb("scan", metadata);
            }
        }
    }

    private void startTracking() {
        startTracking(100);
    }

    private void startTracking(long interval) {
        try
        {
            mLocationManager.requestLocationUpdates(mLocationProvider, interval, 0, mTrackingListener);
            mRunning = true;
        }
        catch(SecurityException e)
        {
            Toast.makeText(this, getResources().getString(R.string.GPSissue), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopTracking() {
        try
        {
            mLocationManager.removeUpdates(mTrackingListener);
            mRunning = false;
        }
        catch(SecurityException e)
        {
            Toast.makeText(this, getResources().getString(R.string.GPSissue), Toast.LENGTH_SHORT).show();
        }
    }

    private void addCrumb(String type) {
        this.addCrumb(type, null);
    }

    private void addCrumb(String type, Bundle metadata) {
        try
        {
            TrackingListener listener = new TrackingListener(this, type, metadata);
            mLocationManager.requestSingleUpdate(mLocationProvider, listener, null);
        }
        catch(SecurityException e)
        {
            Toast.makeText(this, getResources().getString(R.string.GPSissue), Toast.LENGTH_SHORT).show();
        }
    }

    public void writeCrumb(Location location, String type, Bundle metadata)
    {
        if (!crumbsCalculator.isSampleReady(location, type))
            return;

        if (BuildConfig.DEBUG) Log.d(TAG, "I'm writing new crumb !");
        Toast.makeText(this, "NEW CRUMB !!!", Toast.LENGTH_SHORT).show();
        Crumb crumb = crumbsCalculator.getFinalCrumb();


        putCrumbOnLocalDatabase(crumb, type, metadata);
        startTracking(crumbsCalculator.getNexPointDelay());

        sendBroadcastNewCrumb(location);

    }

    private void sendBroadcastNewCrumb(Location location)
    {
        Intent intent = new Intent(UpdatableActivity.PING);
        intent.putExtra(TrackingListenerWriter.LATITUDE, location.getLatitude());
        intent.putExtra(TrackingListenerWriter.LONGITUDE, location.getLongitude());
        intent.putExtra(this._interventionID, mInterventionID);
        sendBroadcast(intent);
    }

    private void putCrumbOnLocalDatabase(Crumb crumb, String type, Bundle metadata)
    {
        ContentValues values = new ContentValues();

        values.put(ZeroContract.CrumbsColumns.USER, AccountTool.getCurrentAccount(this).name);
        values.put(ZeroContract.CrumbsColumns.TYPE, type);
        values.put(ZeroContract.CrumbsColumns.LATITUDE, crumb.getLatitude());
        values.put(ZeroContract.CrumbsColumns.LONGITUDE, crumb.getLongitude());
        values.put(ZeroContract.CrumbsColumns.READ_AT, crumb.getDate());
        values.put(ZeroContract.CrumbsColumns.ACCURACY, 0);
        values.put(ZeroContract.CrumbsColumns.SYNCED, 0);
        values.put(ZeroContract.CrumbsColumns.FK_INTERVENTION, mInterventionID);
        putMetadata(metadata, values);

        getContentResolver().insert(ZeroContract.Crumbs.CONTENT_URI, values);
    }

    private void putMetadata(Bundle metadata, ContentValues values)
    {
        if (metadata != null) {
            try {
                List<NameValuePair> pairs = new ArrayList<NameValuePair>();
                Object[] keys = metadata.keySet().toArray();
                String key;
                for(int i = 0; i < keys.length; i++) {
                    key = (String) keys[i];
                    metadata.getString(key);
                    pairs.add(new BasicNameValuePair(key, metadata.getString(key)));
                }
                UrlEncodedFormEntity params  = new UrlEncodedFormEntity(pairs);
                ByteArrayOutputStream stream = new ByteArrayOutputStream(4096);
                params.writeTo(stream);
                values.put(ZeroContract.CrumbsColumns.METADATA, stream.toString());
            } catch(UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDestroy()
    {
        stopIntervention(null);
        super.onDestroy();
    }
}
