package site.site8.updateapk.updateapp;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class UpdateService extends Service {
    public static final String TAG = "UpdateService";
    public static boolean DEBUG = false;

    //下载大小通知频率
    public static final int UPDATE_NUMBER_SIZE = 4;
    public static final int DEFAULT_RES_ID = -1;
    //params
    private static final String URL = "downloadUrl";
    private static final String ICO_RES_ID = "icoResId";
    private static final String ICO_SMALL_RES_ID = "icoSmallResId";
    private static final String UPDATE_PROGRESS = "updateProgress";
    private static final String STORE_DIR = "storeDir";

    private static Context mContext;


    private String downloadUrl;
    private int icoResId;             //default app ico
    private int icoSmallResId;
    private int updateProgress;   //update notification progress when it add number
    private String storeDir;          //default sdcard/Android/package/update


    private UpdateProgressListener updateProgressListener;
    private LocalBinder localBinder = new LocalBinder();
    public static final String id = "channel_1";
    public static final String name = "channel_name_1";

    /**
     * Class used for the client Binder.
     */
    public class LocalBinder extends Binder {
        /**
         * set update progress call back
         *
         * @param listener
         */
        public void setUpdateProgressListener(UpdateProgressListener listener) {
            UpdateService.this.setUpdateProgressListener(listener);
        }
    }


    private boolean startDownload;//开始下载
    private int lastProgressNumber;
    private NotificationCompat.Builder builder;
    private NotificationManager manager;
    private int notifyId;
    private String appName;
    private LocalBroadcastManager localBroadcastManager;
    private Intent localIntent;
    private DownloadApk downloadApkTask;

    /**
     * whether debug
     */
    public static void debug() {
        DEBUG = true;
    }

    private static Intent installIntent(String path) {
        Uri uri = Uri.fromFile(new File(path));
        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        //判断是否是AndroidN以及更高的版本


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            installIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri contentUri = FileProvider.getUriForFile(mContext, mContext.getPackageName() + ".fileProvider", new File(path));
            installIntent.setDataAndType(contentUri, "application/vnd.android.package-archive");
        } else {
            installIntent.setDataAndType(uri, "application/vnd.android.package-archive");
            installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        return installIntent;
    }


    private static Intent webLauncher(String downloadUrl) {
        Uri download = Uri.parse(downloadUrl);
        Intent intent = new Intent(Intent.ACTION_VIEW, download);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private static String getSaveFileName(String downloadUrl) {
        if (downloadUrl == null || TextUtils.isEmpty(downloadUrl)) {
            return "noName.apk";
        }
        return downloadUrl.substring(downloadUrl.lastIndexOf("/"));
    }

    private static File getDownloadDir(UpdateService service) {
        File downloadDir = null;
        if (Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED)) {
            if (service.storeDir != null) {
                downloadDir = new File(Environment.getExternalStorageDirectory(), service.storeDir);
            } else {
                downloadDir = new File(service.getExternalCacheDir(), "update");
            }
        } else {
            downloadDir = new File(service.getCacheDir(), "update");
        }
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
        return downloadDir;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        appName = getApplicationName();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!startDownload && intent != null) {
            startDownload = true;
            downloadUrl = intent.getStringExtra(URL);
            icoResId = intent.getIntExtra(ICO_RES_ID, DEFAULT_RES_ID);
            icoSmallResId = intent.getIntExtra(ICO_SMALL_RES_ID, DEFAULT_RES_ID);
            storeDir = intent.getStringExtra(STORE_DIR);
            updateProgress = intent.getIntExtra(UPDATE_PROGRESS, UPDATE_NUMBER_SIZE);


            if (DEBUG) {
                Log.d(TAG, "downloadUrl: " + downloadUrl);
                Log.d(TAG, "icoResId: " + icoResId);
                Log.d(TAG, "icoSmallResId: " + icoSmallResId);
                Log.d(TAG, "storeDir: " + storeDir);
                Log.d(TAG, "updateProgress: " + updateProgress);

            }

            notifyId = startId;
            buildNotification();
//            buildBroadcast();
            downloadApkTask = new DownloadApk(this);
            downloadApkTask.execute(downloadUrl);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return localBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    public void setUpdateProgressListener(UpdateProgressListener updateProgressListener) {
        this.updateProgressListener = updateProgressListener;
    }

    @Override
    public void onDestroy() {
        if (downloadApkTask != null) {
            downloadApkTask.cancel(true);
        }

        if (updateProgressListener != null) {
            updateProgressListener = null;
        }
        localIntent = null;
        builder = null;

        super.onDestroy();
    }

    public String getApplicationName() {
        PackageManager packageManager = null;
        ApplicationInfo applicationInfo = null;
        try {
            packageManager = getApplicationContext().getPackageManager();
            applicationInfo = packageManager.getApplicationInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            applicationInfo = null;
        }
        String applicationName =
                (String) packageManager.getApplicationLabel(applicationInfo);
        return applicationName;
    }


    private void buildNotification() {
        boolean isLollipop = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
        int smallIcon = getResources().getIdentifier("notification_small_icon", "drawable", getPackageName());

        if (smallIcon <= 0 || !isLollipop) {
            smallIcon = getApplicationInfo().icon;
        }

        manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

            //android8.0需要NotificationChannel
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH);
            channel.setVibrationPattern(null);
            manager.createNotificationChannel(channel);
            builder = new NotificationCompat.Builder(getApplicationContext(), id);
        } else {
            builder = new NotificationCompat.Builder(this);
        }

        builder.setContentTitle("准备下载")
                .setWhen(System.currentTimeMillis())
                .setProgress(100, 1, false)
                .setSmallIcon(smallIcon)
                .setLargeIcon(BitmapFactory.decodeResource(
                        getResources(), icoResId))
                .setDefaults(NotificationCompat.FLAG_ONLY_ALERT_ONCE);

        manager.notify(notifyId, builder.build());
    }

    private void start() {
        builder.setContentTitle(appName);
        builder.setContentText("准备下载");
        manager.notify(notifyId, builder.build());
        if (updateProgressListener != null) {
            updateProgressListener.start();
        }
    }

    /**
     * @param progress download percent , max 100
     */
    private void update(int progress) {
        if (progress - lastProgressNumber > updateProgress) {
            lastProgressNumber = progress;

            builder.setProgress(100, progress, false);
            builder.setContentText(String.format("正在下载%S", progress) + "%");
            manager.notify(notifyId, builder.build());
            if (updateProgressListener != null) {
                updateProgressListener.update(progress);
            }
        }
    }

    private void success(String path) {
        builder.setProgress(0, 0, false);
        builder.setContentText("下载成功(点击安装)");
        builder.setAutoCancel(true);
        Intent i = installIntent(path);
        PendingIntent intent = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);

        builder.setContentIntent(intent);
        builder.setDefaults(0);
        Notification n = builder.build();
        n.contentIntent = intent;
        manager.notify(notifyId, n);
        if (updateProgressListener != null) {
            updateProgressListener.success();
        }
        startActivity(i);
        stopSelf();
    }

    private void error() {
        builder.setAutoCancel(true);
        Intent i = webLauncher(downloadUrl);
        PendingIntent intent = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentText("下载失败(点击浏览器下载)");
        builder.setContentIntent(intent);
        builder.setProgress(0, 0, false);
        builder.setDefaults(0);
        Notification n = builder.build();
        n.contentIntent = intent;
        manager.notify(notifyId, n);
        if (updateProgressListener != null) {
            updateProgressListener.error();
        }
        stopSelf();
    }

    private static class DownloadApk extends AsyncTask<String, Integer, String> {

        private WeakReference<UpdateService> updateServiceWeakReference;
        /**
         * 覆盖java默认的证书验证
         */
        private static final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[]{};
            }

            public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
            }
        }};

        /**
         * 设置不验证主机
         */
        private static final HostnameVerifier DO_NOT_VERIFY = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };

        /**
         * 信任所有
         *
         * @param connection
         * @return
         */
        private static SSLSocketFactory trustAllHosts(HttpsURLConnection connection) {
            SSLSocketFactory oldFactory = connection.getSSLSocketFactory();
            try {
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                SSLSocketFactory newFactory = sc.getSocketFactory();
                connection.setSSLSocketFactory(newFactory);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return oldFactory;
        }

        public DownloadApk(UpdateService service) {
            updateServiceWeakReference = new WeakReference<>(service);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            UpdateService service = updateServiceWeakReference.get();
            if (service != null) {
                service.start();
            }
        }

        @Override
        protected String doInBackground(String... params) {

            final String downloadUrl = params[0];

            final File file = new File(UpdateService.getDownloadDir(updateServiceWeakReference.get()),
                    UpdateService.getSaveFileName(downloadUrl));
            if (DEBUG) {
                Log.d(TAG, "download url is " + downloadUrl);
                Log.d(TAG, "download apk cache at " + file.getAbsolutePath());
            }
            File dir = file.getParentFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }

            HttpURLConnection httpConnection = null;
            InputStream is = null;
            FileOutputStream fos = null;
            int updateTotalSize = 0;
            java.net.URL url;
            try {
                url = new URL(downloadUrl);
                httpConnection = (HttpURLConnection) url.openConnection();

                SSLSocketFactory oldSocketFactory = null;
                HostnameVerifier oldHostnameVerifier = null;

                boolean useHttps = url.toString().startsWith("https");
                if (useHttps) {
                    HttpsURLConnection https = (HttpsURLConnection) httpConnection;
                    oldSocketFactory = trustAllHosts(https);
                    oldHostnameVerifier = https.getHostnameVerifier();
                    https.setHostnameVerifier(DO_NOT_VERIFY);
                }

                httpConnection.setConnectTimeout(20000);
                httpConnection.setReadTimeout(20000);

                if (DEBUG) {
                    Log.d(TAG, "download status code: " + httpConnection.getResponseCode());
                }

                if (httpConnection.getResponseCode() != 200) {
                    return null;
                }

                updateTotalSize = httpConnection.getContentLength();

                if (file.exists()) {

////                    若本地存在相同大小名字的就不需要再下载
//                    if (updateTotalSize == file.length()) {
//                        // 下载完成
//                        return file.getAbsolutePath();
//                    } else {
//                        file.delete();
//                    }

                    //无论本地是否有都重新下载
                    file.delete();

                }
                file.createNewFile();
                is = httpConnection.getInputStream();
                fos = new FileOutputStream(file, false);
                byte buffer[] = new byte[4096];

                int readSize = 0;
                int currentSize = 0;

                while ((readSize = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, readSize);
                    currentSize += readSize;
                    publishProgress((currentSize * 100 / updateTotalSize));
                }
                // download success
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            } finally {
                if (httpConnection != null) {
                    httpConnection.disconnect();
                }
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return file.getAbsolutePath();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            if (DEBUG) {
                Log.d(TAG, "current progress is " + values[0]);
            }
            UpdateService service = updateServiceWeakReference.get();
            if (service != null) {
                service.update(values[0]);
            }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            UpdateService service = updateServiceWeakReference.get();
            if (service != null) {
                if (s != null) {
                    service.success(s);
                } else {
                    service.error();
                }
            }
        }
    }


    /**
     * a builder class helper use UpdateService
     */
    public static class Builder {

        private String downloadUrl;
        private int icoResId = DEFAULT_RES_ID;             //default app ico
        private int icoSmallResId = DEFAULT_RES_ID;
        private int updateProgress = UPDATE_NUMBER_SIZE;   //update notification progress when it add number
        private int versionCode = 1;
        private String storeDir;          //default sdcard/Android/package/update
        private boolean isForce = false; //是否强制更新

        protected Builder(String downloadUrl) {
            this.downloadUrl = downloadUrl;
        }

        public static Builder create(String downloadUrl) {
            if (downloadUrl == null) {
                throw new NullPointerException("downloadUrl == null");
            }
            return new Builder(downloadUrl);
        }

        public Builder setIsForce(boolean isForce) {
            this.isForce = isForce;
            return this;
        }

        public Builder setVersionCode(int versionCode) {
            this.versionCode = versionCode;
            return this;
        }

        public Builder setIcoResId(int icoResId) {
            this.icoResId = icoResId;
            return this;
        }

        public int getIcoSmallResId() {
            return icoSmallResId;
        }

        public Builder setIcoSmallResId(int icoSmallResId) {
            this.icoSmallResId = icoSmallResId;
            return this;
        }

        public Builder setStoreDir(String storeDir) {
            this.storeDir = storeDir;
            return this;
        }

        public void build(Activity context) {
            if (context == null) {
                throw new NullPointerException("context == null");
            }
            mContext = context;
            try {
                int localVersionCode = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionCode;
                //判断版本号
                if (this.versionCode <= localVersionCode) return;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            ConfirmDialog dialog = new ConfirmDialog(mContext, new ConfirmDialog.ConfirmCallback() {
                @Override
                public void sure() {
                    if (isWifiConnected(mContext)) {
                        build();
                    } else {
                        showNoWifiDialog();
                    }
                }

                @Override
                public void cancle() {
                    if (isForce) System.exit(0);
                }
            });
            dialog.setCancelable(false);
            dialog.setContent("发现新版本是否更新？");
            dialog.show();
        }


        /**
         * 没有在WIFI状态
         */
        private void showNoWifiDialog() {
            ConfirmDialog dialog = new ConfirmDialog(mContext, new ConfirmDialog.ConfirmCallback() {
                @Override
                public void sure() {
                    build();
                }

                @Override
                public void cancle() {
                    if (isForce) System.exit(0);
                }
            });
            dialog.setCancelable(false);
            dialog.setContent("目前手机不是WiFi状态\n确认是否继续下载更新？").show();
        }


        /**
         * 检测wifi是否连接
         */
        private boolean isWifiConnected(Context context) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo networkInfo = cm.getActiveNetworkInfo();
                if (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                    return true;
                }
            }
            return false;
        }


        /**
         * 启动Service
         *
         * @return
         */
        private Builder build() {
            Intent intent = new Intent();
            intent.setClass(mContext, UpdateService.class);
            intent.putExtra(URL, downloadUrl);

            if (icoResId == DEFAULT_RES_ID) {
                icoResId = getIcon(mContext);
            }

            if (icoSmallResId == DEFAULT_RES_ID) {
                icoSmallResId = icoResId;
            }
            intent.putExtra(ICO_RES_ID, icoResId);
            intent.putExtra(STORE_DIR, storeDir);
            intent.putExtra(ICO_SMALL_RES_ID, icoSmallResId);
            intent.putExtra(UPDATE_PROGRESS, updateProgress);
            mContext.startService(intent);
            return this;
        }

        private int getIcon(Context context) {

            final PackageManager packageManager = context.getPackageManager();
            ApplicationInfo appInfo = null;
            try {
                appInfo = packageManager.getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            if (appInfo != null) {
                return appInfo.icon;
            }
            return 0;
        }
    }

}
