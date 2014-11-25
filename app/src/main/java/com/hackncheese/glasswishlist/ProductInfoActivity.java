package com.hackncheese.glasswishlist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;

import com.amazon.advertising.api.ItemLookupHelper;
import com.amazon.advertising.api.Product;
import com.google.android.glass.media.Sounds;
import com.google.android.glass.widget.CardBuilder;
import com.google.android.glass.widget.CardScrollAdapter;
import com.google.android.glass.widget.CardScrollView;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * An {@link Activity} showing a product info card.
 * <p/>
 */
public class ProductInfoActivity extends Activity {

    // for logs
    private static final String TAG = ProductInfoActivity.class.getSimpleName();

    /**
     * {@link CardScrollView} to use as the main content view.
     */
    private CardScrollView mCardScrollView;

    private List<CardBuilder> mCards;

    private SendToTrelloTask mCreateTrelloCardTask;


    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        //dirty workaround to allow doing network IO in the main thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // get the barcode the scanning activity gave us
        Intent myIntent = getIntent();
        String code = myIntent.getStringExtra("code");
        String symbology = myIntent.getStringExtra("symbology");

        Product theProduct;

        if (code == null) {
            theProduct = null;
        } else {
            // get the product corresponding to this barcode
            theProduct = ItemLookupHelper.ItemLookup(getString(R.string.AWS_ACCESS_KEY_ID), getString(R.string.AWS_SECRET_KEY), getString(R.string.AssociateTag), code, symbology);
        }

        createCards(theProduct);

        mCardScrollView = new CardScrollView(this);
        ProductInfoCardScrollAdapter mAdapter = new ProductInfoCardScrollAdapter();
        mCardScrollView.setAdapter(mAdapter);
        mCardScrollView.activate();
        setContentView(mCardScrollView);

        // Handle the TAP event.
        mCardScrollView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Plays disallowed sound to indicate that TAP actions are not supported.
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                am.playSoundEffect(Sounds.DISALLOWED);
            }
        });


        // add the product to Trello
        mCreateTrelloCardTask = new SendToTrelloTask();
        mCreateTrelloCardTask.execute(theProduct);


        //prevent screen dimming
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCardScrollView.activate();
    }

    @Override
    protected void onPause() {
        mCardScrollView.deactivate();

        if (mCreateTrelloCardTask != null) {
            mCreateTrelloCardTask.cancel(true);
        }

        super.onPause();
    }

    private void createCards(Product p) {
        CardBuilder card;

        mCards = new ArrayList<CardBuilder>();

        // product not recognized, just show an alert card
        if (p == null) {
            card = new CardBuilder(this, CardBuilder.Layout.ALERT);
            card.setText(R.string.result_notfound)
                    .setIcon(R.drawable.ic_warning_150);
            mCards.add(card);
            return;
        }

        // main product info card
        card = new CardBuilder(this, CardBuilder.Layout.TEXT);

        String mainText = String.format("%s <font color='#808080'>(%.2f&nbsp;€)</font>", p.getName(), p.getPrice());
        card.setText(Html.fromHtml(mainText));

        card.setFootnote(String.format("%s", p.getBarcode()));

        card.addImage(bitmapFromUrl(p.getImageURL()));


        mCards.add(card);

    }


    private class ProductInfoCardScrollAdapter extends CardScrollAdapter {

        @Override
        public int getPosition(Object item) {
            return mCards.indexOf(item);
        }

        @Override
        public int getCount() {
            return mCards.size();
        }

        @Override
        public Object getItem(int position) {
            return mCards.get(position);
        }

        @Override
        public int getViewTypeCount() {
            return CardBuilder.getViewTypeCount();
        }

        @Override
        public int getItemViewType(int position) {
            return mCards.get(position).getItemViewType();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return mCards.get(position).getView(convertView, parent);
        }
    }


    private Bitmap bitmapFromUrl(String url) {
        try {
            Bitmap bitmap;
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.connect();
            InputStream input = connection.getInputStream();
            bitmap = BitmapFactory.decodeStream(input);
            return bitmap;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     *
     */
    private class SendToTrelloTask extends AsyncTask<Product, Void, Boolean> {
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

    }

}
