#Android Streaming Client

##About

Android library that receives video streaming and plays it in an Android device (particularly in a SurfaceView).
The current version of the code only supports RTP over UDP as the transport protocol and decodes H264 encoded video.
As streaming server, we are using libstreaming library with some modifications running in another Android devices.

##How to use it

Android Streaming Client uses a [libstreaming library for Android's fork](https://github.com/ashyonline/libstreaming) to stream video. 
There is also a [fork from libstreaming-examples](https://github.com/ashyonline/libstreaming-examples) that adds a fourth example (look for example4 folder) to the 3 existing ones. 
In order to test Android Streaming Client library you should follow this steps:

* Clone libstreaming-examples fork:
```
   > git clone https://github.com/ashyonline/libstreaming-examples
```

* Clone libstreaming fork:
```
   > git clone https://github.com/ashyonline/libstreaming
```

Keep in mind that, as libstreaming-examples uses libstreaming as a dependency, you may want to clone libstreaming inside libstreaming-examples folder.

* Import the examples project in your favorite Android IDE.

If using Android Studio, you should import each example (example1, example2, and so on) as a module inside an already existing project. Android Studio will automatically create a build.gradle file for each imported module. Remember to add the libstreaming dependency to those files.

Inside the android brackets add the following line:
```
   dependencies {
      compile project(':libstreaming')
   }
```

* Clone this one repository:
```
   > git clone git@github.com:creativa77/AndroidStreamingClient.git
```

* Import Android Streaming Client project in your favorite Android IDE, also.

* Check the client device's ip address and change [this line](https://github.com/ashyonline/libstreaming-examples/blob/master/example4/src/net/majorkernelpanic/example4/MainActivity.java#L25) accordingly. 

That way, the publisher (example4) will know where to stream the video.

* Run example4 in the server Android device.

* Run the module example from AndroidStreamingClient repository in the client Android device.

If everything works, you will be streaming video from one device to another in real time.

Enjoy!

##License

```
Copyright 2015 Creativa77

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
