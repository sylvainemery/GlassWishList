/**********************************************************************************************
 * Copyright 2009 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file 
 * except in compliance with the License. A copy of the License is located at
 *
 *       http://aws.amazon.com/apache2.0/
 *
 * or in the "LICENSE.txt" file accompanying this file. This file is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under the License. 
 *
 * ********************************************************************************************
 *
 *  Amazon Product Advertising API
 *  Signed Requests Sample Code
 *
 *  API Version: 2009-03-31
 *
 */

package com.amazon.advertising.api;

import java.io.UnsupportedEncodingException;

import java.net.URLEncoder;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.Calendar;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import android.util.Base64;

/**
 * This class contains all the logic for signing requests
 * to the Amazon Product Advertising API.
 */
public class SignedRequestsHelper {
    /**
     * All strings are handled as UTF-8
     */
    private static final String UTF8_CHARSET = "UTF-8";

    /**
     * The HMAC algorithm required by Amazon
     */
    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

    /**
     * This is the URI for the service, don't change unless you really know
     * what you're doing.
     */
    private static final String REQUEST_URI = "/onca/xml";

    /**
     * The sample uses HTTP GET to fetch the response. If you changed the sample
     * to use HTTP POST instead, change the value below to POST.
     */
    private static final String REQUEST_METHOD = "GET";

    private String endpoint = null;
    private String awsAccessKeyId = null;
    private String awsSecretKey = null;
    private String associateTag = null;

    private SecretKeySpec secretKeySpec = null;
    private Mac mac = null;

    /**
     * You must provide the three values below to initialize the helper.
     *
     * @param endpoint       Destination for the requests.
     * @param awsAccessKeyId Your AWS Access Key ID
     * @param awsSecretKey   Your AWS Secret Key
     * @param associateTag   Your Amazon Associate Tag (https://affiliate-program.amazon.com/)
     */
    public static SignedRequestsHelper getInstance(
            String endpoint,
            String awsAccessKeyId,
            String awsSecretKey,
            String associateTag
    ) throws IllegalArgumentException, UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException {

        if (null == endpoint || endpoint.length() == 0) {
            throw new IllegalArgumentException("endpoint is null or empty");
        }
        if (null == awsAccessKeyId || awsAccessKeyId.length() == 0) {
            throw new IllegalArgumentException("awsAccessKeyId is null or empty");
        }
        if (null == awsSecretKey || awsSecretKey.length() == 0) {
            throw new IllegalArgumentException("awsSecretKey is null or empty");
        }
        if (null == associateTag || associateTag.length() == 0) {
            throw new IllegalArgumentException("associateTag is null or empty");
        }

        SignedRequestsHelper instance = new SignedRequestsHelper();
        instance.endpoint = endpoint.toLowerCase();
        instance.awsAccessKeyId = awsAccessKeyId;
        instance.awsSecretKey = awsSecretKey;
        instance.associateTag = associateTag;

        byte[] secretKeyBytes = instance.awsSecretKey.getBytes(UTF8_CHARSET);
        instance.secretKeySpec = new SecretKeySpec(secretKeyBytes, HMAC_SHA256_ALGORITHM);
        instance.mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
        instance.mac.init(instance.secretKeySpec);

        return instance;
    }

    /**
     * The construct is private since we'd rather use getInstance()
     */
    private SignedRequestsHelper() {
    }

    /**
     * This method signs requests in hashmap form. It returns a URL that should
     * be used to fetch the response. The URL returned should not be modified in
     * any way, doing so will invalidate the signature and Amazon will reject
     * the request.
     */
    public String sign(Map<String, String> params) {
        // Let's add the AWSAccessKeyId and Timestamp parameters to the request.
        params.put("AWSAccessKeyId", this.awsAccessKeyId);
        params.put("AssociateTag", this.associateTag);
        params.put("Timestamp", this.timestamp());

        // The parameters need to be processed in lexicographical order, so we'll
        // use a TreeMap implementation for that.
        SortedMap<String, String> sortedParamMap = new TreeMap<String, String>(params);

        // get the canonical form the query string
        String canonicalQS = this.canonicalize(sortedParamMap);

        // create the string upon which the signature is calculated 
        String toSign =
                REQUEST_METHOD + "\n"
                        + this.endpoint + "\n"
                        + REQUEST_URI + "\n"
                        + canonicalQS;

        // get the signature
        String hmac = this.hmac(toSign);
        String sig = this.percentEncodeRfc3986(hmac);

        // construct the URL
        String url = "http://" + this.endpoint + REQUEST_URI + "?" + canonicalQS + "&Signature=" + sig;

        return url;
    }


    /**
     * Compute the HMAC.
     *
     * @param stringToSign String to compute the HMAC over.
     * @return base64-encoded hmac value.
     */
    private String hmac(String stringToSign) {
        String signature;
        byte[] data;
        byte[] rawHmac;
        try {
            data = stringToSign.getBytes(UTF8_CHARSET);
            rawHmac = mac.doFinal(data);
            signature = Base64.encodeToString(rawHmac, Base64.DEFAULT);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(UTF8_CHARSET + " is unsupported!", e);
        }
        // bug? it seems that an extra \n is added at the end of the signature
        // if it exists, we strip it, as the signature made by Amazon doesn't include it
        if (signature.endsWith("\n")) {
            signature = signature.substring(0, signature.length()-1);
        }

        return signature;
    }

    /**
     * Generate a ISO-8601 format timestamp as required by Amazon.
     *
     * @return ISO-8601 format timestamp.
     */
    private String timestamp() {
        String timestamp;
        Calendar cal = Calendar.getInstance();
        DateFormat dfm = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        dfm.setTimeZone(TimeZone.getTimeZone("GMT"));
        timestamp = dfm.format(cal.getTime());
        return timestamp;
    }

    /**
     * Canonicalize the query string as required by Amazon.
     *
     * @param sortedParamMap Parameter name-value pairs in lexicographical order.
     * @return Canonical form of query string.
     */
    private String canonicalize(SortedMap<String, String> sortedParamMap) {
        if (sortedParamMap.isEmpty()) {
            return "";
        }

        StringBuilder buffer = new StringBuilder();
        Iterator<Map.Entry<String, String>> iter = sortedParamMap.entrySet().iterator();

        while (iter.hasNext()) {
            Map.Entry<String, String> kvpair = iter.next();
            buffer.append(percentEncodeRfc3986(kvpair.getKey()));
            buffer.append("=");
            buffer.append(percentEncodeRfc3986(kvpair.getValue()));
            if (iter.hasNext()) {
                buffer.append("&");
            }
        }

        return buffer.toString();
    }

    /**
     * Percent-encode values according the RFC 3986. The built-in Java
     * URLEncoder does not encode according to the RFC, so we make the
     * extra replacements.
     *
     * @param s decoded string
     * @return encoded string per RFC 3986
     */
    private String percentEncodeRfc3986(String s) {
        String out;
        try {
            out = URLEncoder.encode(s, UTF8_CHARSET)
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (UnsupportedEncodingException e) {
            out = s;
        }
        return out;
    }

}