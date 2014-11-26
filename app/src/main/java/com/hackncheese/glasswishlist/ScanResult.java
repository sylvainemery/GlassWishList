package com.hackncheese.glasswishlist;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * "Simple" barcode scan result
 * Contains the barcode and the symbology
 * Parcelable so it can be passed easily between activities
 */
public class ScanResult implements Parcelable {

    private String mCode;
    private String mSymbology;

    public ScanResult(String code, String symbology) {
        this.mCode = code;
        this.mSymbology = symbology;
    }

    public String getCode() {
        return mCode;
    }

    public void setCode(String code) {
        this.mCode = code;
    }

    public String getSymbology() {
        return mSymbology;
    }

    public void setSymbology(String symbology) {
        this.mSymbology = symbology;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeStringArray(new String[]{
                this.mCode,
                this.mSymbology
        });
    }

    public ScanResult(Parcel in) {
        String[] data = new String[2];
        in.readStringArray(data);
        this.mCode = data[0];
        this.mSymbology = data[1];
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<ScanResult> CREATOR = new Parcelable.Creator<ScanResult>() {
        public ScanResult createFromParcel(Parcel in) {
            return new ScanResult(in);
        }
        public ScanResult[] newArray(int size) {
            return new ScanResult[size];
        }
    };

}
