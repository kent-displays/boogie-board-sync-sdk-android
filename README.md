![Boogie Board Sync and Android](http://i.imgur.com/SH4bzBT.png "Boogie Board Sync and Android")

# Boogie Board Sync SDK v1.0 for Android

The software development kit provides a library for communicating with a Boogie Board Sync on Android. This library allows developers to view, modify and retrieve aspects of the file system on the Sync. It also allows developers to retrieve real-time information like current position of the stylus and a user pressing the save and erase button.

*Note: This library is to be used on Android SDK 15+ (Ice Cream Sandwich). All communication is done using Bluetooth.*

- [Installing](#installing)
- [Configuring](#configuring)
- [Structure](#structure)
- [Documentation](#documentation)
- [Limitations](#limitations)
- [Questions?](#questions)
- [License](#license)

## Installing

Note: This library was built with [Android Studio](http://developer.android.com/sdk/installing/studio.html) and is set up to go with the gradle build system. At this time there is no support for the Eclipse IDE.

#### Option 1: Download entire project
Download the entire project directory here and then just import the project into Android Studio. From there you should be able to get up and running with the included samples.

#### Option 2: Include library
To include this library in your current project first download the library folder from this project. The folder already contains the appropriate build file for gradle, but there are a few more steps to follow.

1. Add the following line to the list of dependencies listed in your project's *build.gradle* file.

	```
	compile project(':library')
	```

2. Make sure to include the new module in your *settings.gradle* file as well.
	
	```
	include ':library', ':app'
	```

3. Add the following permissions to the top level of your project's *AndroidManifest.xml* file.

	```
	<uses-permission android:name="android.permission.BLUETOOTH"/>
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
    ```

## Configuring

To allow for easy configuration there is a single java class called *Config.java*. Here you can turn on/off debugging for the entire library.

## Structure
This is a quick overview on how the entire library and API are structured for use. **Highly recommend reading this before starting.**

This library is broken up into two essential parts. On one side you have the Streaming API where you can get erase/save button pushes as well as real-time paths that are drawn on the Sync. On the other is the File Transfer API where you can delete, download files from the Sync as well as traverse the internal directory structure of the Sync.

Both of these APIs extend a single service to facilitate long running operations. If you are new to services, I would suggest reading [this Android guide](http://developer.android.com/guide/components/services.html) on services.

Typically, you would first bind to the SyncStreamingService or SyncFtpService. When you receive a callback for ```onServiceConnected()``` you will get an IBinder. From there you can cast the IBinder to the specific binder class and then retrieve reference to the actual service object.


```
private final ServiceConnection mConnection = new ServiceConnection() {
	public void onServiceConnected(ComponentName name, IBinder service) {
         mFtpServiceBound = true;
         SyncFtpService.SyncFtpBinder binder = (SyncFtpService.SyncFtpBinder) service;
         mFtpService = binder.getService();
         mFtpService.addListener(PlaceholderFragment.this);// Add listener to retrieve events from ftp service.

         if(mFtpService.getState() == SyncFtpService.STATE_CONNECTED) {
             // Connect to the ftp server.
             mFtpService.connect();
         }
     }

    public void onServiceDisconnected(ComponentName name) {
        mFtpService = null;
        mFtpServiceBound = false;
    }
};
```


## Documentation

Javadocs for this library can be found [here](#).


## Limitations
There are the following limitations still imposed upon this library.

- Can only communicate with one Boogie Board Sync at a time. This can also cause issues with having more than one Sync paired at a time.
- Running the [Boogie Board Sync app](https://play.google.com/store/apps/details?id=com.improvelectronics.sync_android), on your Android device, at the same time as this project can cause some issues. In order to correct this, make sure to shutdown all processes and services for the Boogie Board Sync app from your device's settings.

## Questions?

For questions or comments email or contact us on Twitter

- [cfullmer@kentdisplays.com](mailto:cfullmer@kentdisplays.com)
- [@camdenfullmer](http://twitter.com/camdenfullmer)

## License

Copyright © 2014 Kent Displays, Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
