package com.amazon.advertising.api;

/**
 * Created by se on 2014-10-21.
 */
public class Product {
    private long mBarcode;
    private String mName;
    private String mProductURL;
    private String mImageURL;
    private double mPrice;

    public Product(long barcode, String name, String productURL, String imageURL, double price) {
        mBarcode = barcode;
        mName = name;
        mProductURL = productURL;
        mImageURL = imageURL;
        mPrice = price;
    }

    public long getBarcode() {
        return mBarcode;
    }

    public void setBarcode(long barcode) {
        this.mBarcode = barcode;
    }

    public String getName() {
        return mName;
    }

    public void setName(String mName) {
        this.mName = mName;
    }

    public double getPrice() {
        return mPrice;
    }

    public void setPrice(double Price) {
        this.mPrice = Price;
    }

    public String getProductURL() {
        return mProductURL;
    }

    public void setProductURL(String productURL) {
        this.mProductURL = productURL;
    }

    public String getImageURL() {
        return mImageURL;
    }

    public void setImageURL(String imageURL) {
        this.mImageURL = imageURL;
    }
}
