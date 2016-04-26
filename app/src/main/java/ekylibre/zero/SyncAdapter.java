package ekylibre.zero;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountsException;
import android.content.ContentProvider;
import android.database.Cursor;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings.Secure;
import android.util.Log;

import ekylibre.api.Crumb;
import ekylibre.api.Instance;
import ekylibre.api.Issue;
import ekylibre.api.PlantDensityAbacus;
import ekylibre.exceptions.HTTPException;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;


import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handle the transfer of data between a server and an
 * app, using the Android sync adapter framework.
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {
    public static final String TAG = "SyncAdapter";
    public static final String ACCOUNT_TYPE = "ekylibre.account.basic";

    // Global variables
    // Define a variable to contain a content resolver instance
    ContentResolver mContentResolver;
    AccountManager mAccountManager;

    /**
     * Set up the sync adapter
     */
    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContentResolver = context.getContentResolver();
        mAccountManager = AccountManager.get(context);
    }

    /**
     * Set up the sync adapter. This form of the
     * constructor maintains compatibility with Android 3.0
     * and later platform versions
     */
    public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        mContentResolver = context.getContentResolver();
        mAccountManager = AccountManager.get(context);
    }


    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        performCrumbsSync(account, extras, authority, provider, syncResult);
        performIssuesSync(account, extras, authority, provider, syncResult);
        performPlantDensityAbaciSync(account, extras, authority, provider, syncResult);
    }


    // Push data between zero and ekylibre
    public void performCrumbsSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {

        Log.i(TAG, "Beginning network synchronization");
        
        // Get crumbs from tracking (content) provider
        Cursor cursor = mContentResolver.query(ZeroContract.Crumbs.CONTENT_URI, ZeroContract.Crumbs.PROJECTION_ALL, ZeroContract.CrumbsColumns.SYNCED + " IS NULL OR " + ZeroContract.CrumbsColumns.SYNCED + " <= 0", null, ZeroContract.Crumbs.SORT_ORDER_DEFAULT);


        try {
            if (cursor.getCount() > 0) {
                Instance instance = getInstance(account);
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    Log.i(TAG, "New crumb");
                    // Post it to ekylibre
                    JSONObject attributes = new JSONObject();
                    attributes.put("nature", cursor.getString(1));
                    attributes.put("geolocation", "SRID=4326; POINT(" + Double.toString(cursor.getDouble(3)) + " " + Double.toString(cursor.getDouble(2)) + ")");
                    SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
                    attributes.put("read_at", parser.format(new Date(cursor.getLong(4))));
                    attributes.put("accuracy", cursor.getString(5));
                    attributes.put("device_uid", "android:" + Secure.getString(mContentResolver, Secure.ANDROID_ID));
                    JSONObject hash = new JSONObject();
                    Uri metadata = Uri.parse("http://domain.tld?" + cursor.getString(6));
                    Set<String> keys = metadata.getQueryParameterNames();
                    if (keys.size() > 0) {
                        for (String key : keys) {
                            if (!key.equals("null")) {
                                hash.put(key, metadata.getQueryParameter(key));
                            }
                        }
                        if (hash.length() > 0) {
                            attributes.put("metadata", hash);
                        }
                    }

                    long id = Crumb.create(instance, attributes);
                    // Marks them as synced
                    ContentValues values = new ContentValues();
                    values.put(ZeroContract.CrumbsColumns.SYNCED, id);
                    mContentResolver.update(Uri.withAppendedPath(ZeroContract.Crumbs.CONTENT_URI, Long.toString(cursor.getLong(0))), values, null, null);
                    cursor.moveToNext();
                }
            } else {
                Log.i(TAG, "Nothing to sync");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        
        Log.i(TAG, "Finish network synchronization");
    }

    // Push data between zero and ekylibre
    public void performIssuesSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {

        Log.i(TAG, "Beginning network issues synchronization");

        // Get crumbs from Issue (content) provider
        Cursor cursor = mContentResolver.query(ZeroContract.Issues.CONTENT_URI, ZeroContract.Issues.PROJECTION_ALL, ZeroContract.IssuesColumns.SYNCED + " IS NULL OR " + ZeroContract.IssuesColumns.SYNCED + " <= 0", null, ZeroContract.Issues.SORT_ORDER_DEFAULT);


        try {
            if (cursor.getCount() > 0) {
                Instance instance = getInstance(account);
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    Log.i(TAG, "New issue");

                    // Post it to ekylibre
                    JSONObject attributes = new JSONObject();
                    attributes.put("nature", cursor.getString(1));
                    attributes.put("gravity", cursor.getInt(2));
                    attributes.put("priority", cursor.getInt(3));
                    attributes.put("description",cursor.getString(5) );
                    SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
                    attributes.put("observed_at", parser.format(new Date(cursor.getLong(8))));
                    if (cursor.getDouble(9) != 0 && cursor.getDouble(10) != 0) {
                        attributes.put("geolocation", "SRID=4326; POINT(" + Double.toString(cursor.getDouble(10)) + " " + Double.toString(cursor.getDouble(9)) + ")");
                    }

                    long id = Issue.create(instance, attributes);
                    // Marks them as synced
                    ContentValues values = new ContentValues();
                    values.put(ZeroContract.IssuesColumns.SYNCED, id);
                    mContentResolver.update(Uri.withAppendedPath(ZeroContract.Issues.CONTENT_URI, Long.toString(cursor.getLong(0))), values, null, null);
                    cursor.moveToNext();
                }
            } else {
                Log.i(TAG, "Nothing to sync");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        Log.i(TAG, "Finish network synchronization");
    }

    public void performPlantDensityAbaciSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {

        Log.i(TAG, "Beginning network plant_density_abaci synchronization");
        ContentValues cv = new ContentValues();
        Instance instance = getInstance(account);



        try {
            List<PlantDensityAbacus> abacusList = PlantDensityAbacus.all(instance, new JSONObject());
            Log.d("zero", "Nombre d'abaque : " + abacusList.size() );
            Iterator<PlantDensityAbacus> abacus = abacusList.iterator();
            while(abacus.hasNext()){
                cv.put(ZeroContract.PlantDensityAbacusItemsColumns._ID, abacus.next().toString());
            }
        }
        catch (JSONException j){
            Log.d("zero", "JSON Exception : " + j.getMessage());
            j.printStackTrace();
        }
        catch (IOException i){
            Log.d("zero", "IO Exception : " + i.getMessage());
        }
        catch (HTTPException h){
            Log.d("zero", "HTTP Exception : " + h.getMessage());
        }
        Log.i(TAG, "Finish network plant_density_abaci synchronization");
    }


    protected Instance getInstance(Account account){
        Instance instance = null;
            try {
                instance = new Instance(account, mAccountManager);
            } catch(AccountsException e) {
                Log.e(TAG, "Account manager or user cannot help. Cannot get token.");
            } catch(IOException e) {
                Log.w(TAG, "IO problem. Cannot get token.");
            }
        return instance;
    }

}
