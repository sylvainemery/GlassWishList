package com.hackncheese.glasswishlist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.amazon.advertising.api.ItemLookupHelper;
import com.amazon.advertising.api.Product;
import com.google.android.glass.media.Sounds;
import com.google.android.glass.widget.CardBuilder;
import com.google.android.glass.widget.CardScrollAdapter;
import com.google.android.glass.widget.CardScrollView;
import com.google.android.glass.widget.Slider;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;


public class ProductLookupActivity extends Activity {

    // for logs
    private static final String TAG = ProductLookupActivity.class.getSimpleName();

    // Activity state: looking up product info from barcode
    private boolean mIsLookingUpProduct = true;
    // Activity state: product found if not null. Not (yet) found if null
    private Product mProduct = null;

    // Task to look up the product info
    private AmazonProductLookupTask mAmazonProductLookupTask;
    // Task to add the product to a Trello list
    private AddProductToTrelloTask mAddProductToTrelloTask;
    // Task to fetch the product image
    private ImageDownloaderTask mImageDownloaderTask;

    // classic Glass card scroller
    private CardScrollView mCardScroller;
    // the view. Will change depending on activity state
    private View mView;
    // Generic slider from which the indeterminate one is constructed
    private Slider mSlider;
    // Slider shown when accessing teh intertubes
    private Slider.Indeterminate mIndSlider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // get the barcode given by the scanning activity
        Intent myIntent = getIntent();
        ScanResult scanResult = myIntent.getParcelableExtra("ScanResult");

        mIsLookingUpProduct = true;

        mCardScroller = new CardScrollView(this);
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

        //TODO : get this slider working
        mSlider = Slider.from(mCardScroller);

