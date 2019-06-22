package net.everythingandroid.smspopup.service;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.Locale;

/**
 * Created by osvanefaria on 30/09/17.
 */

public class SmsReader extends Service {
    private static final String TAG = "SmsReader";
    private static final Uri INBOX_MSGS_CONTENT_PROVIDER = Uri.parse("content://sms/inbox");

    // This is the start function for the service.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Cursor cursor = getContentResolver().query(INBOX_MSGS_CONTENT_PROVIDER, null, null, null, null);

        if (cursor.moveToFirst()) { // must check the result to prevent exception
            do {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS Z", new Locale("pt", "BR"));
                String msgData = "";
                msgData += cursor.getString(cursor.getColumnIndex("address")) +
                        ";" + sdf.format(new Date(cursor.getLong(cursor.getColumnIndex("date")))) +
                        ";" + sdf.format(new Date(cursor.getLong(cursor.getColumnIndex("date_sent")))) +
                        ";" + cursor.getString(cursor.getColumnIndex("body"));
                if (cursor.getInt(cursor.getColumnIndex("read")) == 0) {
                    String SmsMessageId = cursor.getString(cursor.getColumnIndex("_id"));
                    ContentValues values = new ContentValues();
                    values.put("read", 1);
                    getContentResolver().update(Uri.parse("content://sms/inbox"), values, "_id=" + SmsMessageId, null);
                    Log.d(TAG, msgData);
                    Toast.makeText(getBaseContext(), msgData,
                            Toast.LENGTH_LONG).show();
                }

            } while (cursor.moveToNext());
        } else {
            // empty box, no SMS
            Log.d(TAG, "INBOX_IS_EMPTY");

        }
        stopSelf();
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }
}
