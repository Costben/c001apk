package app.awavenue.c001apk.platform;

import android.os.ParcelFileDescriptor;

interface IShizukuInstallService {
    void destroy();
    String installApk(in ParcelFileDescriptor pfd, long size);
}
