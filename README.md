GlassWishList
=============

What it is
----------

This app allows you to scan a barcode (EAN/UPC).
It is then looked up on the Amazon API to get some info (mainly name, pictures and price).
You can save the product to a Trello card in a predefined list.
If the barcode is not recognized by Amazon, you have the option to take a picture of the product and save it to Trello.


History
-------

The idea of this app came to me when showrooming with my daughter to create her Christmas wishlist.
I used the amazon app to scan the products on my phone and could add them to an amazon wishlist. But I couldn't save an unrecognized product/barcode to this wishlist.

So I thought that [Trello](https://trello.com) would make a great wishlist: easily shareable and I could add whatever I wanted to the list. I could comment on items, put labels to know who bought what, etc.


What's next
-----------

I'm not sure this kind of app would be a good candidate for an official Glassware, but some things are missing in order to be one.

- First, the credentials (Scandit for scanning, AWS for the product lookup and Trello for saving) are written in config files. So to be able to run this app, you have to modify these config files, compile the project and install the app via adb. Not very end-user friendly, for sure!
- Second, the app presumes you have connectivity. No offline fallback has been implemented.
- And probably many other things I will surface when using the app extensively...

A plain Android (= not Glass-specific) version of the app will probably come to life some day!
