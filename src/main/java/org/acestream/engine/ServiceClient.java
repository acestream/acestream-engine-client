package org.acestream.engine;

import java.util.ArrayList;
import java.util.List;

import org.acestream.engine.service.v0.IAceStreamEngine;
import org.acestream.engine.service.v0.IAceStreamEngineCallback;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;

public class ServiceClient {

	private final static String TAG = "AceStream/ServiceClient";

	private IAceStreamEngine mService = null;
	private boolean mBound = false;
	private final Object mBoundLock = new Object();
	private final Context mContext;
	private final Callback mCallback;

	public interface Callback {
		void onConnected(int engineApiPort, int httpApiPort);
		void onFailed();
		void onDisconnected();
		void onUnpacking();
		void onStarting();
		void onStopped();
		void onPlaylistUpdated();
		void onEPGUpdated();
		void onRestartPlayer();
	}

	@SuppressWarnings("WeakerAccess")
    public static class EngineNotFoundException extends Exception {
    }

	private IAceStreamEngineCallback mRemoteCallback = new IAceStreamEngineCallback.Stub() {
		@Override
		public void onUnpacking() {
			Log.d(TAG, "Service callback onUnpacking");
			mCallback.onUnpacking();
		}

		@Override
		public void onStopped() {
			Log.d(TAG, "Service callback onStopped");
			mCallback.onStopped();
		}

		@Override
		public void onWaitForNetworkConnection() {
			// Old method, do nothing
			// It still exists because cannot be deleted from AIDL without breaking compatibility.
		}

		@Override
		public void onStarting() {
			Log.d(TAG, "Service callback onStarting");
			mCallback.onStarting();
		}

		@Override
		public void onPlaylistUpdated() {
			Log.d(TAG, "Service callback onPlaylistUpdated");
			mCallback.onPlaylistUpdated();
		}

		@Override
		public void onEPGUpdated() {
			Log.d(TAG, "Service callback onEPGUpdated");
			mCallback.onEPGUpdated();
		}

		@Override
		public void onRestartPlayer() {
			Log.d(TAG, "Service callback onRestartPlayer");
			mCallback.onRestartPlayer();
		}

		@Override
		public void onReady(int port) throws RemoteException {
			Log.d(TAG, "Service callback onReady: port=" + port);
			boolean success = (port != -1);
			if (success) {
				mCallback.onConnected(mService.getEngineApiPort(), mService.getHttpApiPort());
			} else {
				mCallback.onFailed();
			}
		}
	};

	private void notifyServiceDisconnected() {
	    synchronized (mBoundLock) {
            mCallback.onDisconnected();
            mService = null;
            mBound = false;
        }
    }

	private ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.d(TAG, "Service disconnected");
            notifyServiceDisconnected();
		}
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.d(TAG, "Service connected");
			mService = IAceStreamEngine.Stub.asInterface(service);
			try {
				mService.registerCallback(mRemoteCallback);
                mService.startEngine();
			} catch (RemoteException e) {
				Log.e(TAG, "error", e);
			}
		}
	};

	public ServiceClient(Context ctx, Callback callback) {
        mContext = ctx;
        mCallback = callback;
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
     * @return Selected application ID or null.
     */
	private static String getServicePackage(Context ctx) {
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
				Log.v(TAG, "getServicePackage: found known: id=" + packageName + " version=" + version);
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
						Log.v(TAG, "getServicePackage: found by service: id=" + ri.serviceInfo.packageName + " version=" + version);
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

		Log.v(TAG, "getServicePackage: selected: id=" + selectedPackage);

		return selectedPackage;
	}

	private static int getAppVersion(Context ctx, String packageName) {
		int versionCode = -1;
		try {
			PackageInfo pkgInfo = ctx.getPackageManager().getPackageInfo(packageName, 0);
			versionCode = pkgInfo.versionCode;
		} catch (PackageManager.NameNotFoundException e) {
			Log.v(TAG, "Failed to get package version: " + e.getMessage());
		}
		return versionCode;
	}

	private static Intent getServiceIntent(Context ctx) throws EngineNotFoundException {
		Intent intent = new Intent(org.acestream.engine.service.v0.IAceStreamEngine.class.getName());
		String servicePackage = getServicePackage(ctx);
		if(servicePackage == null) {
		    throw new EngineNotFoundException();
        }
		intent.setPackage(servicePackage);

		return intent;
	}

	private boolean bind() throws EngineNotFoundException {
		Log.d(TAG, "Service bind");
		synchronized (mBoundLock) {
			if (!mBound) {
				if(mContext.bindService(getServiceIntent(mContext), mConnection, Context.BIND_AUTO_CREATE)) {
                    Log.d(TAG, "Service bind done");
                    mBound = true;
                }
                else {
                    Log.d(TAG, "Service bind failed");
                    mBound = false;
                }
			} else {
				Log.d(TAG, "Already bound");
			}
			return mBound;
		}
	}

    @SuppressWarnings("unused")
	public void disconnect() {
		Log.d(TAG, "Service unbind");
		synchronized (mBoundLock) {
			if (mBound) {
				if (mService != null) {
					try {
						mService.unregisterCallback(mRemoteCallback);
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				}
				mContext.unbindService(mConnection);
                notifyServiceDisconnected();
			} else {
				Log.d(TAG, "Already unbound");
			}
		}
	}

	@SuppressWarnings("unused")
	public boolean startEngine() throws EngineNotFoundException {
		Log.v(TAG, "startEngine: bound=" + mBound + " service=" + mService);
		if(mService != null) {
			try {
				mService.startEngine();
			}
			catch(RemoteException e) {
				Log.e(TAG, "Failed to start engine", e);
				return false;
			}
		}
		else {
			if(!mBound) {
				return bind();
			}
		}

		return true;
	}

	@SuppressWarnings("unused")
	public boolean isBound() {
		synchronized (mBoundLock) {
			return mBound;
		}
	}

    @SuppressWarnings("unused")
	public int getEngineApiPort() {
	    if(mService == null) {
	        return 0;
        }
        else {
	        try {
                return mService.getEngineApiPort();
            }
            catch(RemoteException e) {
	            return 0;
            }
        }
    }

    @SuppressWarnings("unused")
    public int getHttpApiPort() {
        if(mService == null) {
            return 0;
        }
        else {
            try {
                return mService.getHttpApiPort();
            }
            catch(RemoteException e) {
                return 0;
            }
        }
    }
}
