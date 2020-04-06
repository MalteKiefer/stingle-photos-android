package org.stingle.photos.AsyncTasks.Gallery;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class DeleteAlbumAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private WeakReference<Context> context;
	private final OnAsyncTaskFinish onFinishListener;
	private Crypto crypto;

	private String albumId = null;
	private AlbumsDb albumsDb;


	public DeleteAlbumAsyncTask(Context context, String albumId, OnAsyncTaskFinish onFinishListener) {
		this.context = new WeakReference<>(context);;
		this.albumId = albumId;;
		this.onFinishListener = onFinishListener;
		this.crypto = StinglePhotosApplication.getCrypto();
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		Context myContext = context.get();
		if(albumId == null){
			return false;
		}

		albumsDb = new AlbumsDb(myContext);

		AlbumFilesDb albumFilesDb = new AlbumFilesDb(myContext);
		ArrayList<StingleDbFile> files = new ArrayList<>();

		Cursor result = albumFilesDb.getFilesList(FilesDb.GET_MODE_ALL, StingleDb.SORT_ASC, null, albumId);
		while(result.moveToNext()) {
			files.add(new StingleDbFile(result));
		}
		albumFilesDb.close();

		boolean moveResult = SyncManager.moveFiles(myContext, files, SyncManager.FOLDER_ALBUM, SyncManager.FOLDER_TRASH, albumId, null, true);

		if(moveResult){
			if(SyncManager.notifyCloudAboutAlbumDelete(context.get(), albumId)){
				albumsDb.deleteAlbum(albumId);
				albumsDb.close();
				return true;
			}
		}

		albumsDb.close();
		return false;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		if(result){
			onFinishListener.onFinish();
		}
		else{
			onFinishListener.onFail();
		}

	}
}