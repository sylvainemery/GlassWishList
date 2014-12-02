package com.hackncheese.glasswishlist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;

import com.google.android.glass.content.Intents;
import com.google.android.glass.media.Sounds;
import com.google.android.glass.view.MenuUtils;
import com.google.android.glass.widget.CardBuilder;
import com.google.android.glass.widget.CardScrollAdapter;
import com.google.android.glass.widget.CardScrollView;
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

    private View mView;
    private CardScrollView mCardScroller;

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

        mCardScroller = new CardScrollView(this);
        // disabling the scrollbar to let the indeterminate scroller work
        mCardScroller.setHorizontalScrollBarEnabled(false);
        mCardScroller.setAdapter(new CardScrollAdapter() {
            @Override
            public int getCount() {
                return 1;
            }

            @Override
            public Object getItem(int position) {
                return mView;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return mView;
            }

            @Override
            public int getPosition(Object item) {
                if (mView.equals(item)) {
                    return 0;
                }
                return AdapterView.INVALID_POSITION;
            }
        });
        mView = buildView();
        setContentView(mCardScroller);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mCardScroller.activate();
    }

    @Override
    protected void onPause() {
        mCardScroller.deactivate();
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

    /**
     * onPreparePanel is called every time the menu is shown
     * Here, we change what's in the menu based on the current state of the activity:
     * - if the item has been sent to Trello already, don't show that menu option
     */
    @Override
    public boolean onPreparePanel(int featureId, View view, Menu menu) {
        if (featureId == Window.FEATURE_OPTIONS_PANEL) {
            MenuItem menuItemSaveToTrello = menu.findItem(R.id.tp_savetotrello);
            if (mSavedToTrello) {
                // already saved, disable the menu item
                menuItemSaveToTrello.setEnabled(false);
                // explain why the menu item is disabled
                MenuUtils.setDescription(menuItemSaveToTrello, getString(R.string.menu_save_to_trello_already_sent));
            } else {
                menuItemSaveToTrello.setEnabled(true);
                MenuUtils.setDescription(menuItemSaveToTrello, null);
            }
        }
        // Pass through to super to setup touch menu.
        return super.onPreparePanel(featureId, view, menu);
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
                case R.id.tp_retake:
                    // re-capture an image
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(intent, TAKE_PICTURE_REQUEST);
                    // reset the status because another picture will be taken. One should be able to upload it too.
                    mSavedToTrello = false;
                    break;
                case R.id.tp_savetotrello:
                    // we save the thumbnail to Trello
                    mSaveTrelloTask = new SavePictureToTrelloTask();
                    mSaveTrelloTask.execute(thumbnailPath);
                    break;
            }
            return true;
        }
        return super.onMenuItemSelected(featureId, item);
    }

    protected View buildView() {
        CardBuilder mCard = new CardBuilder(this, CardBuilder.Layout.CAPTION);
        if (img != null) {
            mCard.addImage(img);
        }
        if (mSavedToTrello) {
            mCard.setAttributionIcon(R.drawable.ic_cloud_done_36);
        }
        return mCard.getView();
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
                    mView = buildView();
                    mCardScroller.getAdapter().notifyDataSetChanged();
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
            mView = buildView();
            mCardScroller.getAdapter().notifyDataSetChanged();

            // play a nice sound
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.playSoundEffect(Sounds.SUCCESS);

        }
    }


}
