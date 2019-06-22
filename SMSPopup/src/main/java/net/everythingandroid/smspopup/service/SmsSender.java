/*	
    ShellMS - Android Debug Bridge Shell SMS Application
	https://github.com/try2codesecure/ShellMS
	
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.everythingandroid.smspopup.service;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;

public class SmsSender extends Service {
	  
	private static final String TAG = "SmsSender";
	private static final String TELEPHON_NUMBER_FIELD_NAME = "address";
    private static final String MESSAGE_BODY_FIELD_NAME = "body";
    private static final Uri SENT_MSGS_CONTENT_PROVIDER = Uri.parse("content://sms/sent");
	boolean DEBUG = false;	// debug mode, display additional output, sends no sms.
	
	// This is the start function for the service.
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		boolean SECRET = false;	// for secret mode => dont't save sent sms to sent folder.
		String contact = null;
		String val_num = null;	// validated Number
		String msg = null;		// message
		boolean valid = false;	// for user input validation
		int check = 0;			// getExtras check counter
		
		// extract and validate the extra strings from the service start
		Bundle extras = intent.getExtras();
		if ( extras != null ) {
			if ( extras.containsKey("debug") ) {
				DEBUG = true;
				Log.d(TAG, "DEBUG Mode enabled" );
			}
			if ( extras.containsKey("secret") ) {
				SECRET = true;
			}
			if ( extras.containsKey("contact") ) {
				contact = extras.getString("contact");
				if (contact!=null)	{
					check++;
				}
			}
			if ( extras.containsKey("msg") ) {
				msg = extras.getString("msg");
				if (msg!=null)	{
					check++;
				}
			}
			if (check == 2)	{
				if (DEBUG)	{
					Log.d(TAG, contact);
				}
				// search for valid telephone number
				valid = isNumberValid(contact);
				
				// otherwise search for valid contact names in database
				if (!valid)	{
					if (DEBUG)	{
						Log.d(TAG, "Error: Can't validate mobile number: " + contact);
						Log.d(TAG, "try searching in contacts database ...");
					}
					
					val_num = getNumberfromContact(contact, DEBUG);
					if (val_num != null)	{
						contact = val_num;
						valid = true;
						if (DEBUG)	{
							Log.d(TAG, "found contact: " + contact );
						}
					} else	{
						Log.e(TAG, "Error: No valid mobile number for contact " + contact);
					}
				}
				if (valid)	{
					if (!DEBUG)	{
						sendsms(contact, msg, !SECRET);
						Log.i(TAG, "Sent SMS to contact: " + contact );
					} else	{
						Log.d(TAG, "NO MESSAGE WILL BE SENT IN DEBUG MODE" );
						Log.d(TAG, "Contact: " + contact );
						Log.d(TAG, "Message: " + msg);
					}
				} else	{
					Log.e(TAG, "Unknown Error occoured with contact: " + contact);
				}
			} else {
				Log.e(TAG, "Error: Contact or Message missing" );
			}
		}
		stopSelf();
		return Service.START_STICKY;
	}

	// User input validation
	private Boolean isNumberValid(String contact)	{
		if (contact == null)	{
			return false;
		}
		boolean valid1 = PhoneNumberUtils.isGlobalPhoneNumber(contact);
		boolean valid2 = PhoneNumberUtils.isWellFormedSmsAddress(contact);
		if ((valid1 == true) && (valid2 == true))	{
			return true;
		}
		return false;
	}
	private String makeNumberValid(String contact)	{
		if (contact == null)	{
			return null;
		}
		String number;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			number = PhoneNumberUtils.normalizeNumber(contact);
		} else {
			number = PhoneNumberUtils.formatNumber(contact);
		}
		if (DEBUG)	{
			Log.e(TAG, "corrected number: " + number );
		}
		boolean valid = isNumberValid(number);
		if (valid)	{
			return number;
		}
		return null;
	}
	
	// This function searches for an mobile phone entry for the contact
	private String getNumberfromContact(String contact, Boolean debugging)	{
		ContentResolver cr = getContentResolver();
		String result = null;
		boolean valid = false;	
		String val_num = null;
		int contact_id = 0;
        // Cursor1 search for valid Database Entries who matches the contact name
		Uri uri = ContactsContract.Contacts.CONTENT_URI;
		String[] projection = new String[]{	ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.HAS_PHONE_NUMBER };
		String selection = ContactsContract.Contacts.DISPLAY_NAME + "=?";
		String[] selectionArgs = new String[]{String.valueOf(contact)};
		String sortOrder = null;
		Cursor cursor1 = cr.query(uri, projection, selection, selectionArgs, sortOrder);
	
	    if(cursor1.moveToFirst()){
	    	if(cursor1.getInt(cursor1.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) == 1){
	    		contact_id = cursor1.getInt(cursor1.getColumnIndex(ContactsContract.Contacts._ID));
	    		if (debugging)	{
	        		Log.d(TAG, "C1 found Database ID: " + contact_id + " with Entry: " + cursor1.getString(cursor1.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)));
	            }
	            // Cursor 2 search for valid MOBILE Telephone numbers (selection = Phone.TYPE 2)
	        	Uri uri2 = Data.CONTENT_URI;
	        	String[] projection2 = new String[]{ Phone.NUMBER, Phone.TYPE };
	        	String selection2 = Phone.CONTACT_ID + "=? AND " + Data.MIMETYPE + "=? AND " + Phone.TYPE + "=2";
	    		String[] selectionArgs2 = new String[]{ String.valueOf(contact_id), Phone.CONTENT_ITEM_TYPE };
	    		String sortOrder2 = Data.IS_PRIMARY + " desc"; 	
	        	Cursor cursor2 = cr.query(uri2, projection2, selection2, selectionArgs2, sortOrder2);
	            
	        	if(cursor2.moveToFirst()){
	                result = cursor2.getString(cursor2.getColumnIndex(Phone.NUMBER));
	        		if (debugging)	{
	                	Log.d(TAG, "C2 found number: " + result);
	                }
	            }
	            cursor2.close();
	        }
	        cursor1.close();
	    }
	    if (result != null)	{
	    	valid = isNumberValid(result);
	    }
		if (!valid)	{
			if (debugging)	{
            	Log.d(TAG, "number seems invalid, try to resolve: " + result);
            }
			val_num = makeNumberValid(result);
			if (val_num != null)	{
				valid = true;
				result = val_num;
				if (debugging)	{
	            	Log.d(TAG, "return modified number: " + result);
	            }
			}
		}
	    if (valid)	{
	    	if (debugging)	{
            	Log.d(TAG, "return number: " + result);
            }
	    	return result;
	    } else	{
	    	return null;
	    }
	}
	
	// This function sends the sms with the SMSManager
	private void sendsms(final String phoneNumber, final String message, final Boolean AddtoSent)	{
		try {
			String SENT = TAG + "_SMS_SENT";
			Intent myIntent = new Intent(SENT);
	    	PendingIntent sentPI = PendingIntent.getBroadcast(this, 0, myIntent, 0);
	        
	    	SmsManager sms = SmsManager.getDefault();
	        ArrayList<String> msgparts = sms.divideMessage(message);
			int msgcount = msgparts.size();
	    	ArrayList<PendingIntent> sentPendingIntents = new ArrayList<PendingIntent>(msgcount);
			ArrayList<PendingIntent> deliveredPendingIntents = new ArrayList<PendingIntent>(msgcount);

			Context curContext = this.getApplicationContext();

			for (int i = 0; i < msgcount; i++) {
		    	/* Adding Sent PendingIntent For Message Part */
				PendingIntent sentPendingIntent = PendingIntent.getBroadcast(curContext,
						0, new Intent("SENT"), 0);

				curContext.registerReceiver(new BroadcastReceiver() {
					@Override
					public void onReceive(Context arg0, Intent arg1) {
						switch (getResultCode()) {
							case Activity.RESULT_OK:
								Toast.makeText(getBaseContext(), "Sent.",
										Toast.LENGTH_LONG).show();
								break;
							case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
								Toast.makeText(getBaseContext(),
										"Not Sent: Generic failure.", Toast.LENGTH_LONG)
										.show();
								break;
							case SmsManager.RESULT_ERROR_NO_SERVICE:
								Toast.makeText(
										getBaseContext(),
										"Not Sent: No service (possibly, no SIM-card).",
										Toast.LENGTH_LONG).show();
								break;
							case SmsManager.RESULT_ERROR_NULL_PDU:
								Toast.makeText(getBaseContext(), "Not Sent: Null PDU.",
										Toast.LENGTH_LONG).show();
								break;
							case SmsManager.RESULT_ERROR_RADIO_OFF:
								Toast.makeText(
										getBaseContext(),
										"Not Sent: Radio off (possibly, Airplane mode enabled in Settings).",
										Toast.LENGTH_LONG).show();
								break;
							default:
								Toast.makeText(
										getBaseContext(),
										"Not Sent: ",
										Toast.LENGTH_LONG).show();
								break;
						}
					}
				}, new IntentFilter("SENT"));
				sentPendingIntents.add(sentPendingIntent);

				/* Adding Delivered PendingIntent For Message Part */
				PendingIntent deliveredPendingIntent = PendingIntent.getBroadcast(
						curContext, 0, new Intent("DELIVERED"), 0);
				curContext.registerReceiver(new BroadcastReceiver() {
					@Override
					public void onReceive(Context arg0, Intent arg1) {
						switch (getResultCode()) {
							case Activity.RESULT_OK:
								Toast.makeText(getBaseContext(), "Delivered.",
										Toast.LENGTH_LONG).show();
								break;
							case Activity.RESULT_CANCELED:
								Toast.makeText(getBaseContext(),
										"Not Delivered: Canceled.", Toast.LENGTH_LONG)
										.show();
								break;
						}
					}
				}, new IntentFilter("DELIVERED"));
				deliveredPendingIntents.add(deliveredPendingIntent);
			}

	    	sms.sendMultipartTextMessage(phoneNumber, null, msgparts, sentPendingIntents, deliveredPendingIntents);
	        if (AddtoSent)	{
				addMessageToSent(phoneNumber, message);
			}
		} catch (Exception e) {
	        e.printStackTrace();
	        Log.e(TAG, "undefined Error: SMS sending failed ... please REPORT to ISSUE Tracker");
	    }
    }
	// This function add's the sent sms to the SMS sent folder
	private void addMessageToSent(String phoneNumber, String message) {
        ContentValues sentSms = new ContentValues();
        sentSms.put(TELEPHON_NUMBER_FIELD_NAME, phoneNumber);
        sentSms.put(MESSAGE_BODY_FIELD_NAME, message);
        
        ContentResolver contentResolver = getContentResolver();
        contentResolver.insert(SENT_MSGS_CONTENT_PROVIDER, sentSms);
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}
}