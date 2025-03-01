/*
 *  Copyright (C) 2014 Benjamin W. (bitbatzen@gmail.com)
 *
 *  This file is part of WLANScanner.
 *
 *  WLANScanner is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  WLANScanner is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with WLANScanner.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.bitbatzen.wlanscanner;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import org.bitbatzen.wlanscanner.dialogs.DialogAbout;
import org.bitbatzen.wlanscanner.dialogs.DialogFilter;
import org.bitbatzen.wlanscanner.dialogs.DialogPermissions;
import org.bitbatzen.wlanscanner.dialogs.DialogQuit;
import org.bitbatzen.wlanscanner.dialogs.DialogSettings;
import org.bitbatzen.wlanscanner.events.EventManager;
import org.bitbatzen.wlanscanner.events.Events;
import org.bitbatzen.wlanscanner.events.Events.EventID;
import org.bitbatzen.wlanscanner.events.IEventListener;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class MainActivity extends Activity implements IEventListener {

	public String getLoc() {
		// Obtain the context from your Android application
		Context context = getApplicationContext();
		StringBuilder loc = new StringBuilder();
		LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				// TODO: Consider calling
				//    Activity#requestPermissions
				// here to request the missing permissions, and then overriding
				//   public void onRequestPermissionsResult(int requestCode, String[] permissions,
				//                                          int[] grantResults)
				// to handle the case where the user grants the permission. See the documentation
				// for Activity#requestPermissions for more details.
				return "Wrong permissions for loc.";
			}
		}
		Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

		if (location != null) {
			double latitude = location.getLatitude();
			double longitude = location.getLongitude();
			loc.append(latitude).append(longitude);
			final String locFinal = loc.toString();
			Log.d("Loc check:", locFinal);
			return locFinal;
		} else {
			Log.e("Loc check", "Location not available.");
		}
		return "Undefined loc error.";
	}

	public final static boolean SHOWROOM_MODE_ENABLED = false;

	public final static int MAX_SCAN_RESULT_AGE = 130; // (in seconds)

	public final static int FRAGMENT_ID_WLANLIST = 0;
	public final static int FRAGMENT_ID_DIAGRAM_24GHZ = 1;
	public final static int FRAGMENT_ID_DIAGRAM_5GHZ = 2;
	public final static int FRAGMENT_ID_DIAGRAM_6GHZ = 3;

	private final static String[] permissions = new String[]{
			Manifest.permission.ACCESS_FINE_LOCATION,
			Manifest.permission.ACCESS_COARSE_LOCATION,
			Manifest.permission.ACCESS_WIFI_STATE,
			Manifest.permission.CHANGE_WIFI_STATE,
			Manifest.permission.WRITE_EXTERNAL_STORAGE,
			Manifest.permission.READ_EXTERNAL_STORAGE,
			Manifest.permission.WAKE_LOCK,
			Manifest.permission.ACCESS_BACKGROUND_LOCATION,
			Manifest.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND,
			Manifest.permission.REQUEST_COMPANION_USE_DATA_IN_BACKGROUND,
	};

	private ActionBar.Tab tab1, tab2, tab3, tab4;
	private Fragment fragmentWLANList;
	private Fragment fragmentDiagram24GHz;
	private Fragment fragmentDiagram5GHz;
	private Fragment fragmentDiagram6GHz;
	private int currentFragmentID;

	private MenuItem buttonToggleScan;
	private ImageView ivPauseButton;
	private Animation animPauseButton;

	private ImageView ivRefreshIndicator;
	private Animation animRefreshIndicator;

	private MenuItem buttonFilter;

	private WifiManager wm;

	private ArrayList<ScanResult> scanResultListOrig;
	private ArrayList<ScanResult> scanResultListFiltered;

	private BroadcastReceiver brScanResults;

	private boolean scanEnabled;
	private boolean scanTimerIsRunning = false;
	private long lastScanResultsReceivedTime = 0;
	private long latestScanResultTime = 0; // the elapsed time of the latest scan result item since boot

	private SharedPreferences sharedPrefs;

	private OUI ouiHandler;


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final String TAG = "MainActivity";
		String currentWorkingDir = System.getProperty("user.dir");
		Log.d(TAG, "[*] Current Working Directory: " + currentWorkingDir);

		// create oui database
		ouiHandler = new OUI(this);

		EventManager.sharedInstance().addListener(this, EventID.USER_QUIT);
		EventManager.sharedInstance().addListener(this, EventID.FILTER_CHANGED);

		sharedPrefs = getPreferences(Context.MODE_PRIVATE);
		scanEnabled = sharedPrefs.getBoolean(Util.PREF_SCAN_ENABLED, true);
		currentFragmentID = sharedPrefs.getInt(Util.PREF_SELECTED_TAB, FRAGMENT_ID_WLANLIST);

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.activity_main);

		FrameLayout rootLayout = (FrameLayout) findViewById(android.R.id.content);
		View.inflate(this, R.layout.refresh_indicator, rootLayout);
		ivRefreshIndicator = (ImageView) findViewById(R.id.refresh_indicator);
		ivRefreshIndicator.setVisibility(View.INVISIBLE);

		animRefreshIndicator = AnimationUtils.loadAnimation(this, R.anim.anim_refresh_indicator);

		fragmentWLANList = new FragmentWLANList();
		fragmentDiagram24GHz = new FragmentDiagram24GHz();
		fragmentDiagram5GHz = new FragmentDiagram5GHz();
		fragmentDiagram6GHz = new FragmentDiagram6GHz();

		ActionBar actionBar = null;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			actionBar = getActionBar();
			actionBar.setDisplayShowTitleEnabled(false);
			actionBar.setDisplayUseLogoEnabled(true);
			actionBar.setDisplayShowHomeEnabled(true);
			actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
			tab1 = actionBar.newTab();
			tab1.setTabListener(new MyTabListener(this, fragmentWLANList));
			actionBar.addTab(tab1, FRAGMENT_ID_WLANLIST, currentFragmentID == FRAGMENT_ID_WLANLIST);
			tab2 = actionBar.newTab();
			tab2.setTabListener(new MyTabListener(this, fragmentDiagram24GHz));
			actionBar.addTab(tab2, FRAGMENT_ID_DIAGRAM_24GHZ, currentFragmentID == FRAGMENT_ID_DIAGRAM_24GHZ);
			tab3 = actionBar.newTab();
			tab3.setTabListener(new MyTabListener(this, fragmentDiagram5GHz));
			actionBar.addTab(tab3, FRAGMENT_ID_DIAGRAM_5GHZ, currentFragmentID == FRAGMENT_ID_DIAGRAM_5GHZ);
			tab4 = actionBar.newTab();
			tab4.setTabListener(new MyTabListener(this, fragmentDiagram6GHz));
			actionBar.addTab(tab4, FRAGMENT_ID_DIAGRAM_6GHZ, currentFragmentID == FRAGMENT_ID_DIAGRAM_6GHZ);
		}

		updateTabTitles(new ArrayList<ScanResult>());

		ivPauseButton = new ImageView(MainActivity.this);
		ivPauseButton.setImageResource(R.drawable.ic_pause);
		ivPauseButton.setClickable(true);
		ivPauseButton.setFocusable(true);
		ivPauseButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setScanEnabled(false);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
					invalidateOptionsMenu();
				}
			}
		});

		animPauseButton = AnimationUtils.loadAnimation(this, R.anim.anim_pause_button);

		wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

		scanResultListOrig = new ArrayList<>();
		scanResultListFiltered = new ArrayList<>();

		brScanResults = new BroadcastReceiver() {
			@Override
			public void onReceive(Context c, Intent intent) {
				try {
					onReceivedScanResults();
				} catch (IOException | JSONException e) {
					throw new RuntimeException(e);
				}
			}
		};

		registerReceiver(brScanResults,
				new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

		// (call WifiManager.getScanResults() to get existing scan results on app start)
		try {
			onReceivedScanResults();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}

		requestScan();

		handlePermissions();
	}

	public void handlePermissions() {
		if (android.os.Build.VERSION.SDK_INT < 23) {
			return;
		}

		List<String> permissionsToRequest = new ArrayList<String>();

		for (int i = 0; i < permissions.length; i++) {
			String p = permissions[i];
			if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
				permissionsToRequest.add(p);
			}
		}

		if (!permissionsToRequest.isEmpty()) {
			new DialogPermissions(this, permissionsToRequest).show();
		}
	}

	public void requestPermissions(String[] permissions) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			requestPermissions(permissions, 111);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		SharedPreferences.Editor editor = getPreferences(Context.MODE_PRIVATE).edit();
		editor.putBoolean(Util.PREF_SCAN_ENABLED, scanEnabled);
		editor.putInt(Util.PREF_SELECTED_TAB, currentFragmentID);
		editor.commit();
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(brScanResults);
	}

	@Override
	public void onBackPressed() {
		new DialogQuit(this).show();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.actionbar_buttons, menu);

		buttonToggleScan = menu.findItem(R.id.actionbutton_toggle_scan);
		buttonFilter = menu.findItem(R.id.actionbutton_filter);
		updateFilterButton();

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (scanEnabled) {
			if (ivPauseButton.getAnimation() == null) {
				ivPauseButton.startAnimation(animPauseButton);
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				buttonToggleScan.setActionView(ivPauseButton);
			}
		} else {
			ivPauseButton.clearAnimation();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				buttonToggleScan.setActionView(null);
			}
			buttonToggleScan.setIcon(R.drawable.ic_play);
		}

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.actionbutton_toggle_scan:
				setScanEnabled(!scanEnabled);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
					invalidateOptionsMenu();
				}
				return true;
			case R.id.actionbutton_filter:
				new DialogFilter(this).show();
				return true;
			case R.id.actionbutton_settings:
				new DialogSettings(this).show();
				return true;
			case R.id.actionbutton_about:
				new DialogAbout(this).show();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void onReceivedScanResults() throws IOException, JSONException {
		lastScanResultsReceivedTime = System.currentTimeMillis();

		if (!scanEnabled) {
			return;
		}

		List<ScanResult> scanResults;
		if (SHOWROOM_MODE_ENABLED) {
			scanResults = createShowRoomScanResults();
		} else {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
					// TODO: Consider calling
					//    Activity#requestPermissions
					// here to request the missing permissions, and then overriding
					//   public void onRequestPermissionsResult(int requestCode, String[] permissions,
					//                                          int[] grantResults)
					// to handle the case where the user grants the permission. See the documentation
					// for Activity#requestPermissions for more details.
				}
			}
			scanResults = wm.getScanResults();
		}

		scanResultListOrig.clear();
    	scanResultListFiltered.clear();

		ScanResult latestScanResult = getLatestScanResult(scanResults);
		if (latestScanResult != null) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
				latestScanResultTime = latestScanResult.timestamp;
			}
		}

    	for (ScanResult sr : scanResults) {
    		if (Build.VERSION.SDK_INT >= 17) {
				long age = ((SystemClock.elapsedRealtime() * 1000) - sr.timestamp) / 1000000;
				// if the wlan was last seen more than MAX_SCAN_RESULT_AGE seconds ago, do not add it to the list
				if (age > MAX_SCAN_RESULT_AGE) {
					continue;
				}
    		}

    		scanResultListOrig.add(sr);

			if (checkFilter(sr)) {
				scanResultListFiltered.add(sr);
			}
    	}

		updateTabTitles(scanResultListFiltered);

    	Animation anim = ivRefreshIndicator.getAnimation();
    	if (anim == null || (anim != null && anim.hasEnded())) {
	    	ivRefreshIndicator.setVisibility(View.VISIBLE);
	    	ivRefreshIndicator.startAnimation(animRefreshIndicator);
	    	ivRefreshIndicator.setVisibility(View.GONE);
    	}

    	EventManager.sharedInstance().sendEvent(EventID.SCAN_RESULT_CHANGED);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			invalidateOptionsMenu();
		}
	}

	public OUI getOUIHandler() {
		return ouiHandler;
	}

	public ScanResult getLatestScanResult(List<ScanResult> scanResults) {
		if (android.os.Build.VERSION.SDK_INT < 17) {
			return null;
		}

		ScanResult latest = null;

		for (ScanResult sr : scanResults) {
			if (latest == null || sr.timestamp < latest.timestamp) {
				latest = sr;
			}
		}
		return latest;
	}

	private void updateTabTitles(ArrayList<ScanResult> scanResults) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			getActionBar().getTabAt(MainActivity.FRAGMENT_ID_WLANLIST).setText("List  ("
					+ Util.getScanResults(scanResults, null).size() + ")");
			getActionBar().getTabAt(MainActivity.FRAGMENT_ID_DIAGRAM_6GHZ).setText("6  ("
					+ Util.getScanResults(scanResults, Util.FrequencyBand.SIX_GHZ).size() + ")");
			getActionBar().getTabAt(MainActivity.FRAGMENT_ID_DIAGRAM_24GHZ).setText("2.4  ("
					+ Util.getScanResults(scanResults, Util.FrequencyBand.TWO_FOUR_GHZ).size() + ")");
			getActionBar().getTabAt(MainActivity.FRAGMENT_ID_DIAGRAM_5GHZ).setText("5  ("
					+ Util.getScanResults(scanResults, Util.FrequencyBand.FIVE_GHZ).size() + ")");
		}
	}

	private boolean checkFilter(ScanResult sr) {
		SharedPreferences sharedPrefs 	= getPreferences(Context.MODE_PRIVATE);

		boolean filter24GHzEnabled 		= sharedPrefs.getBoolean(Util.PREF_FILTER_24GHZ_ENABLED, true);
		boolean filter5GHzEnabled 		= sharedPrefs.getBoolean(Util.PREF_FILTER_5GHZ_ENABLED, true);
		boolean filter6GHzEnabled 		= sharedPrefs.getBoolean(Util.PREF_FILTER_6GHZ_ENABLED, true);

		boolean filterSSIDEnabled 		= sharedPrefs.getBoolean(Util.PREF_FILTER_SSID_ENABLED, false);
		String filterSSID 				= sharedPrefs.getString(Util.PREF_FILTER_SSID, "");

		boolean filterBSSIDEnabled 		= sharedPrefs.getBoolean(Util.PREF_FILTER_BSSID_ENABLED, false);
		String filterBSSID 				= sharedPrefs.getString(Util.PREF_FILTER_BSSID, "");

		boolean filterChannelEnabled 	= sharedPrefs.getBoolean(Util.PREF_FILTER_CHANNEL_ENABLED, false);
		String filterChannel			= sharedPrefs.getString(Util.PREF_FILTER_CHANNEL, "");

		boolean filterStandardEnabled 	= sharedPrefs.getBoolean(Util.PREF_FILTER_STANDARD_ENABLED, false);
		String filterStandard			= sharedPrefs.getString(Util.PREF_FILTER_STANDARD, "");

		boolean filterCapabiliEnabled 	= sharedPrefs.getBoolean(Util.PREF_FILTER_CAPABILI_ENABLED, false);
		String filterCapabili			= sharedPrefs.getString(Util.PREF_FILTER_CAPABILI, "");

		boolean filterInvertEnabled 	= sharedPrefs.getBoolean(Util.PREF_FILTER_INVERT_ENABLED, false);

		if (filter24GHzEnabled && filter5GHzEnabled && filter6GHzEnabled
				&& ! filterSSIDEnabled && ! filterBSSIDEnabled && ! filterChannelEnabled && ! filterStandardEnabled && ! filterCapabiliEnabled) {

			return true;
		}

		Util.FrequencyBand fb 	= Util.getFrequencyBand(sr);
		boolean is24GHzMatch 	= (filter24GHzEnabled && fb == Util.FrequencyBand.TWO_FOUR_GHZ);
		boolean is5GHzMatch 	= (filter5GHzEnabled && fb == Util.FrequencyBand.FIVE_GHZ);
		boolean is6GHzMatch 	= (filter6GHzEnabled && fb == Util.FrequencyBand.SIX_GHZ);

		boolean isSSIDMatch 	= (filterSSIDEnabled && sr.SSID.toLowerCase().contains(filterSSID.toLowerCase()));
		boolean isBSSIDMatch 	= (filterBSSIDEnabled && sr.BSSID.toLowerCase().contains(filterBSSID.toLowerCase()));
		boolean isCapabiliMatch	= (filterCapabiliEnabled && sr.capabilities.toLowerCase().contains(filterCapabili.toLowerCase()));
		boolean isStandardMatch = (filterStandardEnabled && Util.getWLANStandard(sr).toLowerCase().contains(filterStandard.toLowerCase()));

		boolean isChannelMatch = false;
		if (filterChannelEnabled) {
			int fChannel = Integer.parseInt(filterChannel);
			int[] frequencies = Util.getFrequencies(sr);

			for (int f : frequencies) {
				if (Util.getChannel(f) == fChannel) {
					isChannelMatch = true;
					break;
				}
			}
		}

		if (filterInvertEnabled) {
			return ((is24GHzMatch || is5GHzMatch || is6GHzMatch)
					&& (! filterSSIDEnabled || ! isSSIDMatch)
					&& (! filterBSSIDEnabled || ! isBSSIDMatch)
					&& (! filterCapabiliEnabled || ! isCapabiliMatch)
					&& (! filterChannelEnabled || ! isChannelMatch)
					&& (! filterStandardEnabled || ! isStandardMatch));
		} else {
			return ((is24GHzMatch || is5GHzMatch || is6GHzMatch)
					&& (! filterSSIDEnabled || isSSIDMatch)
					&& (! filterBSSIDEnabled || isBSSIDMatch)
					&& (! filterCapabiliEnabled || isCapabiliMatch)
					&& (! filterChannelEnabled || isChannelMatch)
					&& (! filterStandardEnabled || isStandardMatch));
		}
	}

	private void onFilterChanged() throws IOException, JSONException {
		ArrayList<ScanResult> mList = new ArrayList<>();

		for (ScanResult sr : scanResultListOrig) {
			if (checkFilter(sr)) {
				mList.add(sr);
			}
		}

		scanResultListFiltered = mList;
		updateTabTitles(scanResultListFiltered);
		updateFilterButton();
		EventManager.sharedInstance().sendEvent(Events.EventID.SCAN_RESULT_CHANGED);
	}

	private void updateFilterButton() {
		SharedPreferences sharedPrefs 	= getPreferences(Context.MODE_PRIVATE);
		boolean filter24GHzEnabled 		= sharedPrefs.getBoolean(Util.PREF_FILTER_24GHZ_ENABLED, true);
		boolean filter5GHzEnabled 		= sharedPrefs.getBoolean(Util.PREF_FILTER_5GHZ_ENABLED, true);
		boolean filter6GHzEnabled 		= sharedPrefs.getBoolean(Util.PREF_FILTER_6GHZ_ENABLED, true);

		boolean filterSSIDEnabled 		= sharedPrefs.getBoolean(Util.PREF_FILTER_SSID_ENABLED, false);
		boolean filterBSSIDEnabled 		= sharedPrefs.getBoolean(Util.PREF_FILTER_BSSID_ENABLED, false);
		boolean filterChannelEnabled 	= sharedPrefs.getBoolean(Util.PREF_FILTER_CHANNEL_ENABLED, false);
		boolean filterStandardEnabled 	= sharedPrefs.getBoolean(Util.PREF_FILTER_STANDARD_ENABLED, false);
		boolean filterCapabiliEnabled 	= sharedPrefs.getBoolean(Util.PREF_FILTER_CAPABILI_ENABLED, false);

		if (! filter24GHzEnabled || ! filter5GHzEnabled || ! filter6GHzEnabled
				|| filterSSIDEnabled || filterBSSIDEnabled || filterChannelEnabled || filterStandardEnabled || filterCapabiliEnabled) {

			buttonFilter.setIcon(R.drawable.ic_filter_active);
		}
		else {
			buttonFilter.setIcon(R.drawable.ic_filter);
		}
	}

    public ArrayList<ScanResult> getScanResults() {
    	return scanResultListFiltered;
    }
	
	private void setScanEnabled(boolean enable) {
		scanEnabled = enable;
		if (enable) {
			requestScan();
		}
	}

	private void requestScan() {
		if (! scanEnabled || scanTimerIsRunning) {
			return;
		}

		long delay = getMillisToNextScanRequest();

		scanTimerIsRunning = true;
		new Timer().schedule(new TimerTask() {
			@Override
			public void run() {
				scanTimerIsRunning = false;
				if (scanEnabled) {
					SharedPreferences.Editor editor = getPreferences(Context.MODE_PRIVATE).edit();
					editor.putLong(Util.PREF_SETTING_LAST_SCAN_REQUEST_TIME, System.currentTimeMillis());
					editor.commit();

					wm.startScan();
					requestScan();
				}
			}
		}, delay);
	}

	public long getMillisToNextScanRequest() {
		SharedPreferences sharedPrefs 	= getPreferences(Context.MODE_PRIVATE);
		long lastScanRequestTime 		= sharedPrefs.getLong(Util.PREF_SETTING_LAST_SCAN_REQUEST_TIME, 0);
		float scanDelay 				= sharedPrefs.getFloat(Util.PREF_SETTING_SCAN_DELAY, Util.getDefaultScanDelay());
		long millis						= (long) Math.max(0, scanDelay - (System.currentTimeMillis() - lastScanRequestTime));
		return millis;
	}

	public long getLastScanResultsReceivedTime() {
		return lastScanResultsReceivedTime;
	}

	/**
	 * @return the elapsed time of the latest scan result item since boot
	 */
	public long getLatestScanResultTime() {
		return latestScanResultTime;
	}

	public void setCurrentFragmentID(int fragmentID) {
		currentFragmentID = fragmentID;
	}

	public int getCurrentFragmentID() {
		return currentFragmentID;
	}

	@Override
	public void handleEvent(EventID eventID) throws IOException, JSONException {
		switch (eventID) {
		case USER_QUIT:
        	setScanEnabled(false);

        	currentFragmentID 	= FRAGMENT_ID_WLANLIST;
        	scanEnabled 		= true;

			finish();
			break;

		case FILTER_CHANGED:
			onFilterChanged();
			break;

		default:
			break;
		}
	}

	@TargetApi(30)
	private List<ScanResult> createShowRoomScanResults() {
		List<ScanResult> scanResults = new ArrayList<>();

		ScanResult sr = new ScanResult();
		sr.SSID			= "SSID-1";
		sr.BSSID		= "f0:9f:c2:29:3a:01";
		sr.level 		= -38;
		sr.centerFreq0	= Util.getFrequency(Util.FrequencyBand.TWO_FOUR_GHZ, 6);
		sr.channelWidth	= ScanResult.CHANNEL_WIDTH_40MHZ;
		sr.capabilities	= "WPA2-PSK-CCMP ESS WPS";
		sr.timestamp	= System.currentTimeMillis();
		scanResults.add(sr);

		sr = new ScanResult();
		sr.SSID			= "SSID-2";
		sr.BSSID		= "24:65:11:2a:19:e8";
		sr.level 		= -45;
		sr.centerFreq0	= Util.getFrequency(Util.FrequencyBand.TWO_FOUR_GHZ, 6);
		sr.channelWidth	= ScanResult.CHANNEL_WIDTH_40MHZ;
		sr.capabilities	= "WPA2-PSK-CCMP ESS WPS";
		sr.timestamp	= System.currentTimeMillis();
		scanResults.add(sr);

		sr = new ScanResult();
		sr.SSID			= "SSID-3";
		sr.BSSID		= "f0:9f:c2:01:9a:20";
		sr.level 		= -51;
		sr.frequency	= Util.getFrequency(Util.FrequencyBand.TWO_FOUR_GHZ, 1);
		sr.channelWidth	= ScanResult.CHANNEL_WIDTH_20MHZ;
		sr.capabilities	= "WPA2-PSK-CCMP ESS";
		sr.timestamp	= System.currentTimeMillis();
		scanResults.add(sr);

		sr = new ScanResult();
		sr.SSID			= "SSID-4";
		sr.BSSID		= "24:65:11:2e:40:f7";
		sr.level		= -74;
		sr.frequency	= Util.getFrequency(Util.FrequencyBand.TWO_FOUR_GHZ, 13);
		sr.channelWidth	= ScanResult.CHANNEL_WIDTH_20MHZ;
		sr.capabilities	= "WPA2-PSK-CCMP ESS WPS";
		sr.timestamp	= System.currentTimeMillis();
		scanResults.add(sr);

		sr = new ScanResult();
		sr.SSID			= "SSID-5";
		sr.BSSID		= "98:da:c4:49:b9:22";
		sr.level 		= -81;
		sr.centerFreq0	= Util.getFrequency(Util.FrequencyBand.TWO_FOUR_GHZ, 8);
		sr.channelWidth	= ScanResult.CHANNEL_WIDTH_40MHZ;
		sr.capabilities	= "WPA2-PSK-CCMP ESS WPS";
		sr.timestamp	= System.currentTimeMillis();
		scanResults.add(sr);

		sr = new ScanResult();
		sr.SSID			= "SSID-6";
		sr.BSSID		= "98:da:c4:b3:78:1a";
		sr.level		= -83;
		sr.centerFreq0	= Util.getFrequency(Util.FrequencyBand.FIVE_GHZ, 12);
		sr.channelWidth	= ScanResult.CHANNEL_WIDTH_160MHZ;
		sr.capabilities	= "ESS";
		sr.timestamp	= System.currentTimeMillis();
		scanResults.add(sr);

		sr = new ScanResult();
		sr.SSID			= "SSID-7";
		sr.BSSID		= "24:65:11:c3:8a:91";
		sr.level 		= -92;
		sr.centerFreq0	= Util.getFrequency(Util.FrequencyBand.FIVE_GHZ, 36);
		sr.channelWidth	= ScanResult.CHANNEL_WIDTH_40MHZ;
		sr.capabilities	= "WPA2-PSK-CCMP ESS WPS";
		sr.timestamp	= System.currentTimeMillis();
		scanResults.add(sr);

		sr = new ScanResult();
		sr.SSID			= "SSID-8";
		sr.BSSID		= "f0:9f:c2:33:9f:44";
		sr.level 		= -93;
		sr.centerFreq0	= Util.getFrequency(Util.FrequencyBand.SIX_GHZ, 39);
		sr.centerFreq1	= Util.getFrequency(Util.FrequencyBand.SIX_GHZ, 87);
		sr.channelWidth	= ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ;
		sr.capabilities	= "ESS";
		sr.timestamp	= System.currentTimeMillis();
		scanResults.add(sr);

		sr = new ScanResult();
		sr.SSID			= "SSID-9";
		sr.BSSID		= "24:65:11:b3:78:1a";
		sr.level		= -96;
		sr.centerFreq0	= Util.getFrequency(Util.FrequencyBand.SIX_GHZ, 17);
		sr.channelWidth	= ScanResult.CHANNEL_WIDTH_160MHZ;
		sr.capabilities	= "ESS";
		sr.timestamp	= System.currentTimeMillis();
		scanResults.add(sr);

		sr = new ScanResult();
		sr.SSID			= "SSID-10";
		sr.BSSID		= "98:da:c4:31:7a:82";
		sr.level 		= -97;
		sr.frequency	= Util.getFrequency(Util.FrequencyBand.TWO_FOUR_GHZ, 13);
		sr.channelWidth	= ScanResult.CHANNEL_WIDTH_20MHZ;
		sr.capabilities	= "ESS";
		sr.timestamp	= System.currentTimeMillis();
		scanResults.add(sr);

		sr = new ScanResult();
		sr.SSID			= "SSID-11";
		sr.BSSID		= "f0:9f:c2:b3:78:1a";
		sr.level		= -97;
		sr.centerFreq0	= Util.getFrequency(Util.FrequencyBand.FIVE_GHZ, 36);
		sr.channelWidth	= ScanResult.CHANNEL_WIDTH_160MHZ;
		sr.capabilities	= "ESS";
		sr.timestamp	= System.currentTimeMillis();
		scanResults.add(sr);

		return scanResults;
	}
}