        // look up the product info
        mAmazonProductLookupTask = new AmazonProductLookupTask();
        mAmazonProductLookupTask.execute(scanResult);

    }

    @Override
    protected void onResume() {
        super.onResume();

        mCardScroller.activate();
    }

    @Override
    protected void onPause() {
        mCardScroller.deactivate();
        // hide the progress bar - if any
        if (mIndSlider != null) {
            mIndSlider.hide();
            mIndSlider = null;
        }
        // cancel the async task if it exists
        if (mAmazonProductLookupTask != null) {
            mAmazonProductLookupTask.cancel(true); // true = force interruption
        }
        // cancel the async task if it exists
        if (mImageDownloaderTask != null) {
            mImageDownloaderTask.cancel(true); // true = force interruption
        }
        // cancel the async task if it exists
        if (mAddProductToTrelloTask != null) {
            mAddProductToTrelloTask.cancel(true); // true = force interruption
        }

        super.onPause();
    }

    /**
     * Builds the view.
     * Depending on the activity state, it will be either:
     * - a progress indicator
     * - a product view
     * - a "not found" view
     */
    private View buildView() {
        CardBuilder card;

        if (mIsLookingUpProduct) {
            // Still looking up the product, show a simple card
            card = new CardBuilder(this, CardBuilder.Layout.MENU);
            card.setText(R.string.result_lookingup);
        } else {
            if (mProduct != null) {
                // we have a product
                card = new CardBuilder(this, CardBuilder.Layout.TEXT);

                String mainText = String.format("%s <font color='#808080'>(%.2f&nbsp;€)</font>", mProduct.getName(), mProduct.getPrice());
                card.setText(Html.fromHtml(mainText));

                card.setFootnote(String.format("%s", mProduct.getBarcode()));

                Bitmap bitmap = mProduct.getImageBitmap();
                if (bitmap != null) {
                    card.addImage(bitmap);
                }
            } else {
                // no product could be found
                card = new CardBuilder(this, CardBuilder.Layout.ALERT);
                card.setText(R.string.result_notfound)
                        .setIcon(R.drawable.ic_warning_150);
            }
        }

        return card.getView();
    }


    /**
     * Looks up a product from barcode+symbology with the Amazon Product Advertising API
     */
    private class AmazonProductLookupTask extends AsyncTask<ScanResult, Void, Product> {
        @Override
        protected Product doInBackground(ScanResult... scanResults) {
            Product product = null;

            // only work on the first (and only) scan result
            if (scanResults[0] != null) {
                product = ItemLookupHelper.ItemLookup(getString(R.string.AWS_ACCESS_KEY_ID), getString(R.string.AWS_SECRET_KEY), getString(R.string.AssociateTag), scanResults[0].getCode(), scanResults[0].getSymbology());
            }

            return product;
        }

        @Override
        protected void onPreExecute() {
            // show progress bar
            mIndSlider = mSlider.startIndeterminate();
        }

        @Override
        protected void onPostExecute(Product product) {
            // we are done looking up a product
            mIsLookingUpProduct = false;
            // hide the progress bar
            if (mIndSlider != null) {
                mIndSlider.hide();
                mIndSlider = null;
            }
            // set the instance product
            mProduct = product;
            // show the result
            mView = buildView();
            mCardScroller.getAdapter().notifyDataSetChanged();
            // play a nice sound
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (product != null) {
                am.playSoundEffect(Sounds.SUCCESS);
                // fetch the product image
                mImageDownloaderTask = new ImageDownloaderTask();
                mImageDownloaderTask.execute(product.getImageURL());
                // add the product to Trello
                mAddProductToTrelloTask = new AddProductToTrelloTask();
                mAddProductToTrelloTask.execute(product);
            } else {
                am.playSoundEffect(Sounds.ERROR);
            }
            super.onPostExecute(product);
        }
    }

    private class ImageDownloaderTask extends AsyncTask<String, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(String... urls) {
            Bitmap bitmap = null;

            // only work on the first (and only) URL
            if (urls[0] != null) {
                OkHttpClient client = new OkHttpClient();

                // don't wait more than 3 seconds total
                client.setConnectTimeout(1000, TimeUnit.MILLISECONDS);
                client.setWriteTimeout(1000, TimeUnit.MILLISECONDS);
                client.setReadTimeout(1000, TimeUnit.MILLISECONDS);

                Request request = new Request.Builder()
                        .url(urls[0])
                        .build();

                try {
                    Response response = client.newCall(request).execute();
                    InputStream input = response.body().byteStream();
                    bitmap = BitmapFactory.decodeStream(input);
                } catch (IOException e) {
                    Log.e(TAG, String.format("timed out while trying to get data from url %s", urls[0]));
                }

            }

            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            mProduct.setImageBitmap(bitmap);
            mView = buildView();
            mCardScroller.getAdapter().notifyDataSetChanged();

            super.onPostExecute(bitmap);
        }
    }

    /**
     * Add a product to a Trello list
     */
    private class AddProductToTrelloTask extends AsyncTask<Product, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Product... p) {
            OkHttpClient client = new OkHttpClient();
            String addCardURL = String.format("https://api.trello.com/1/cards?key=%1$s&token=%2$s&idList=%3$s&urlSource=%4$s", getString(R.string.trello_api_key), getString(R.string.trello_token), getString(R.string.trello_list_id), p[0].getProductURL());
            boolean result;

            // don't wait more than 8 seconds total
            client.setConnectTimeout(1000, TimeUnit.MILLISECONDS);
            client.setWriteTimeout(1000, TimeUnit.MILLISECONDS);
            client.setReadTimeout(6000, TimeUnit.MILLISECONDS);

            Request request = new Request.Builder()
                    .url(addCardURL)
                    .post(null)
                    .build();

            try {
                Response response = client.newCall(request).execute();
                result = response.isSuccessful();
            } catch (IOException e) {
                Log.e(TAG, "timed out while trying to create card in Trello");
                result = false;
            }

            return result;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            // play a nice sound
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (result) {
                am.playSoundEffect(Sounds.SUCCESS);
            } else {
                am.playSoundEffect(Sounds.ERROR);
            }
            super.onPostExecute(result);
        }
    }

}
