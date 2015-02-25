#Android Streaming Client

##About

Android Streaming Client is an Android library which main purpose is to play real <br>
time video in an Android device. <br>

The current version of the code only supports RTP over UDP as the transport <br>
protocol and decodes H264 encoded video. <br>
It currently only works with video streamed from [libstreaming library](https://github.com/fyhertz/libstreaming) <br>
running in a separate Android device. We are using a custom version of <br>
libstreaming which is the original library plus some minimal changes made to <br>
fix some issues we encountered while we were working on the Android Streaming <br>
Client library.<br>

##Content of the project

This project contains an Android library which source code is located in the <br>
folder [android_streaming_client](AndroidStreamingClient/tree/master/android_streaming_client) and an Android module that uses the library. <br>
This module is a simple example of how to use the library and it is located in <br>
the folder [example](AndroidStreamingClient/tree/master/example).<br>
You may notice there is a folder called [efflux] (AndroidStreamingClient/tree/master/efflux) which is a third party library <br>
we use to receive RTP data packets from the nework. <br>
You will also find several gradle files, as this is an Android Studio project <br>
and for Android Studio users it is nice to have those files to avoid fighting <br>
with dependencies, versions, and other project configurations.<br>
The [license_script folder](https://github.com/creativa77/AndroidStreamingClient/tree/master/license_script) includes a script to apply the license to every java <br>
file along with the license text itself.<br>
You can also find the [LICENSE](https://github.com/creativa77/AndroidStreamingClient/blob/master/LICENCE) and [README](https://github.com/creativa77/AndroidStreamingClient/blob/master/README.md) in the root folder of the project.<br>

###Documentation

You can find the AndroidStreamingClient library documentation in the [doc](https://github.com/creativa77/AndroidStreamingClient/tree/master/android_streaming_client/doc) <br>
folder located inside the [android_streaming_client](AndroidStreamingClient/tree/master/android_streaming_client) folder.

##How to use it

Android Streaming Client uses a [libstreaming library for Android's fork](https://github.com/ashyonline/libstreaming) <br>
to stream video. <br>
There is also a [fork from libstreaming-examples](https://github.com/ashyonline/libstreaming-examples) that adds a fourth example <br>
(look for example4 folder) to the 3 existing ones. <br>
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

* Import the examples project in your favorite Android IDE.

If using Android Studio, you should import each example (example1, example2, <br>
and so on) as a module inside an already existing project. Android Studio will <br>
automatically create a build.gradle file for each imported module. Remember to <br>
add the libstreaming dependency to those files.

Add the following line at the end of the corresponding build.gradle file:
```
   dependencies {
      compile project(':libstreaming')
   }
```

* Clone this repository:
```
   > git clone git@github.com:creativa77/AndroidStreamingClient.git
```

* Import Android Streaming Client project in your favorite Android IDE, also.

* Check the ip address of the client device (B) and change [this line](https://github.com/ashyonline/libstreaming-examples/blob/master/example4/src/net/majorkernelpanic/example4/MainActivity.java#L25) accordingly. <br>

That way, the publisher (example4) will know where to stream the video.

* Run example4 in the server Android device (A).

* Run the module example from AndroidStreamingClient repository in the client <br>
Android device (B).

If everything works, you will be streaming video from one device (A) to another<br>
(B) in real time.

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

##Gradle dependency

Add the following line at the end of your module's build.gradle file:
```
dependencies {
   compile('com.creativa77:android_streaming_client:1.0.0')
}
```
> Version number may change.

##Authors

Ayelen Chavez <ashi@creativa77.com.ar>

Julian Cerruti <jcerruti@creativa77.com.ar>

##Issues, bugs, feature requests

[Github issue tracker](https://github.com/creativa77/AndroidStreamingClient/issues/new)

##License

This project uses code from [efflux library](https://github.com/brunodecarvalho/efflux) Copyright 2010 Bruno de Carvalho, <br>
licensed under the Apache License, Version 2.0.<br>
Efflux author gave us full approval to use his library. <br>

Android Streaming Client is licensed under the Apache License, Version 2.0.

```
Copyright 2015 Creativa77 SRL

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Contributors:

Ayelen Chavez ashi@creativa77.com.ar
Julian Cerruti jcerruti@creativa77.com.ar

```
