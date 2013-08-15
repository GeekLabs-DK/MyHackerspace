/*
 * Copyright (C) 2012-2013 Aubort Jean-Baptiste (Rorist)
 * Licensed under GNU's GPL 2, see README
 */

package ch.fixme.status;

import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.AnimationDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.util.Linkify;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class Main extends Activity {

	// API: http://hackerspaces.nl/spaceapi/
	// http://spaceapi.net

	protected static String TAG = "MyHackerspace";
	protected static final String PKG = "ch.fixme.status";
	protected static final String OPEN = "Open";
	protected static final String CLOSED = "Closed";
	protected static final String PREF_API_URL_WIDGET = "api_url_widget_";
	protected static final String PREF_INIT_WIDGET = "init_widget_";
	protected static final String PREF_LAST_WIDGET = "last_widget_";
	protected static final String PREF_FORCE_WIDGET = "force_widget_";
	protected static final String STATE_HS = "hs";
	protected static final String STATE_DIR = "dir";
	private static final String PREF_API_URL = "apiurl";
	private static final int DIALOG_LOADING = 0;
	private static final int DIALOG_LIST = 1;
	private static final String TWITTER = "https://twitter.com/#!/";
	private static final String MAP_SEARCH = "geo:0,0?q=";
	private static final String MAP_COORD = "geo:%s,%s?z=23&q=%s&";

	public static final String API_DIRECTORY = "http://spaceapi.net/directory.json";
	protected static final String API_NAME = "space";
	protected static final String API_LON = "lon";
	protected static final String API_LAT = "lat";
	private static final String API_URL = "url";
	private static final String API_STATUS_TXT = "status";
	private static final String API_DURATION = "duration";
	private static final String API_ADDRESS = "address";
	private static final String API_CONTACT = "contact";
	private static final String API_EMAIL = "email";
	private static final String API_IRC = "irc";
	private static final String API_PHONE = "phone";
	private static final String API_TWITTER = "twitter";
	private static final String API_ML = "ml";
	private static final String API_STREAM = "stream";
	private static final String API_CAM = "cam";

	protected static final String API_DEFAULT = "https://fixme.ch/cgi-bin/spaceapi.py";
	protected static final String API_ICON = "icon";
	protected static final String API_ICON_OPEN = "open";
	protected static final String API_ICON_CLOSED = "closed";
	protected static final String API_LOGO = "logo";
	protected static final String API_STATUS = "open";
	protected static final String API_LASTCHANGE = "lastchange";

	private SharedPreferences mPrefs;
	private String mResultHs;
	private String mResultDir;
	private String mApiUrl;
	private boolean finishApi = false;
	private boolean finishDir = false;

	private GetDirTask getDirTask;
	private GetApiTask getApiTask;
	private GetImage getImageTask;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		mPrefs = PreferenceManager.getDefaultSharedPreferences(Main.this);
		Intent intent = getIntent();
		checkNetwork();
		getHsList(savedInstanceState);
		showHsInfo(intent, savedInstanceState);
	}

	@Override
	protected void onDestroy() {
		if (getApiTask != null) {
			getApiTask.cancel(true);
		}
		if (getDirTask != null) {
			getDirTask.cancel(true);
		}
		if (getImageTask != null) {
			getImageTask.cancel(true);
		}
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_refresh:
			checkNetwork();
			showHsInfo(getIntent(), null);
			return true;
		case R.id.menu_choose:
			showDialog(DIALOG_LIST);
			return true;
		case R.id.menu_prefs:
			startActivity(new Intent(Main.this, Prefs.class));
			return true;
		case R.id.menu_map:
			Intent intent = new Intent(Main.this, Map.class);
			intent.putExtra(STATE_DIR, mResultDir);
			try {
				JSONObject api = new JSONObject(mResultHs);
				intent.putExtra(API_LON, api.getString(API_LON));
				intent.putExtra(API_LAT, api.getString(API_LAT));
				startActivity(intent);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public Bundle onRetainNonConfigurationInstance() {
		Bundle data = new Bundle(2);
		data.putString(STATE_HS, mResultHs);
		data.putString(STATE_DIR, mResultDir);
		return data;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putString(STATE_HS, mResultHs);
		outState.putString(STATE_DIR, mResultDir);
		super.onSaveInstanceState(outState);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		AlertDialog dialog = null;
		switch (id) {
		case DIALOG_LOADING:
			dialog = new ProgressDialog(this);
			dialog.setCancelable(false);
			dialog.setMessage(getString(R.string.msg_loading));
			dialog.setCancelable(true);
			((ProgressDialog) dialog).setIndeterminate(true);
			break;
		case DIALOG_LIST:
			return createHsDialog();
		}
		return dialog;
	}

	@Override
	public void startActivity(Intent intent) {
		// http://stackoverflow.com/questions/13691241/autolink-not-working-on-htc-htclinkifydispatcher
		try {
			/* First attempt at fixing an HTC broken by evil Apple patents. */
			if (intent.getComponent() != null
					&& ".HtcLinkifyDispatcherActivity".equals(intent
							.getComponent().getShortClassName()))
				intent.setComponent(null);
			super.startActivity(intent);
		} catch (ActivityNotFoundException e) {
			/*
			 * Probably an HTC broken by evil Apple patents. This is not
			 * perfect, but better than crashing the whole application.
			 */
			super.startActivity(Intent.createChooser(intent, null));
		}
	}

	private AlertDialog createHsDialog() {
		// Construct hackerspaces list
		try {
			JSONObject obj = new JSONObject(mResultDir);
			JSONArray arr = obj.names();
			int len = obj.length();
			String[] names = new String[len];
			final ArrayList<String> url = new ArrayList<String>(len);
			for (int i = 0; i < len; i++) {
				names[i] = arr.getString(i);
			}
			Arrays.sort(names);
			for (int i = 0; i < len; i++) {
				url.add(i, obj.getString(names[i]));
			}

			// Create the dialog
			AlertDialog.Builder builder = new AlertDialog.Builder(Main.this);
			builder.setTitle(R.string.choose_hs).setItems(names,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							setIntent(null);
							Editor edit = mPrefs.edit();
							edit.putString(PREF_API_URL, url.get(which));
							getApiTask = new GetApiTask();
							getApiTask.execute(url.get(which));
							edit.commit();
						}
					});
			return builder.create();
		} catch (Exception e) {
			e.printStackTrace();
			showError(e.getClass().getCanonicalName(), e.getLocalizedMessage());
			return null;
		}
	}

	private void getHsList(Bundle savedInstanceState) {
		final Bundle data = (Bundle) getLastNonConfigurationInstance();
		if (data == null
				|| (savedInstanceState == null && !savedInstanceState
						.containsKey(STATE_DIR))) {
			getDirTask = new GetDirTask();
			getDirTask.execute(API_DIRECTORY);
		} else {
			finishDir = true;
			mResultDir = data.getString(STATE_DIR);
		}
	}

	private void showHsInfo(Intent intent, Bundle savedInstanceState) {
		// Get hackerspace api url
		if (intent != null
				&& intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID)) {
			mApiUrl = mPrefs.getString(
					PREF_API_URL_WIDGET
							+ intent.getIntExtra(
									AppWidgetManager.EXTRA_APPWIDGET_ID,
									AppWidgetManager.INVALID_APPWIDGET_ID),
					API_DEFAULT);
		} else if (intent != null && intent.hasExtra(STATE_HS)) {
			mApiUrl = intent.getStringExtra(STATE_HS);
		} else {
			mApiUrl = mPrefs.getString(PREF_API_URL, API_DEFAULT);
		}
		// Get Data
		final Bundle data = (Bundle) getLastNonConfigurationInstance();
		if (data == null
				|| (savedInstanceState == null && !savedInstanceState
						.containsKey(STATE_HS))) {
			getApiTask = new GetApiTask();
			getApiTask.execute(mApiUrl);
		} else {
			finishApi = true;
			mResultHs = data.getString(STATE_HS);
			populateDataHs();
		}
	}

	private boolean checkNetwork() {
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo netInfo = cm.getActiveNetworkInfo();
		if (netInfo == null || !netInfo.isConnected()) {
			showError(getString(R.string.error_network_title),
					getString(R.string.error_network_msg));
			return false;
		}
		return true;
	}

	private void showError(String title, String msg) {
		if (title != null && msg != null) {
			// showDialog(DIALOG_ERROR);
			new AlertDialog.Builder(this)
					.setTitle(getString(R.string.error_title) + title)
					.setMessage(msg)
					.setNeutralButton(getString(R.string.ok), null).show();
		}
	}

	private void dismissLoading() {
		if (finishApi && finishDir) {
			try {
				removeDialog(DIALOG_LOADING);
			} catch (IllegalArgumentException e) {
				Log.e(TAG, e.getMessage());
			}
		}
	}

	public class GetDirTask extends AsyncTask<String, Void, String> {

		private String mErrorTitle;
		private String mErrorMsg;

		@Override
		protected void onPreExecute() {
			showDialog(DIALOG_LOADING);
		}

		@Override
		protected String doInBackground(String... url) {
			try {
				return new Net(url[0]).getString();
			} catch (Exception e) {
				mErrorTitle = e.getClass().getCanonicalName();
				mErrorMsg = e.getLocalizedMessage();
				e.printStackTrace();
			}
			return "";
		}

		@Override
		protected void onPostExecute(String result) {
			finishDir = true;
			dismissLoading();
			if (mErrorMsg == null) {
				mResultDir = result;
			} else {
				showError(mErrorTitle, mErrorMsg);
			}
		}

		@Override
		protected void onCancelled() {
			finishDir = true;
			dismissLoading();
		}
	}

	private class GetApiTask extends AsyncTask<String, Void, String> {

		private String mErrorTitle;
		private String mErrorMsg;

		@Override
		protected void onPreExecute() {
			showDialog(DIALOG_LOADING);
			// Clean UI
			((ScrollView) findViewById(R.id.scroll)).removeAllViews();
			((TextView) findViewById(R.id.space_name))
					.setText(getString(R.string.empty));
			((TextView) findViewById(R.id.space_url))
					.setText(getString(R.string.empty));
			((ImageView) findViewById(R.id.space_image)).setImageBitmap(null);
		}

		@Override
		protected String doInBackground(String... url) {
			try {
				return new Net(url[0]).getString();
			} catch (Exception e) {
				mErrorTitle = e.getClass().getCanonicalName();
				mErrorMsg = e.getLocalizedMessage();
				e.printStackTrace();
			}
			return "";
		}

		@Override
		protected void onPostExecute(String result) {
			finishApi = true;
			dismissLoading();
			if (mErrorMsg == null) {
				mResultHs = result;
				populateDataHs();
			} else {
				showError(mErrorTitle, mErrorMsg);
			}
		}

		@Override
		protected void onCancelled() {
			finishApi = true;
			dismissLoading();
		}
	}

	private class GetImage extends AsyncTask<String, Void, Bitmap> {

		private int mId;
		private String mErrorTitle;
		private String mErrorMsg;

		public GetImage(int id) {
			mId = id;
		}

		@Override
		protected void onPreExecute() {
			ImageView img = (ImageView) findViewById(mId);
			img.setImageResource(android.R.drawable.ic_popup_sync);
			AnimationDrawable anim = (AnimationDrawable) img.getDrawable();
			anim.start();
		}

		@Override
		protected Bitmap doInBackground(String... url) {
			try {
				return new Net(url[0]).getBitmap();
			} catch (Exception e) {
				mErrorTitle = e.getClass().getCanonicalName();
				mErrorMsg = e.getLocalizedMessage();
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			if (mErrorMsg == null) {
				((ImageView) findViewById(mId)).setImageBitmap(result);
			} else {
				showError(mErrorTitle, mErrorMsg);
				((ImageView) findViewById(mId))
						.setImageResource(android.R.drawable.ic_menu_report_image);
			}
		}

	}

	private void populateDataHs() {
		try {
			JSONObject api = new JSONObject(mResultHs);
			// Initialize views
			LayoutInflater inflater = getLayoutInflater();
			LinearLayout vg = (LinearLayout) inflater.inflate(R.layout.base,
					null);
			ScrollView scroll = (ScrollView) findViewById(R.id.scroll);
			scroll.removeAllViews();
			scroll.addView(vg);
			// Mandatory fields
			String status_txt;
			// String status = API_ICON_CLOSED;
			if (api.getBoolean(API_STATUS)) {
				// status = API_ICON_OPEN;
				status_txt = OPEN;
				((TextView) findViewById(R.id.status_txt))
						.setCompoundDrawablesWithIntrinsicBounds(
								android.R.drawable.presence_online, 0, 0, 0);
			} else {
				status_txt = CLOSED;
				((TextView) findViewById(R.id.status_txt))
						.setCompoundDrawablesWithIntrinsicBounds(
								android.R.drawable.presence_busy, 0, 0, 0);
			}
			((TextView) findViewById(R.id.space_name)).setText(api
					.getString(API_NAME));
			((TextView) findViewById(R.id.space_url)).setText(api
					.getString(API_URL));
			// Status icon or space icon
			// if (!api.isNull(API_ICON)) {
			// JSONObject status_icon = api.getJSONObject(API_ICON);
			// if (!status_icon.isNull(status)) {
			// new GetImage(R.id.space_image).execute(status_icon
			// .getString(status));
			// }
			// } else {
			getImageTask = new GetImage(R.id.space_image);
			getImageTask.execute(api.getString(API_LOGO));
			// }
			// Status
			if (!api.isNull(API_STATUS_TXT)) {
				status_txt += ": " + api.getString(API_STATUS_TXT);
			}
			((TextView) findViewById(R.id.status_txt)).setText(status_txt);
			if (!api.isNull(API_LASTCHANGE)) {
				Date date = new Date(api.getLong(API_LASTCHANGE) * 1000);
				DateFormat formatter = SimpleDateFormat.getDateTimeInstance();
				TextView tv = (TextView) inflater.inflate(R.layout.entry, null);
				tv.setAutoLinkMask(0);
				tv.setText(getString(R.string.api_lastchange) + " "
						+ formatter.format(date));
				vg.addView(tv);
			}
			if (!api.isNull(API_DURATION) && api.getBoolean(API_STATUS)) {
				TextView tv = (TextView) inflater.inflate(R.layout.entry, null);
				tv.setText(getString(R.string.api_duration)
						+ api.getString(API_DURATION)
						+ getString(R.string.api_duration_hours));
				vg.addView(tv);
			}
			// Location
			Pattern ptn = Pattern.compile("^.*$", Pattern.DOTALL);
			if (!api.isNull(API_ADDRESS)
					|| (!api.isNull(API_LAT) && !api.isNull(API_LON))) {
				TextView title = (TextView) inflater.inflate(R.layout.title,
						null);
				title.setText(getString(R.string.api_location));
				vg.addView(title);
				inflater.inflate(R.layout.separator, vg);
				if (!api.isNull(API_ADDRESS)) {
					TextView tv = (TextView) inflater.inflate(R.layout.entry,
							null);
					tv.setAutoLinkMask(0);
					tv.setText(api.getString(API_ADDRESS));
					Linkify.addLinks(tv, ptn, MAP_SEARCH);
					vg.addView(tv);
				}
				if (!api.isNull(API_LON) && !api.isNull(API_LAT)) {
					String addr = (!api.isNull(API_ADDRESS)) ? api
							.getString(API_ADDRESS) : getString(R.string.empty);
					TextView tv = (TextView) inflater.inflate(R.layout.entry,
							null);
					tv.setAutoLinkMask(0);
					tv.setText(api.getString(API_LON) + ", "
							+ api.getString(API_LAT));
					Linkify.addLinks(tv, ptn, String.format(MAP_COORD,
							api.getString(API_LAT), api.getString(API_LON),
							addr));
					vg.addView(tv);
				}
			}
			// Contact
			if (!api.isNull(API_CONTACT)) {
				TextView title = (TextView) inflater.inflate(R.layout.title,
						null);
				title.setText(R.string.api_contact);
				vg.addView(title);
				inflater.inflate(R.layout.separator, vg);
				JSONObject contact = api.getJSONObject(API_CONTACT);
				// Phone
				if (!contact.isNull(API_PHONE)) {
					TextView tv = (TextView) inflater.inflate(R.layout.entry,
							null);
					tv.setText(contact.getString(API_PHONE));
					vg.addView(tv);
				}
				// Twitter
				if (!contact.isNull(API_TWITTER)) {
					TextView tv = (TextView) inflater.inflate(R.layout.entry,
							null);
					tv.setText(TWITTER + contact.getString(API_TWITTER));
					vg.addView(tv);
				}
				// IRC
				if (!contact.isNull(API_IRC)) {
					TextView tv = (TextView) inflater.inflate(R.layout.entry,
							null);
					tv.setAutoLinkMask(0);
					tv.setText(contact.getString(API_IRC));
					vg.addView(tv);
				}
				// Email
				if (!contact.isNull(API_EMAIL)) {
					TextView tv = (TextView) inflater.inflate(R.layout.entry,
							null);
					tv.setText(contact.getString(API_EMAIL));
					vg.addView(tv);
				}
				// Mailing-List
				if (!contact.isNull(API_ML)) {
					TextView tv = (TextView) inflater.inflate(R.layout.entry,
							null);
					tv.setText(contact.getString(API_ML));
					vg.addView(tv);
				}
			}
			// Stream and cam
			if (!api.isNull(API_STREAM) || !api.isNull(API_CAM)) {
				TextView title = (TextView) inflater.inflate(R.layout.title,
						null);
				title.setText(getString(R.string.api_stream));
				vg.addView(title);
				inflater.inflate(R.layout.separator, vg);
				// Stream
				if (!api.isNull(API_STREAM)) {
					JSONObject stream = api.optJSONObject(API_STREAM);
					if (stream != null) {
						JSONArray names = stream.names();
						for (int i = 0; i < stream.length(); i++) {
							final String type = names.getString(i);
							final String url = stream.getString(type);
							TextView tv = (TextView) inflater.inflate(
									R.layout.entry, null);
							tv.setText(url);
							tv.setOnClickListener(new View.OnClickListener() {
								public void onClick(View v) {
									Intent i = new Intent(Intent.ACTION_VIEW);
									i.setDataAndType(Uri.parse(url), type);
									startActivity(i);
								}
							});
							vg.addView(tv);
						}
					} else {
						String streamStr = api.optString(API_STREAM);
						TextView tv = (TextView) inflater.inflate(
								R.layout.entry, null);
						tv.setText(streamStr);
						vg.addView(tv);
					}
				}
				// Cam
				if (!api.isNull(API_CAM)) {
					JSONArray cam = api.optJSONArray(API_CAM);
					if (cam != null) {
						for (int i = 0; i < cam.length(); i++) {
							TextView tv = (TextView) inflater.inflate(
									R.layout.entry, null);
							tv.setText(cam.getString(i));
							vg.addView(tv);
						}
					} else {
						String camStr = api.optString(API_CAM);
						TextView tv = (TextView) inflater.inflate(
								R.layout.entry, null);
						tv.setText(camStr);
						vg.addView(tv);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			showError(e.getClass().getCanonicalName(), e.getLocalizedMessage());
		}
	}

}
