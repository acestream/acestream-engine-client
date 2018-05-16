Android Ace Stream Service
==========================

On Android Ace Stream applications provide a service which is used to start engine (if it's not already started).

The general schema of using engine on Android is:

- bind to Ace Stream service
- send "start" command to service (to start engine)
- wait for "ready" event from service (which means that engine has started and ready to receive commands)
- use engine via `Engine API <http://wiki.acestream.org/wiki/index.php/Engine_API>`_ or `HTTP API <http://wiki.acestream.org/wiki/index.php/Engine_HTTP_API>`_

You can bind directly to the service via AIDL or Messenger (see `Android Bound Services <https://developer.android.com/guide/components/bound-services>`_).

Here is the sample app which shows both ways to connect to Ace Stream service: `<https://bitbucket.org/AceStream/androidacestreamserviceclientexample>`_

The third and preferred way to start engine on Android is `Ace Stream Client` module, which is covered later in this document.
It's a wrapper around AIDL interface of Ace Stream service.

Ace Stream applications
-----------------------

Ace Stream for Android is distributed within several packages with different application IDs.

Currently there are four official application IDs:

- ``org.acestream.media`` (Ace Stream Media for Android)
- ``org.acestream.media.atv`` (Ace Stream Media for Android TV)
- ``org.acestream.core`` (Ace Stream Engine for Android)
- ``org.acestream.core.atv`` (Ace Stream Engine for Android TV)

Selecting service
-----------------

There is a possibility that several Ace Stream apps are installed on the same device.
Each app provides its own Ace Stream service.
In such situation a client should connect to the service of app with the highest version code.

If you are using Ace Stream Client the selection of proper app is done automatically.

The procedure of app selection is implemented (with comments) in ``ServiceClient.getServicePackage(Context ctx)`` method (see `code <https://github.com/acestream/android-service-client/blob/master/src/main/java/org/acestream/engine/ServiceClient.java#L145>`_).


Ace Stream Client
==========================

`Ace Stream Client` is an Anroid Studio module which simplifies the procedure of starting Ace Stream engine on Android.


Including Ace Stream Client in your app with Android Studio
-----------------------------------------------------------

1. Open the terminal and execute these commands:

   .. code-block:: bash

      cd your_project_folder
      git clone https://github.com/acestream/android-service-client.git AceStreamClient

2. On the root of your project create/modify the ``settings.gradle`` file. It should contain something like this:

   .. code-block:: gradle

      include ':app', ':AceStreamClient'

3. Edit your project's ``build.gradle`` to add this in the `dependecies` section:

   .. code-block:: gradle

      dependencies {
          // ...
          implementation project(':AceStreamClient')
      }

Using Ace Stream Client
-----------------------

- import client

  .. code-block:: java

     import org.acestream.engine.client;

- create client instance. Usually this is done in activity's or service's ``onCreate`` method.
  You must provide a callback which implements ``ServiceClient.Callback`` interface to receive events from service.

  .. code-block:: java

     public class MyActivity extends Activity implements ServiceClient.Callback {
         private ServiceClient mClient = null;
         // ...

         @Override
         protected void onCreate(Bundle savedInstanceState) {
             super.onCreate();
             // ...
             mClient = new ServiceClient(/*Context*/ this, /*ServiceClient.Callback*/ this);
         }

         //////////////////////////////////////////////////
         //
         // ServiceClient.Callback interface implementation

         @Override
         public void onConnected(int engineApiPort, int httpApiPort) {
             // Engine is ready to receive command.
             // Now you can use engine via either Engine API or HTTP API.
             // @see http://wiki.acestream.org/wiki/index.php/Engine_API
             // @see http://wiki.acestream.org/wiki/index.php/Engine_HTTP_API
         }

         @Override
         public void onFailed() {
             // Engine failed to start
         }

         @Override
         public void onDisconnected() {
             // Service is disconnected
         }

         @Override
         public void onUnpacking() {
             // Engine is unpacking
         }

         @Override
         public void onStarting() {
             // Engine is starting
         }

         @Override
         public void onStopped() {
             // Engine has stopped
         }

         @Override
         public void onPlaylistUpdated() {
             // Engine's media server event: playlist updated
         }

         @Override
         public void onEPGUpdated() {
             // Engine's media server event: EPG updated
         }

         @Override
         public void onRestartPlayer() {
             // You should restart player (stop playback and then start it with the same playback URI)
         }

     }

- start engine when you need it. It can be done in activity's ``onResume`` method. ``startEngine`` method throws ``ServiceClient.EngineNotFoundException`` exception when Ace Stream application is not installed.

  .. code-block:: java

     @Override
     protected void onResume() {
         super.onResume();
         try {
             if(mClient.startEngine()) {
                 // "start" command was sent. Wait for "onConnected" callback.
             }
             else {
                 // Failed to send "start" command.
             }
         }
         catch(ServiceClient.EngineNotFoundException e) {
             // Ace Stream is not installed
         }
     }


- disconnect from engine service when you no longer need it. This can be done in activity's ``onPause`` method:

  .. code-block:: java

     @Override
     protected void onPause() {
         super.onPause();
         mClient.disconnect();
     }

Engine will start asynchronous.

Upon successfull start ``callback.onConnected(int engineApiPort, int httpApiPort)`` method will be called.

Upon failure ``callback.onFailed()`` method will be called.
