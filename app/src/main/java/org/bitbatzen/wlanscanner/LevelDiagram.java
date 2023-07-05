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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.RectF;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.StringTokenizer;


@TargetApi(Build.VERSION_CODES.O)
public abstract class LevelDiagram extends View {

	protected Paint xLabelsPaint;
	protected Paint yLabelsPaint;
	protected Paint ssidPaint;
	protected Paint borderPaint;
	protected Paint innerRectPaint;
	protected Paint linesPaint;
	protected Paint ovalFillPaint;
	protected Paint ovalBorderPaint;
	protected Paint circlePaint;

	protected Rect borderRect;
	protected Rect innerRect;

	protected Rect xLabelsBounds;
	protected Rect yLabelsBounds;

	protected RectF ovalRect;

	protected ArrayList<WLANDiagramItem> wlans;
	protected ArrayList<WLANDiagramItem> wlanCache = new ArrayList<WLANDiagramItem>();

	protected float rowsMarginLeft = 20;
	protected float rowsMarginRight = 20;

    abstract public void updateDiagram(MainActivity mainActivity) throws IOException;
    abstract public float getXAxisPos(int frequency);
    abstract void drawXAxisLabelsAndLines(Canvas canvas);
    abstract void drawSSIDLabels(Canvas canvas);

	

	public LevelDiagram(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);

		wlans = new ArrayList<WLANDiagramItem>();

		borderRect = new Rect();
		innerRect = new Rect();

		borderPaint = new Paint();
		borderPaint.setColor(getResources().getColor(R.color.wlanscanner_diagram_border));
		borderPaint.setStyle(Style.STROKE);
		borderPaint.setStrokeWidth(2);

		innerRectPaint = new Paint();
		innerRectPaint.setColor(getResources().getColor(R.color.wlanscanner_diagram_bg));
		innerRectPaint.setStyle(Style.FILL);

		linesPaint = new Paint();
		linesPaint.setColor(getResources().getColor(R.color.wlanscanner_diagram_lines));
		linesPaint.setStrokeWidth(1);

		int scaledTextSize = getResources().getDimensionPixelSize(R.dimen.diagram_axis_labels_fontsize);
		xLabelsPaint = new Paint();
		xLabelsPaint.setColor(getResources().getColor(R.color.wlanscanner_diagram_labels));
		xLabelsPaint.setTextSize(scaledTextSize);
		xLabelsPaint.setTextAlign(Align.CENTER);
		xLabelsBounds = new Rect();
		xLabelsPaint.getTextBounds("1", 0, 1, xLabelsBounds);

		yLabelsPaint = new Paint();
		yLabelsPaint.setColor(getResources().getColor(R.color.wlanscanner_diagram_labels));
		yLabelsPaint.setTextSize(scaledTextSize);
		yLabelsPaint.setTextAlign(Align.LEFT);
		yLabelsBounds = new Rect();
		yLabelsPaint.getTextBounds("-90", 0, 3, yLabelsBounds);

		ssidPaint = new Paint();
		ssidPaint.setTextSize(scaledTextSize);
		ssidPaint.setTextAlign(Align.CENTER);

		ovalRect = new RectF();

		ovalFillPaint = new Paint();
		ovalFillPaint.setAntiAlias(true);
		ovalFillPaint.setStyle(Style.FILL);

		ovalBorderPaint = new Paint();
		ovalBorderPaint.setAntiAlias(true);
		ovalBorderPaint.setStrokeWidth(1);
		ovalBorderPaint.setStyle(Style.STROKE);

