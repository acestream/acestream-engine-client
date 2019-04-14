package org.acestream.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.acestream.engine.client.BuildConfig;
import org.acestream.engine.service.v0.IAceStreamEngine;
import org.acestream.engine.service.v0.IAceStreamEngineCallback;
import org.acestream.engine.service.v0.IStartEngineResponse;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;

public class ServiceClient {

    private final static String TAG = "AS/ServiceClient";

    private IAceStreamEngine mService = null;
    private boolean mBound = false;
    private boolean mIsActive = false;
    private boolean mStartOnBind;
    private boolean mEnableAceCastServerOnBind = false;
    private final String mName;
    private final Context mContext;
    private final Callback mCallback;
    private final ReentrantLock mBoundLock = new ReentrantLock();

    private String mAccessToken = null;
    private int mEngineApiPort = 0;
    private int mHttpApiPort = 0;

    private Handler mHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onConnected(IAceStreamEngine service);
        void onFailed();
        void onDisconnected();
        void onUnpacking();
        void onStarting();
        void onStopped();
        void onPlaylistUpdated();
        void onEPGUpdated();
        void onRestartPlayer();
        void onSettingsUpdated();
    }

    public static class ServiceMissingException extends Exception {}

    private IAceStreamEngineCallback mRemoteCallback = new IAceStreamEngineCallback.Stub() {
        @Override
        public void onUnpacking() throws RemoteException {
            Log.d(TAG, "Service callback onUnpacking");
            runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    mCallback.onUnpacking();
                }
            });
        }

        @Override
        public void onStopped() throws RemoteException {
            Log.d(TAG, "Service callback onStopped");
            setIsActive(false);
            runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    mCallback.onStopped();
                }
            });
        }

        @Override
        public void onWaitForNetworkConnection() {
            // Old method, do nothing
            // It still exists because cannot be deleted from AIDL without breaking compatibility.
        }

        @Override
        public void onStarting() throws RemoteException {
            Log.d(TAG, "Service callback onStarting");
            runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    mCallback.onStarting();
                }
            });
        }

        @Override
        public void onPlaylistUpdated() throws RemoteException {
            Log.d(TAG, "Service callback onPlaylistUpdated");
            runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    mCallback.onPlaylistUpdated();
                }
            });
        }

        @Override
        public void onEPGUpdated() throws RemoteException {
            Log.d(TAG, "Service callback onEPGUpdated");
            runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    mCallback.onEPGUpdated();
                }
            });
        }

        @Override
        public void onSettingsUpdated() throws RemoteException {
            Log.d(TAG, "Service callback onSettingsUpdated");
            runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    mCallback.onSettingsUpdated();
                }
            });
        }

        @Override
        public void onRestartPlayer() throws RemoteException {
            Log.d(TAG, "Service callback onRestartPlayer");
            runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    mCallback.onRestartPlayer();
                }
            });
        }

        @Override
        public void onReady(int port) throws RemoteException {
            Log.d(TAG, "Service callback onReady: port=" + port);
            final boolean success = (port != -1);
            setIsActive(success);
            if (success) {
                mAccessToken = mService.getAccessToken();
                mEngineApiPort = mService.getEngineApiPort();
                mHttpApiPort = mService.getHttpApiPort();
            }

            runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    if (success) {
                        mCallback.onConnected(mService);
                    } else {
                        mCallback.onFailed();
                    }
                }
            });
        }
    };

    private final IStartEngineResponse mStartEngineCallback = new IStartEngineResponse.Stub() {
        @Override
        public void onResult(boolean success) throws RemoteException {
            mAccessToken = mService.getAccessToken();
            mEngineApiPort = mService.getEngineApiPort();
            mHttpApiPort = mService.getHttpApiPort();

            // hide real token from log
            String token = null;
            if(mAccessToken != null) {
                token = mAccessToken.substring(0, 4);
            }

            Log.d(TAG, "engine started: engineApiPort=" + mEngineApiPort + " httpApiPort=" + mHttpApiPort + " token=" + token);

            mRemoteCallback.onReady(mEngineApiPort);
        }
    };

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Service disconnected");
            mCallback.onDisconnected();
            mService = null;
            mBound = false;
        }
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Service connected");
            mService = IAceStreamEngine.Stub.asInterface(service);
            try {
                mService.registerCallbackExt(mRemoteCallback, true);
                if(mStartOnBind) {
                    mService.startEngineWithCallback(mStartEngineCallback);
                }
                if(mEnableAceCastServerOnBind) {
                    mService.enableAceCastServer();
                }
            } catch (RemoteException e) {
                Log.e(TAG, "error", e);
                mCallback.onFailed();
            }
        }
    };

    public ServiceClient(String name, Context ctx, Callback callback) {
        this(name, ctx, callback,true);
    }

    public ServiceClient(String name, Context ctx, Callback callback, boolean startOnBind) {
        if(BuildConfig.DEBUG) {
            Log.v(TAG, "new service client: name=" + name + " context=" + ctx);
        }
        mName = name;
        mContext = ctx;
        mCallback = callback;
        mStartOnBind = startOnBind;
    }

    public String getAccessToken() {
        return mAccessToken;
    }

    public int getEngineApiPort() {
        return mEngineApiPort;
    }

    public int getHttpApiPort() {
        return mHttpApiPort;
    }

    private static List<ResolveInfo> resolveIntent(Context ctx, Intent intent) {
        PackageManager pm = ctx.getPackageManager();
        List<ResolveInfo> resolveInfo = pm.queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if(resolveInfo == null || resolveInfo.size() == 0) {
            Log.d(TAG, "resolveIntent: nothing found");
            return null;
        }

        return resolveInfo;
    }

    /**
     * Select which Ace Stream application to connect to.
     * If no Ace Stream apps are installed return null.
     * If several Ace Stream apps are installed then select one with highest version code.
     *
     * @param ctx Any valid Context (used to resolve intents)
     * @return Selected application ID. If no installed app is found then ServiceMissingException is thrown
     */
    public static String getServicePackage(Context ctx) throws ServiceMissingException {
        String selectedPackage = null;
        int maxVersion = -1;

        // Place all found Ace Streams apps here with version codes
        List<Pair<String,Integer>> packages = new ArrayList<>();

        // First, find well-known apps
        List<String> knownPackages = new ArrayList<String>(){
            {
                add("org.acestream.media");
                add("org.acestream.media.atv");
                add("org.acestream.core");
                add("org.acestream.core.atv");
            }
        };
        for(String packageName: knownPackages) {
            int version = getAppVersion(ctx, packageName);
            if(version != -1) {
                packages.add(new Pair<>(packageName, version));
                if(version > maxVersion) {
                    maxVersion = version;
                }
            }
        }

        // Second, find apps by implicit service intent.
        // Only apps starting from version 3.1.30.1 (code 301301000) can be found with intent.
        Intent intent = new Intent("org.acestream.engine.service.v0.IAceStreamEngine");
        List<ResolveInfo> services = resolveIntent(ctx, intent);
        if(services != null) {
            for (ResolveInfo ri : services) {
                int version = getAppVersion(ctx, ri.serviceInfo.packageName);
                if(version != -1) {
                    if(!knownPackages.contains(ri.serviceInfo.packageName)) {
                        packages.add(new Pair<>(ri.serviceInfo.packageName, version));
                        if (version > maxVersion) {
                            maxVersion = version;
                        }
                    }
                }
            }
        }

        // Select package with the max version
        for(Pair<String,Integer> item: packages) {
            if(item.second == maxVersion) {
                selectedPackage = item.first;
                break;
            }
        }

        if(BuildConfig.DEBUG) {
            Log.v(TAG, "getServicePackage: selected: id=" + selectedPackage);
        }

        if(selectedPackage == null) {
            Log.e(TAG, "AceStream is not installed");
            throw new ServiceMissingException();
        }

        return selectedPackage;
    }

    private static int getAppVersion(Context ctx, String packageName) {
        int versionCode;
        try {
            PackageInfo pkgInfo = ctx.getPackageManager().getPackageInfo(packageName, 0);
            versionCode = pkgInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            versionCode = -1;
        }
        return versionCode;
    }

    public static Intent getServiceIntent(Context ctx) throws ServiceMissingException {
        Intent intent = new Intent(org.acestream.engine.service.v0.IAceStreamEngine.class.getName());
        String servicePackage = getServicePackage(ctx);
        intent.setPackage(servicePackage);

        return intent;
    }

    public void bind() throws ServiceMissingException {
        Log.d(TAG, "Service bind: name=" + mName + " class=" + org.acestream.engine.service.v0.IAceStreamEngine.class.getName());
        mBoundLock.lock();
        try {
            if (!mBound) {
                mBound = mContext.bindService(getServiceIntent(mContext), mConnection, Context.BIND_AUTO_CREATE);
                Log.d(TAG, "Service bind done: bound=" + mBound);
            } else {
                Log.d(TAG, "Already bound");
            }
        }
        finally {
            mBoundLock.unlock();
        }
    }

    public void unbind() {
        Log.d(TAG, "Service unbind: name=" + mName);
        mBoundLock.lock();
        try {
            if (mBound) {
                if (mService != null) {
                    try {
                        mService.unregisterCallback(mRemoteCallback);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                mContext.unbindService(mConnection);
                mBound = false;
            } else {
                Log.d(TAG, "Already unbound");
            }
        }
        finally {
            mBoundLock.unlock();
        }
    }

    public void startEngine() throws ServiceMissingException {
        if(mService != null) {
            try {
                mService.startEngineWithCallback(mStartEngineCallback);
            }
            catch(Throwable e) {
                Log.e(TAG, "Failed to start engine", e);
                mCallback.onFailed();
            }
        }
        else {
            mStartOnBind = true;
            if(!mBound) {
                bind();
            }
        }
    }

    public void enableAceCastServer() throws ServiceMissingException {
        Log.v(TAG, "enableAceCastServer: bound=" + mBound + " service=" + mService + " name=" + mName);
        if(mService != null) {
            try {
                mService.enableAceCastServer();
            }
            catch(Throwable e) {
                Log.e(TAG, "Failed to enable AceCast server", e);
            }
        }
        else {
            mEnableAceCastServerOnBind = true;
            if(!mBound) {
                bind();
            }
        }
    }

    synchronized void setIsActive(boolean active) {
        // if already active we won't make unactive
        if (!mIsActive) {
            mIsActive = active;
        }
    }

    public synchronized boolean isActive() {
        return mIsActive;
    }

    public synchronized boolean isBound() {
        mBoundLock.lock();
        try {
            return mBound;
        }
        finally {
            mBoundLock.unlock();
        }
    }

    private void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        }
        else {
            mHandler.post(runnable);
        }
    }
}
