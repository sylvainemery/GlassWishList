package com.amazon.advertising.api;
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

import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

/*
 * This class shows how to make a simple authenticated ItemLookup call to the
 * Amazon Product Advertising API.
 *
 * See the README.html that came with this sample for instructions on
 * configuring and running the sample.
 */
public class ItemLookupHelper {
    /*
     * Use one of the following end-points, according to the region you are
     * interested in:
     *
     *      US: ecs.amazonaws.com
     *      CA: ecs.amazonaws.ca
     *      UK: ecs.amazonaws.co.uk
     *      DE: ecs.amazonaws.de
     *      FR: ecs.amazonaws.fr
     *      JP: ecs.amazonaws.jp
     *
     */
    private static final String ENDPOINT = "webservices.amazon.fr";


    public static Product ItemLookup(String access_key, String secret_key, String associate_tag, String ean) {
        /*
         * Set up the signed requests helper
         */

        SignedRequestsHelper helper;
        try {
            helper = SignedRequestsHelper.getInstance(ENDPOINT, access_key, secret_key);
        } catch (Exception e) {
            e.printStackTrace();
            return (null);
        }

        String requestUrl = null;

        /* Here is an example with string form, where the requests parameters have already been concatenated
         * into a query string. */

        Map<String, String> params = new HashMap<String, String>();
        params.put("AssociateTag", associate_tag);
        params.put("Service", "AWSECommerceService");
        params.put("Version", "2011-08-01");
        params.put("Operation", "ItemLookup");
        params.put("SearchIndex", "All");
        params.put("MerchantId", "Amazon");
        params.put("IdType", "EAN");
        params.put("ItemId", ean);
        params.put("Condition", "New");
        params.put("ResponseGroup", "Small,Images,OfferSummary");
        requestUrl = helper.sign(params);

        Product p = new Product(Long.parseLong(ean), fetchTitle(requestUrl), null, 0.0);
        return (p);
    }

    /*
     * Utility function to fetch the response from the service and extract the
     * title from the XML.
     */
    private static String fetchTitle(String requestUrl) {
        String title = null;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(requestUrl);
            Node titleNode = doc.getElementsByTagName("Title").item(0);
            title = titleNode.getTextContent();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return title;
    }

}