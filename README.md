#Android Streaming Client

##Introduction

###What it does?

**Android Streaming Client** is an Android library which main purpose is to play real <br>
time video in an Android device. <br>

###How it does it

The current version of the code only supports RTP over UDP as the transport <br>
protocol and decodes H264 encoded video. <br>
It uses [efflux library](https://github.com/brunodecarvalho/efflux) to create an underlying RTP session to listen to <br>
package arrival. <br>
It only works with video streamed from [libstreaming library](https://github.com/fyhertz/libstreaming) running in a <br>
separate Android device. It uses a custom version of libstreaming which is <br>
composed of the original libstreaming library plus a couple changes that fix <br>
particular issues encountered while working on the **Android Streaming Client** <br>
library.<br>

###Using Android Streaming Client in your application

Simply add the following dependency in your module's build.gradle file:
```
dependencies {
   compile('com.creativa77:android_streaming_client:1.0.5')
}
```
> Version number may change.

###A straightforward example

Android Streaming Client uses a [libstreaming's fork](https://github.com/ashyonline/libstreaming) to publish the video. <br>
There is also a [fork from libstreaming-examples](https://github.com/ashyonline/libstreaming-examples) that adds a fourth example <br>
to the set of 3 existing examples ([example4 folder](https://github.com/ashyonline/libstreaming-examples/tree/master/example4)). <br>
In order to test Android Streaming Client library you should follow this steps:


* Clone libstreaming-examples fork:
```
   > git clone https://github.com/ashyonline/libstreaming-examples
```

* Clone libstreaming fork:
```
   > git clone https://github.com/ashyonline/libstreaming
```

Keep in mind that, as libstreaming-examples uses libstreaming as a dependency, <br>
you may want to clone libstreaming inside libstreaming-examples folder.<br>

* Import the examples project in your favourite Android IDE.

If using Android Studio, you should import example4 as a module in a project. <br>
Android Studio will automatically create a build.gradle file for each imported <br>
module. Remember to add the libstreaming dependency to those files:<br>

```
   dependencies {
      compile project(':libstreaming')
   }
```

* Clone **this** repository:
```
   > git clone git@github.com:creativa77/AndroidStreamingClient.git
```

* Import **Android Streaming Client** project.

* Check the IP address of the *player* device and change [this line](https://github.com/ashyonline/libstreaming-examples/blob/master/example4/src/net/majorkernelpanic/example4/MainActivity.java#L25) accordingly. <br>

That way, the publisher (example4) will know where to stream the video.

* Run example4 in the *publisher* Android device.

* Run the module *example* from **Android Streaming Client** in the *player* <br>
Android device.

If everything works, you will be streaming video from one device to another <br>
in real time.

##Code snippet

How to simple use this library in your main activity.

```

   import com.c77.androidstreamingclient.lib.RtpMediaDecoder;
   
   ... 
   
   @Override
   protected void onCreate(Bundle savedInstanceState) {

      ...
   
      // create an RtpMediaCodec with the surface view where you want 
      // the video to be shown
      RtpMediaDecoder rtpMediaDecoder = new RtpMediaDecoder(surfaceView);   
      // start it
      rtpMediaDecoder.start();
      
      ...
   }
   
   @Override
   protected void onStop() {
      
      ...
      
      // release decoder when application is stopped
      rtpMediaDecoder.release();
      
      ...
      
   }
   
```

##Content of the project

This project contains an Android library which source code is located in the <br>
folder [android_streaming_client](AndroidStreamingClient/tree/master/android_streaming_client) and an Android application that uses the library. <br>
located in the folder [example](AndroidStreamingClient/tree/master/example).<br>
The [efflux folder](AndroidStreamingClient/tree/master/efflux) includes the efflux library source code. <br><br>
Since **Android Streaming Client** was created using Android Studio, you will find several gradle files <br>
that include dependencies, versions, and other project configurations.<br>
The [license_script folder](https://github.com/creativa77/AndroidStreamingClient/tree/master/license_script) includes a script to apply the license to every java <br>
file.<br>
You can also find the [LICENSE](https://github.com/creativa77/AndroidStreamingClient/blob/master/LICENCE) and [README](https://github.com/creativa77/AndroidStreamingClient/blob/master/README.md) files.<br>

##Documentation

**Android Streaming Client** library documentation is located in [doc](https://github.com/creativa77/AndroidStreamingClient/tree/master/android_streaming_client/doc), <br>
inside the [android_streaming_client](AndroidStreamingClient/tree/master/android_streaming_client) folder.

##Authors

Ayelen Chavez <ashi@creativa77.com.ar>

Julian Cerruti <jcerruti@creativa77.com.ar>

##Issues, bugs, feature requests

[Github issue tracker](https://github.com/creativa77/AndroidStreamingClient/issues/new)

##Licensing

This project uses code from [efflux library](https://github.com/brunodecarvalho/efflux) Copyright 2010 Bruno de Carvalho, <br>
licensed under the Apache License, Version 2.0.<br>
Efflux author gave us full approval to use his library. <br>

Android Streaming Client is licensed under the Apache License, Version 2.0.
