package com.fenritz.safecam;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.Toast;

import com.fenritz.safecam.util.AESCrypt;
import com.fenritz.safecam.util.AESCryptException;
import com.fenritz.safecam.util.Helpers;

public class ChangePasswordActivity extends Activity {

	private BroadcastReceiver receiver;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.change_password);
		
		findViewById(R.id.change).setOnClickListener(changeClick());
		findViewById(R.id.cancel).setOnClickListener(cancelClick());
		
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.package.ACTION_LOGOUT");
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				finish();
			}
		};
		registerReceiver(receiver, intentFilter);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(receiver);
	}
	
	private OnClickListener changeClick() {
		return new OnClickListener() {

			public void onClick(View v) {
				String currentPassword = ((EditText)findViewById(R.id.current_password)).getText().toString();
				
				SharedPreferences preferences = getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, MODE_PRIVATE);
				String savedHash = preferences.getString(SafeCameraActivity.PASSWORD, "");
				
				try {
					String enteredPasswordHash = AESCrypt.byteToHex(AESCrypt.getHash(AESCrypt.byteToHex(AESCrypt.getHash(currentPassword)) + currentPassword));
					if (!enteredPasswordHash.equals(savedHash)) {
						Helpers.showAlertDialog(ChangePasswordActivity.this, getString(R.string.incorrect_password));
						return;
					}
					
					String newPassword = ((EditText)findViewById(R.id.new_password)).getText().toString();
					String confirm_password = ((EditText)findViewById(R.id.confirm_password)).getText().toString();
					
					if(!newPassword.equals(confirm_password)){
						Helpers.showAlertDialog(ChangePasswordActivity.this, getString(R.string.password_not_match));
						return;
					}
					
					if(newPassword.length() < Integer.valueOf(getString(R.string.min_pass_length))){
						Helpers.showAlertDialog(ChangePasswordActivity.this, String.format(getString(R.string.password_short), getString(R.string.min_pass_length)));
						return;
					}
						
					new ReEncryptFiles().execute(newPassword);
				}
				catch (AESCryptException e) {
					e.printStackTrace();
				}
			}
		};
	}
	
	private OnClickListener cancelClick() {
		return new OnClickListener() {

			public void onClick(View v) {
				ChangePasswordActivity.this.finish();
			}
		};
	}
	
	private class ReEncryptFiles extends AsyncTask<String, Integer, Void> {

		private ProgressDialog progressDialog;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog = new ProgressDialog(ChangePasswordActivity.this);
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					ReEncryptFiles.this.cancel(false);
				}
			});
			progressDialog.setMessage(getString(R.string.changing_password));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.show();
		}

		private ArrayList<File> getFilesList(String path){
			File dir = new File(path);
			File[] folderFiles = dir.listFiles();

			Arrays.sort(folderFiles);

			ArrayList<File> files = new ArrayList<File>();
			for (File file : folderFiles) {
				if (file.isFile() && file.getName().endsWith(getString(R.string.file_extension))) {
					files.add(file);
					
					String thumbPath = Helpers.getThumbsDir(ChangePasswordActivity.this) + "/" + file.getName();
					File thumb = new File(thumbPath);
					if(thumb.exists() && thumb.isFile()){
						files.add(thumb);
					}
				}
				else if(file.isDirectory() && !file.getName().startsWith(".")){
					files.addAll(getFilesList(file.getPath()));
				}
			}
			
			return files;
		}
		
		@Override
		protected Void doInBackground(String... params) {
			String newPassword = params[0];
			try {
				String passwordHash = AESCrypt.byteToHex(AESCrypt.getHash(newPassword));
	
				AESCrypt newCrypt = Helpers.getAESCrypt(passwordHash, ChangePasswordActivity.this);
				
				ArrayList<File> files = getFilesList(Helpers.getHomeDir(ChangePasswordActivity.this));
				
				progressDialog.setMax(files.size());
				
				int counter = 0;
				for(File file : files){
					try {
						FileInputStream inputStream = new FileInputStream(file);
						
						String origFilePath = file.getPath();
						String tmpFilePath = origFilePath + ".tmp";
						
						File tmpFile = new File(tmpFilePath);
						FileOutputStream outputStream = new FileOutputStream(tmpFile);
						
						Helpers.getAESCrypt(ChangePasswordActivity.this).reEncrypt(inputStream, outputStream, newCrypt, null, this);
						
						file.delete();
						tmpFile.renameTo(new File(origFilePath));
						
						publishProgress(++counter);
					}
					catch (FileNotFoundException e) {
						e.printStackTrace();
					}
				}
				
				SharedPreferences preferences = getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, MODE_PRIVATE);
				String loginHash = AESCrypt.byteToHex(AESCrypt.getHash(passwordHash + newPassword));
				preferences.edit().putString(SafeCameraActivity.PASSWORD, loginHash).commit();
				
				((SafeCameraApplication) ChangePasswordActivity.this.getApplication()).setKey(passwordHash);
			}
			catch (AESCryptException e1) {
				e1.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);

			progressDialog.setProgress(values[0]);
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			progressDialog.dismiss();
			Toast.makeText(ChangePasswordActivity.this, getString(R.string.success_change_pass), Toast.LENGTH_LONG).show();
			
			ChangePasswordActivity.this.finish();
		}

	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		Helpers.setLockedTime(this);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		Helpers.checkLoginedState(this);
		Helpers.disableLockTimer(this);
	}
}