package com.hackncheese.glasswishlist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;

import com.google.android.glass.content.Intents;
import com.google.android.glass.media.Sounds;
import com.google.android.glass.widget.CardBuilder;
import com.google.android.glass.widget.Slider;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.MultipartBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Takes a picture (via the standard Glass Intent)
 * and saves the picture to a Trello card
 */
public class TakePictureActivity extends Activity {

    // for logs
    private static final String TAG = TakePictureActivity.class.getSimpleName();

    private static final int TAKE_PICTURE_REQUEST = 1;

    // THE view
    private View mView;

    // Slider used when saving to Trello
    private Slider.Indeterminate mIndSlider;

    // the path to the thumbnail file.
    // we don't use the full-res file as it's too big for our use
    private String thumbnailPath;
    // the thumbnail picture
    private Bitmap img;

    private SavePictureToTrelloTask mSaveTrelloTask;
    // will be true only when Trello acknowledged the card creation
    private boolean mSavedToTrello = false;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        // capture an image
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, TAKE_PICTURE_REQUEST);
    }

    @Override
    protected void onPause() {
        // hide the progress bar, if it was showing
        if (mIndSlider != null) {
            mIndSlider.hide();
            mIndSlider = null;
        }
        // cancel the async task if it exists
        if (mSaveTrelloTask != null) {
            mSaveTrelloTask.cancel(true); // true = force interruption
        }

        super.onPause();
    }

    @Override
    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        if (featureId == Window.FEATURE_OPTIONS_PANEL) {
            getMenuInflater().inflate(R.menu.takepicture, menu);
            return true;
        }

        // Pass through to super to setup touch menu.
        return super.onCreatePanelMenu(featureId, menu);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            openOptionsMenu();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        if (featureId == Window.FEATURE_OPTIONS_PANEL) {
            switch (item.getItemId()) {
                case R.id.retake:
                    // re-capture an image
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(intent, TAKE_PICTURE_REQUEST);
                    break;
                case R.id.savetotrello:
                    // we save the thumbnail to Trello
                    mSaveTrelloTask = new SavePictureToTrelloTask();
                    mSaveTrelloTask.execute(thumbnailPath);
                    break;
            }
            return true;
        }
        return super.onMenuItemSelected(featureId, item);
    }

    protected void buildView() {
        CardBuilder mCard = new CardBuilder(this, CardBuilder.Layout.CAPTION);
        mCard.addImage(img);
        if (mSavedToTrello) {
            mCard.setText("Saved to Trello");
            mCard.setIcon(R.drawable.ic_cloud_done_50);
        }
        mView = mCard.getView();
        setContentView(mView);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == TAKE_PICTURE_REQUEST && resultCode == RESULT_OK) {
            thumbnailPath = data.getStringExtra(Intents.EXTRA_THUMBNAIL_FILE_PATH);

            // read the thumbnail from sdcard
            File file = new File(thumbnailPath);
            img = null;
            if (file.exists()) {
                img = BitmapFactory.decodeFile(thumbnailPath);
                if (img != null) {
                    buildView();
                    openOptionsMenu();
                }
            } else {
                Log.w(TAG, String.format("image file %s not found", thumbnailPath));
            }
        } else {
            finish();
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * an AsyncTask that will create a new Trello card with a picture
     */
    private class SavePictureToTrelloTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... imgPaths) {
            String imgPath = imgPaths[0];
            OkHttpClient client = new OkHttpClient();
            String addCardURL = String.format("https://api.trello.com/1/cards?key=%1$s&token=%2$s&idList=%3$s&name=%4$s", getString(R.string.trello_api_key), getString(R.string.trello_token), getString(R.string.trello_list_id), "fromGlass");

            // don't wait more than a few seconds total
            client.setConnectTimeout(1000, TimeUnit.MILLISECONDS);
            client.setWriteTimeout(6000, TimeUnit.MILLISECONDS);
            client.setReadTimeout(6000, TimeUnit.MILLISECONDS);

            RequestBody requestBody = new MultipartBuilder()
                    .type(MultipartBuilder.FORM)
                    .addPart(
                            Headers.of("Content-Disposition", String.format("form-data; name=\"fileSource\"; filename=\"%s\"", imgPath)),
                            RequestBody.create(MediaType.parse("image/jpeg"), new File(imgPath))
                    )
                    .build();

            Request request = new Request.Builder()
                    .url(addCardURL)
                    .post(requestBody)
                    .build();

            try {
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    mSavedToTrello = true;
                    Log.d(TAG, "wrote to Trello");
                } else {
                    Log.e(TAG, String.format("Trello didn't accept the request: %s", response.message()));
                }
            } catch (IOException e) {
                Log.e(TAG, "timed out while trying to create card in Trello");
            }

            return null;
        }

        protected void onPreExecute() {
            // show progress bar
            mIndSlider = Slider.from(mView).startIndeterminate();
        }

        protected void onPostExecute(Void v) {
            // hide the progress bar
            if (mIndSlider != null) {
                mIndSlider.hide();
                mIndSlider = null;
            }
            buildView();

            // play a nice sound
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.playSoundEffect(Sounds.SUCCESS);

        }
    }


}
