Signature SDK for Android

Version 2.2

Created by Wacom

----------------------------------------------------------------------------------------------------------------

For further details on using the SDK see https://developer-docs.wacom.com
Navigate to: WILL SDK - for signature / Signature SDK - Android

----------------------------------------------------------------------------------------------------------------

Contents:

sdk\
  signaturesdk_2.2.aar -  This is the Wacom Signature library for Android that licensees can incorporate into their own apps.
                          The library enables the capture of signature information input by a Wacom Digital pen on mobile devices and tablets running Android operating software.

sample-code\
  SignatureSDKExample  -  This is an Android Studio project containing the source code of a sample client app that interacts with the Wacom Signature App in order to demonstrate its capabilities.
  SignatureSDKExample.apk - This is a precompile version of the SignatureSDKExample source code.

documentation\
  javadoc - provides the API information that developers need in order to use the Wacom Signature library.
  Wacom Evaluation Version Signature SDK License Agreement.pdf


NOTE:
-----
The license agreements must be accepted in order to use the Wacom Signature SDK for Android.
A license key is required, detailed in developer-docs.wacom.com.

-------------------------------------------------------------------------------

Version History

2.0     02 March 2018

    -	now an Android Studio Project instead of Eclipse
    -	the SDK is now an aar library instead of a single apk.
      This implies that the user can include it in a Project without installation
    -	the JWT license format is now supported
    -	due to vulnerability issues updated to the latest version of libpng
    - now requires the library 'jose4j'
      add the following in the Android Studio Project:
      compile group: 'org.bitbucket.b_c', name: 'jose4j', version: '0.5.2'
    - Revised zip file format

2.1    22 June 2018

   - Added support for Encryption, required bouncy castle libraries for using encryption
   - Added ISO standard support

2.2    22 February  2019

   - Fixed problem when pressing spen stylus button while signing
   - Fixed problem when using bambbo stylus on touch mode
   - Fixed production licensing problems.



Copyright © 2018 Wacom, Co., Ltd. All Rights Reserved.
