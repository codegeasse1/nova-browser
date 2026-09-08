package com.android.installreferrer.api;

import android.content.Context;
import android.os.RemoteException;

public abstract class InstallReferrerClient {
    public InstallReferrerClient() {
    }

    public static Builder newBuilder(Context context) {
        return new Builder(context);
    }

    public abstract boolean isReady();

    public abstract void startConnection(InstallReferrerStateListener installReferrerStateListener);

    public abstract void endConnection();

    public abstract ReferrerDetails getInstallReferrer() throws RemoteException;

    public static final class Builder {
        private final Context mContext;

        private Builder(Context context) {
            this.mContext = context;
        }

        public InstallReferrerClient build() {
            return new InstallReferrerClientImpl(this.mContext);
        }
    }

    public static @interface InstallReferrerResponse {
        int SERVICE_DISCONNECTED = -1;
        int OK = 0;
        int SERVICE_UNAVAILABLE = 1;
        int FEATURE_NOT_SUPPORTED = 2;
        int DEVELOPER_ERROR = 3;
        int PERMISSION_ERROR = 4;
    }
}