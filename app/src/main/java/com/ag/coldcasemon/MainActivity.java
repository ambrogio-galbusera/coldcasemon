/*
 * Copyright (c) 2011-2012 Jeff Boody
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package com.ag.coldcasemon;

import android.app.Activity;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.content.Intent;
import android.util.Log;
import android.os.Looper;
import android.os.Handler;
import android.os.Message;
import java.util.Set;
import java.util.ArrayList;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;

import org.w3c.dom.Text;

public class MainActivity extends AppCompatActivity implements Runnable, Handler.Callback, OnItemSelectedListener, View.OnTouchListener, SeekBar.OnSeekBarChangeListener
{
	private static final String TAG = "ColdCase";
	private static final int COMMAND_NONE = 0;
	private static final int COMMAND_DOOR_OPEN = 1;
	private static final int COMMAND_DOOR_CLOSE = 2;

	private static final int APPSTATUS_RUNNING = 0;

	private static final String[] APP_STATUSES = {
		"Running"
	};

	// app state
	private BlueSmirfSPP      mSPP;
	private boolean           mIsThreadRunning;
	private String            mBluetoothAddress;
	private ArrayList<String> mArrayListBluetoothAddress;

	// UI
	private TextView     mTextViewStatus;
	private TextView     mTextViewAppStatus;
	private TextView     mTextPosition;
	private TextView     mTextLengths;
	private TextView     mTextPWMs;
	private Spinner      mSpinnerDevices;
	private SeekBar		 mSeekSetpoint;
	private ArrayAdapter mArrayAdapterDevices;
	private Handler      mHandler;

	// Arduino state
	public boolean mManual;
	public int mCommand;
	public int mCommandCntr;
	public int mTemperature;
	public int mTempSetpoint;
	public int mHumidity;
	public boolean mFanStatus;
	public boolean mFanCommand;
	public boolean mCellStatus;
	public boolean mCellCommand;
	public boolean mMainsStatus;
	public boolean mMainsCommand;
	public int mAppStatus;

	public MainActivity()
	{
		mIsThreadRunning           = false;
		mBluetoothAddress          = null;
		mSPP                       = new BlueSmirfSPP();
		mManual = false;
		mCommand = COMMAND_NONE;
		mCommandCntr = 0;
		mFanStatus = false;
		mFanCommand = false;
		mCellStatus = false;
		mCellCommand = false;
		mMainsStatus = false;
		mMainsCommand = false;
		mTempSetpoint = 50;

		mArrayListBluetoothAddress = new ArrayList<String>();
	}

	BiometricListener listener = new BiometricListener() {
		@Override
		public void onSuccess() {
			Toast.makeText(MainActivity.this, "User authentication successful",Toast.LENGTH_LONG).show();
			mCommand = COMMAND_DOOR_OPEN;
			mCommandCntr = 0;
		}

		@Override
		public void onFailed() {
			Toast.makeText(MainActivity.this, "User authentication FAILED!",Toast.LENGTH_LONG).show();
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// initialize UI
		setContentView(R.layout.activity_main);
		mTextViewStatus = (TextView) findViewById(R.id.status);
		mTextViewAppStatus = (TextView) findViewById(R.id.appStatus);

		ArrayList<String> items = new ArrayList<String>();
		mSpinnerDevices         = (Spinner) findViewById(R.id.pairedDevices);
		mArrayAdapterDevices    = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items);
		mHandler                = new Handler(this);
		mSpinnerDevices.setOnItemSelectedListener(this);
		mArrayAdapterDevices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mSpinnerDevices.setAdapter(mArrayAdapterDevices);

		findViewById(R.id.doorOpen).setOnTouchListener(this);
		findViewById(R.id.doorClose).setOnTouchListener(this);

		mSeekSetpoint = (SeekBar)findViewById(R.id.temperatureSetpoint);
		mSeekSetpoint.setOnSeekBarChangeListener(this);
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		// update the paired device(s)
		BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		Set<BluetoothDevice> devices = adapter.getBondedDevices();
		mArrayAdapterDevices.clear();
		mArrayListBluetoothAddress.clear();
		if(devices.size() > 0)
		{
			for(BluetoothDevice device : devices)
			{
				mArrayAdapterDevices.add(device.getName());
				mArrayListBluetoothAddress.add(device.getAddress());
			}

			// request that the user selects a device
			if(mBluetoothAddress == null)
			{
				//mSpinnerDevices.performClick();
			}
		}
		else
		{
			mBluetoothAddress = null;
		}

		UpdateUI();
	}

	@Override
	protected void onPause()
	{
		mSPP.disconnect();
		super.onPause();
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
	}

	@Override
	public boolean onTouch(View view, MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_UP)
		{
			int tmp = Integer.parseInt(view.getTag().toString());
			if (tmp == COMMAND_DOOR_OPEN)
			{
				//perform biometric check
				new BiometricCheck(MainActivity.this, listener);
			}
			else
			{
				mCommand = tmp;
				mCommandCntr = 0;
			}
		}

		return false;
	}

	/*
	 * Spinner callback
	 */

	public void onItemSelected(AdapterView<?> parent, View view, int pos, long id)
	{
		mBluetoothAddress = mArrayListBluetoothAddress.get(pos);
	}

	public void onNothingSelected(AdapterView<?> parent)
	{
		mBluetoothAddress = null;
	}

	/*
	 * buttons
	 */

	public void onBluetoothSettings(View view)
	{
		Intent i = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
		startActivity(i);
	}

	public void onConnectClick(View view)
	{
		if(mIsThreadRunning == false)
		{
			mIsThreadRunning = true;
			UpdateUI();
			Thread t = new Thread(this);
			t.start();
		}
	}

	public void onDisconnectClick(View view)
	{
		mSPP.disconnect();
	}

	public void onManualClick(View view) {
		ToggleButton tb = (ToggleButton)view;
		mManual = tb.isChecked();
	}

	public void onMainsClick(View view) {
		if (mManual) {
			ToggleButton tb = (ToggleButton) view;
			mMainsCommand = tb.isChecked();
		}
	}

	public void onFanClick(View view) {
		if (mManual) {
			ToggleButton tb = (ToggleButton) view;
			mFanCommand = tb.isChecked();
		}
	}

	public void onCellClick(View view) {
		if (mManual) {
			ToggleButton tb = (ToggleButton) view;
			mCellCommand = tb.isChecked();
		}
	}

	private boolean appIsRunning()
	{
		return (mAppStatus == APPSTATUS_RUNNING);

	}
	/*
	 * main loop
	 */

	public void run()
	{
		Looper.prepare();
		mSPP.connect(mBluetoothAddress);
		while(mSPP.isConnected())
		{
			int outputs = 0x00;
			if (mFanCommand) outputs |= 0x01;
			if (mCellCommand) outputs |= 0x02;
			if (mMainsCommand) outputs |= 0x04;

			mSPP.writeByte('$');
			mSPP.writeByte(mManual? 1 : 0);
			mSPP.writeByte(mCommand);
			mSPP.writeByte(mTempSetpoint >> 8);
			mSPP.writeByte(mTempSetpoint & 0xFF);
			mSPP.writeByte(outputs);
			mSPP.writeByte('#');
			mSPP.flush();

			int stx = mSPP.readByte();
			mAppStatus = mSPP.readByte();
			if (mAppStatus != 0)
				mAppStatus = 0;

			int tempH = mSPP.readByte();
			int tempL = mSPP.readByte();
			mTemperature = (tempH << 8) | tempL;
			mHumidity = mSPP.readByte();

			outputs = mSPP.readByte();
			mFanStatus = ((outputs & 0x01) != 0);
			mCellStatus = ((outputs & 0x02) != 0);
			mMainsStatus = ((outputs & 0x04) != 0);

			if (!mManual)
			{
				mFanCommand = mFanStatus;
				mCellCommand = mCellStatus;
				mMainsCommand = mMainsStatus;
			}

			if (mCommand != COMMAND_NONE)
			{
				mCommandCntr++;
				if (mCommandCntr > 3)
				{
					mCommand = COMMAND_NONE;
					mCommandCntr = 0;
				}
			}

			int etx = mSPP.readByte();

			/*
			if ((mCommand == COMMAND_START) && appIsRunning())
				mCommand = COMMAND_NONE;
			*/

			if(mSPP.isError())
			{
				mSPP.disconnect();
			}

			mHandler.sendEmptyMessage(0);

			// wait briefly before sending the next packet
			try { Thread.sleep((long) (1000.0F/30.0F)); }
			catch(InterruptedException e) { Log.e(TAG, e.getMessage());}
		}

		mFanStatus = false;
		mCellStatus = false;
		mMainsStatus = false;

		mIsThreadRunning = false;
		mHandler.sendEmptyMessage(0);
	}

	/*
	 * update UI
	 */

	public boolean handleMessage (Message msg)
	{
		// received update request from Bluetooth IO thread
		UpdateUI();
		return true;
	}

	private void UpdateUI()
	{
		if(mSPP.isConnected())
		{
			mTextViewStatus.setText("connected to " + mSPP.getBluetoothAddress());
			mTextViewAppStatus.setText(APP_STATUSES[mAppStatus]);

			ToggleButton mainsButton = (ToggleButton)findViewById(R.id.mainsCommand);
			mainsButton.setChecked(mMainsStatus);

			ToggleButton fanButton = (ToggleButton)findViewById(R.id.fanCommand);
			fanButton.setChecked(mFanStatus);

			ToggleButton cellButton = (ToggleButton)findViewById(R.id.cellCommand);
			cellButton.setChecked(mCellStatus);

			TextView temperature = (TextView) findViewById(R.id.temperature);
			temperature.setText(String.format("%.1f °C", mTemperature/10.0));

			TextView humidity = (TextView) findViewById(R.id.humidity);
			humidity.setText(String.format("%d %%", mHumidity));
		}
		else if(mIsThreadRunning)
		{
			mTextViewStatus.setText("connecting to " + mBluetoothAddress);
		}
		else
		{
			mTextViewStatus.setText("disconnected");

			TextView temperature = (TextView) findViewById(R.id.temperature);
			temperature.setText("--.- °C");

			TextView humidity = (TextView) findViewById(R.id.humidity);
			humidity.setText("-- %");
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		mTempSetpoint = (int)(progress + 50);

		TextView label = (TextView)findViewById(R.id.temperatureSetpointValue);
		label.setText(String.format("%.1f °C", mTempSetpoint/10.0));
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {

	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {

	}
}
