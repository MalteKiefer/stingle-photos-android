package org.stingle.photos.Auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Net.HttpsClient;

import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;

public class KeyManagement {

	public static String getApiToken(Context context){
		SharedPreferences preferences = context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, context.MODE_PRIVATE);
		return preferences.getString(StinglePhotosApplication.API_TOKEN, null);
	}

	public static void setApiToken(Context context, String token){
		SharedPreferences preferences = context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS,context. MODE_PRIVATE);
		preferences.edit().putString(StinglePhotosApplication.API_TOKEN, token).commit();
	}

	public static void removeApiToken(Context context){
		SharedPreferences preferences = context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS,context. MODE_PRIVATE);
		preferences.edit().remove(StinglePhotosApplication.API_TOKEN).commit();
	}

	public static boolean uploadKeyBundleAsync(Context context, String password, HttpsClient.OnNetworkFinish onFinish){
		HttpsClient.post(context, context.getString(R.string.api_server_url) + context.getString(R.string.upload_key_bundle_path), getUploadKeyBundlePostParams(context, password), onFinish);
		return true;
	}

	public static boolean uploadKeyBundle(Context context, String password){
		JSONObject resultJson = HttpsClient.postFunc(context.getString(R.string.api_server_url) + context.getString(R.string.upload_key_bundle_path), getUploadKeyBundlePostParams(context, password));
		StingleResponse response = new StingleResponse(context, resultJson, false);
		if(response.isStatusOk()){
			return true;
		}
		return false;
	}

	private static HashMap<String, String> getUploadKeyBundlePostParams(Context context, String password){
		HashMap<String, String> postParams = new HashMap<String, String>();

		String keyBundle;
		String publicKey;
		try {
			byte[] keyBundleBytes = StinglePhotosApplication.getCrypto().exportKeyBundle(password);
			keyBundle = Base64.encodeToString(keyBundleBytes, Base64.NO_WRAP);

			byte[] publicKeyBytes = StinglePhotosApplication.getCrypto().exportPublicKey();
			publicKey = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP);
		} catch (IOException | CryptoException e) {
			e.printStackTrace();
			return null;
		}

		postParams.put("token", getApiToken(context));
		postParams.put("keyBundle", keyBundle);
		postParams.put("publicKey", publicKey);

		return postParams;
	}

	public static boolean importKeyBundle(Context context, String keyBundle, String password){
		try {
			byte[] keyBundleBytes = Base64.decode(keyBundle, Base64.NO_WRAP);

			StinglePhotosApplication.getCrypto().importKeyBundle(keyBundleBytes, password);
		} catch (IOException | CryptoException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	public static void deleteLocalKeys(){
		StinglePhotosApplication.getCrypto().deleteKeys();
	}

}