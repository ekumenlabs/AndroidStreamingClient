#Android Streaming Client

##Introduction

###What it does?

**Android Streaming Client** is a library to play real time video in an Android device. <br>

###How does it work

The current version of the code only supports RTP over UDP as the transport protocol and only decodes H264 encoded video. <br>
**Android Streaming Client** uses [efflux library](https://github.com/brunodecarvalho/efflux) to create an underlying RTP session and listen to packages. <br>

It includes two different approaches to handle the package arrival:
</p>

* *Min-delay*, which uses an RTP buffer that sends packets upstream for 
processing immediately. Each packet will be sent upstream only if it is the one 
being expected, hence this approach will work as long as the packages arrive in 
order. If a received packet is newer than the one being expected, it will be 
stored in order. Also, If stored packages are older than the configured threshold, 
they will be discarded.
</p>
* *Time-window*, which uses an RTP buffer that keeps packets for a fixed amount 
of time and moves forward at a fixed rate. The received packets are pushed 
upstream in the same order and at a fixed rate. This approach uses two threads, 
one for storing the packets that arrive to the client and another to consume 
them with some wisdom.

##How do I use it

* Add the following dependency in your module's build.gradle file:

```
dependencies {
   compile('com.creativa77:android_streaming_client:1.0.5')
}
```
> Version number may change.

* Import the library in your main activity

```

   import com.c77.androidstreamingclient.lib.RtpMediaDecoder;
   
```

* On onCreate method, create a `Decoder` and start it

```   
   @Override
   protected void onCreate(Bundle savedInstanceState) {

      ...
   
      // create an RtpMediaDecoder with the surface view where you want 
      // the video to be shown
      RtpMediaDecoder rtpMediaDecoder = new RtpMediaDecoder(surfaceView);   
      // start it
      rtpMediaDecoder.start();
      
      ...
   }
   
```

* Remember to release the Decoder when onStop is called.

```
   @Override
   protected void onStop() {
      
      ...
      
      // release decoder when application is stopped
      rtpMediaDecoder.release();
      
      ...
      
   }
   
```

##Video Publishers

###Libstreaming

Android Streaming Client can play video streamed by an Android library called 
**libstreaming**. To give it a try, you can use the repositories used while 
developing **Android Streaming Client** library. <br>

Follow this steps:

* Clone libstreaming-examples fork:

```
   > git clone https://github.com/ashyonline/libstreaming-examples
```

</p>

* Clone libstreaming inside libstreaming-examples's folder:

```
   > git clone https://github.com/ashyonline/libstreaming
```

</p>

* Create an empty Android Studio project.

</p>
* Import [libstreaming](https://github.com/ashyonline/libstreaming) library in Android Studio as a module.

</p>

* Import [example4](https://github.com/ashyonline/libstreaming-examples/tree/master/example4) project as a module in Android Studio and add the libstreaming dependency to its build.gradle file:<br>

```
   dependencies {
      compile project(':libstreaming')
   }
```

</p>

* Clone **this** repository:

```
   > git clone git@github.com:creativa77/AndroidStreamingClient.git
```

</p>

* Import [example](AndroidStreamingClient/tree/master/example) as a module in Android Studio.

</p>
* Check the IP address of the *player* device and change [this line](https://github.com/ashyonline/libstreaming-examples/blob/master/example4/src/net/majorkernelpanic/example4/MainActivity.java#L25) accordingly, so that the publisher knows where to stream the video to. 
</p>

* Run example4 in the *publisher* Android device.

</p>

* Run the *example* module from **Android Streaming Client** in the *player* Android device.
</p>

If everything works, you will be streaming video from one device to another in real time.

##Other video publishers

Be sure to point your video *publisher* to the device's IP where you are playing 
video.

###Disclamer

So far, **Android Streaming Client** was tested with video streamed from 
[libstreaming library](https://github.com/fyhertz/libstreaming) running in a 
separate Android device. It uses a custom version of libstreaming which is 
composed of the original libstreaming library plus a couple changes that fix 
particular issues encountered while working on the **Android Streaming Client** 
library.<br>

##Content of the project

This project contains an Android library which source code is located in the 
folder [android_streaming_client](AndroidStreamingClient/tree/master/android_streaming_client) and an Android application that uses the library 
located in the folder [example](AndroidStreamingClient/tree/master/example). The [efflux folder](AndroidStreamingClient/tree/master/efflux) includes the efflux library
source code. <br><br>
Since **Android Streaming Client** was created using Android Studio, you will find 
several gradle files that include dependencies, versions, and other project 
configurations. The [license_script folder](https://github.com/creativa77/AndroidStreamingClient/tree/master/license_script) includes a script to apply the license 
to every java file. You can also find the [LICENSE](https://github.com/creativa77/AndroidStreamingClient/blob/master/LICENCE) and [README](https://github.com/creativa77/AndroidStreamingClient/blob/master/README.md) files.

##Documentation

**Android Streaming Client** library documentation is located in [doc](https://github.com/creativa77/AndroidStreamingClient/tree/master/android_streaming_client/doc), 
inside the [android_streaming_client](AndroidStreamingClient/tree/master/android_streaming_client) folder.

##Authors

Ayelen Chavez <ashi@creativa77.com.ar>

Julian Cerruti <jcerruti@creativa77.com.ar>

##Issues, bugs, feature requests

[Github issue tracker](https://github.com/creativa77/AndroidStreamingClient/issues/new)

##Licensing

This project uses code from [efflux library](https://github.com/brunodecarvalho/efflux) Copyright 2010 Bruno de Carvalho, 
licensed under the Apache License, Version 2.0. Efflux author gave us full approval to use his library. <br>

Android Streaming Client is licensed under the Apache License, Version 2.0.