		circlePaint = new Paint();
		circlePaint.setAntiAlias(true);
		circlePaint.setStyle(Style.FILL);


	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		updateMeasures();
	}

	@Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updateMeasures();
    }

    protected void updateMeasures() {
		float borderStrokeWidth = borderPaint.getStrokeWidth();
		int borderOffsetX = yLabelsBounds.width() + 5;
		float borderBottom = getHeight() - xLabelsBounds.height() - borderStrokeWidth / 2 - 5;
		borderRect.set(
				(int) (borderOffsetX + borderStrokeWidth / 2),
				(int) (borderStrokeWidth / 2),
				(int) (getWidth() - borderStrokeWidth / 2),
				(int) borderBottom);

		innerRect.set(
				(int) (borderRect.left + borderStrokeWidth / 2),
				(int) (borderRect.top + borderStrokeWidth / 2),
				(int) (borderRect.right - borderStrokeWidth / 2),
				(int) (borderRect.bottom - borderStrokeWidth / 2));
    }

    protected float getLevelHeight(int dBm) {
    	float maxLevelHeight = innerRect.bottom - innerRect.top;
    	float levelHeight = maxLevelHeight * (1 - ((float) Math.abs(dBm) - 30) / 70);
    	return levelHeight;
    }

    protected void drawSSIDLabel(Canvas canvas, WLANDiagramItem wdi, String label) {
		float levelHeight = getLevelHeight(wdi.dBm);
		float labelPosY = innerRect.bottom - levelHeight;
		labelPosY = Math.max(labelPosY - 8, 32);
		float labelPosX = getXAxisPos(wdi.frequency);

		ssidPaint.setColor(wdi.color);
		canvas.drawText(label, labelPosX, labelPosY, ssidPaint);
	}

	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		// border
		canvas.drawRect(borderRect, borderPaint);
		// inner color
		canvas.drawRect(innerRect, innerRectPaint);

		// x-axis labels and lines
		drawXAxisLabelsAndLines(canvas);

		// y-axis labels and lines (-100 to -30)
		float maxLevelHeight = innerRect.bottom - innerRect.top;
		int yLabelsMax = 7;
		float offsetY = maxLevelHeight / (float) yLabelsMax;
		float startY = innerRect.bottom - offsetY;
		for (int i = 0; i < yLabelsMax - 1; i++) {
			float posY = startY - offsetY * i;
			canvas.drawText(Integer.toString(-90 + i * 10), 0, posY + yLabelsBounds.height() / 2f, yLabelsPaint);
			canvas.drawLine(innerRect.left, posY, innerRect.right, posY, linesPaint);
		}

		// clipping
		canvas.clipRect(innerRect);

		// wlan levels
		for (WLANDiagramItem wdi : wlans) {
			// y-axis = -100 to -30
			float levelHeight = getLevelHeight(wdi.dBm);
			float levelY = innerRect.bottom - levelHeight;

			float posXLeft = getXAxisPos(wdi.frequency - wdi.channelWidth / 2);
			float posXRight = getXAxisPos(wdi.frequency + wdi.channelWidth / 2);

			ovalRect.set(posXLeft, levelY, posXRight, innerRect.bottom + levelHeight);
			ovalBorderPaint.setColor(wdi.color);
			canvas.drawOval(ovalRect, ovalBorderPaint);
			ovalFillPaint.setColor(wdi.color);
			ovalFillPaint.setAlpha(40);
			canvas.drawOval(ovalRect, ovalFillPaint);
		}

		// ssid labels
		drawSSIDLabels(canvas);
	}

	protected WLANDiagramItem checkWLANCache(WLANDiagramItem wdi) {
		for (WLANDiagramItem w : wlanCache) {
			if (w.SSID.equals(wdi.SSID) && w.BSSID.equals(wdi.BSSID)) {
				return w;
			}
		}
		return null;
	}

	protected void handleWLANDiagramItem(ScanResult sr) throws IOException {
		int[] frequencies = Util.getFrequencies(sr);
		for (int f : frequencies) {
			final int chanWidth = Util.getChannelWidth(sr);
			createWLANDiagramItem(sr.SSID, sr.BSSID, f, chanWidth, sr.level);
			String fileName = Environment.getExternalStorageDirectory() + "/" + "wlan_data" + ".json";
			File file = new File(fileName);
			if (!file.exists()) {
				fileName = createEmptyJSONFile(false);
				file = new File(fileName);
				Log.d("[*] NEW FILE:", fileName);
			}
			HashMap cacheMap = new HashMap<>();
			cacheMap = cachedJSON(file);

			try {
				if (isBetter(cacheMap, sr.SSID, sr.BSSID, sr.level)) {
					// Save the JSON object to a file
					JSONObject jsonWLAN = new JSONObject();
					try {
						jsonWLAN.put("SSID", sr.SSID);
						jsonWLAN.put("BSSID", sr.BSSID);
						jsonWLAN.put("frequency", f);
						jsonWLAN.put("channelWidth", chanWidth);
						jsonWLAN.put("level", sr.level);
						jsonWLAN.put("loc", "TODO");
						jsonWLAN.put("dist", calculateDistance(sr.level, f));
						jsonWLAN.put("time", System.currentTimeMillis() / 1000);

					} catch (JSONException e) {
						e.printStackTrace();
					}
					File outputFile = new File(fileName);
					try (FileWriter writer = new FileWriter(outputFile, true)) {
						writer.append(jsonWLAN.toString()).append("\n");
						final String TAG = "JSON file writer";
						Log.d(TAG, "WLAN data saved to file: " + outputFile.getAbsolutePath());
					} catch (IOException e) {
						final String TAG = "JSON file writer";
						Log.e(TAG, "Cannot write to JSON.");
						e.printStackTrace();
					}
				}
			} catch (Exception e) {
				Log.e("WRITER:","ERROR.");
				throw new RuntimeException(e);
			}
		}
	}

	public String getLoc(){
		MainActivity mainActivity = new MainActivity();
		return mainActivity.getLoc();
	}


	public double calculateDistance(int signalStrength, int frequency) {
		int referenceSignalStrength = -60; // Reference signal strength at 1 meter distance
		double referenceDistance = 1.0; // Reference distance in meters
		double exponent = 2.0; // Path loss exponent

		// Calculate the path loss
		double pathLoss = (referenceSignalStrength - signalStrength) / (10.0 * exponent);

		// Calculate the distance
		double distance = Math.pow(10.0, pathLoss) * referenceDistance;

		// Adjust distance for the frequency (optional)
		double frequencyAdjustment = 27.55;
		if (frequency != 0) {
			double frequencyInMHz = frequency / 1000.0;
			distance = distance * (frequencyAdjustment / frequencyInMHz);
		}

		return distance;
	}

	@TargetApi(Build.VERSION_CODES.ECLAIR)
	private HashMap<String, Pair<String, Integer>> cachedJSON(File file) throws IOException {
		HashMap<String, Pair<String, Integer>> dataMap = new HashMap<>();

		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = reader.readLine()) != null) {
				try {
					JSONObject jsonWLAN = new JSONObject(line);
					String ssid = jsonWLAN.getString("SSID");
					String bssid = jsonWLAN.getString("BSSID");
					int oldLevel = jsonWLAN.getInt("level");
					Pair<String, Integer> bssidSignalPair = null;
					if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ECLAIR) {
						bssidSignalPair = new Pair<>(bssid, oldLevel);
					}
					dataMap.put(ssid, bssidSignalPair);
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		}
		return dataMap;
	}


	@TargetApi(Build.VERSION_CODES.ECLAIR)
	private boolean isBetter(HashMap dataMap, String SSID, String BSSID, int level) throws IOException {
		Pair<String, Integer> bssidSignalPair = (Pair<String, Integer>) dataMap.get(SSID);
		if (dataMap.isEmpty() || bssidSignalPair == null) return true;
		return (!dataMap.containsKey(SSID)) ||
				(!Objects.equals(bssidSignalPair.first, BSSID) && (bssidSignalPair.second < level))
					|| (!Objects.equals(bssidSignalPair.first, BSSID));
	}

	private String createEmptyJSONFile(Boolean add_epoch) {
		String fileName = null;
		try {
			// Define the file path
			if (add_epoch) {
				long epochTime = System.currentTimeMillis() / 1000;
				fileName = Environment.getExternalStorageDirectory() + "/" + "wlan_data" + epochTime + ".json";
			}else{
				fileName = Environment.getExternalStorageDirectory() + "/" + "wlan_data" + ".json";
			}

			File file = new File(fileName);

			// Create the directory if it doesn't exist
			File directory = file.getParentFile();
			//assert directory != null;
			if (directory != null && !directory.exists()) {
				boolean created = directory.mkdirs();
				Log.d("JSON file creator", "Directory created: " + directory.getAbsolutePath() + " : " + created);
			}

			boolean result = file.createNewFile();

			// Log a message indicating the file creation
			Log.d("JSON file creator", "New empty JSON file created: " + file.getAbsolutePath() + " : " + result);
			return fileName;
		} catch (IOException e) {
			Log.e("JSON creator ERROR:", fileName, e);
		}
		return fileName;
	}

	private WLANDiagramItem checkExistingEntry(File JSONFile, String ssid, String bssid) {
		return null;
	}


	private void createWLANDiagramItem(String SSID, String BSSID, int frequency, int channelWidth, int level) {
		WLANDiagramItem wdi = new WLANDiagramItem(SSID, BSSID, frequency, channelWidth, level);
		WLANDiagramItem cachedWLAN = checkWLANCache(wdi);

		if (cachedWLAN != null) {
			wdi.color = cachedWLAN.color;
		}
		else {
			wdi.color = Util.getRandomColor(80, 180);
			wlanCache.add(new WLANDiagramItem(wdi));
		}
		wlans.add(wdi);


		// Create a JSON object to hold the WLAN data
//		JSONObject jsonWLAN = new JSONObject();
//		try {
//			jsonWLAN.put("SSID", SSID);
//			jsonWLAN.put("BSSID", BSSID);
//			jsonWLAN.put("frequency", frequency);
//			jsonWLAN.put("channelWidth", channelWidth);
//			jsonWLAN.put("level", level);
//		} catch (JSONException e) {
//			e.printStackTrace();
//		}
//
//		// Save the JSON object to a file
//		File outputFile = new File(Environment.getExternalStorageDirectory(), "wlan_data.json");
//		try (FileWriter writer = new FileWriter(outputFile, true)) {
//			writer.append(jsonWLAN.toString()).append("\n");
//			final String TAG = "JSON file writer";
//			Log.d(TAG, "WLAN data saved to file: " + outputFile.getAbsolutePath());
//		} catch (IOException e) {
//			final String TAG = "JSON file writer";
//			Log.e(TAG, "Cannot write to JSON.");
//			e.printStackTrace();
//		}
	}
 }
