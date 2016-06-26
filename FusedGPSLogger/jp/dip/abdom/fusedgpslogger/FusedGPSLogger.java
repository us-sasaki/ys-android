package jp.dip.abdom.fusedgpslogger;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderApi;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Created by Yusuke on 2016/05/22.
 */
public class FusedGPSLogger extends BasePeriodicService
        implements LocationListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

/*-----------
 * Constants
 */
    // 書き込むファイルのpath (CSV形式、追記で書き込む)
    public static final String LOG_FILE = "/storage/sdcard0/Download/GPSLog";

    // setFastestInterval で設定する値 onLocationUpdate をどのくらいの頻度で呼んでよいかを
    // 伝える。5 秒が推奨値。
    protected static final long INTERVAL = 1000 * 5; // 5秒、Google API Reference での推奨値

    // 時々 Service を alarm から起動させる。クラスをアンロードさせない効果があるかも？
    protected static final long WAKEUP_INTERVAL = 1000 * 60 * 10; // 10分ごとに service wake up

    // Log出力用のタグ
    public static final String TAG = "FusedGPSLogger";

/*------------------
 * static variables
 */
    // 画面から常駐を解除したい場合のために，常駐インスタンスを保持
    public static FusedGPSLogger activeService;

/*--------------------
 * instance variables
 */
    // ロケーションマネージャを保存
    // 使わないからそのうち削除
    protected LocationManager manager;

    // SimpleDateFormat
    protected SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd HH:mm:ss");

    protected SimpleDateFormat yyMMdd = new SimpleDateFormat("yyMMdd");

    // onLocationChanged を呼ぶ頻度(目安)
    protected long interval = 1000 * 20; // 20秒

    // 常駐サービスの状態を表すメッセージ
    protected String message = "";

    // Google Location API に渡すパラメータセット
    private LocationRequest mLocationRequest;

    // GoogleApiClient インスタンス
    // Stores the current instantiation of the location client in this object
    private GoogleApiClient mGoogleApiClient;
    //private FusedLocationProviderApi fusedLocationProviderApi;

    // Google Location Service に対して、周期的な location の update を要求しているか
    private boolean mRequestingLocationUpdates = false;

    private Location mLastLocation;

/*--------------------------------
 * overrides(BasePeriodicService)
 */

    /**
     * このサービスが呼ばれた時の処理
     * GoogleApiService に接続する。
     * 接続は非同期に行われ、完了時に onConnect() が呼ばれるため、そっちで Location Service に
     * 周期的な update の依頼を行っている。
     *
     * @param intent intent
     * @param flags  flags
     * @param startId startId
     * @return START_STICKYを返す
     */
    @Override
    public int onStartCommandImpl(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommandImpl");

        if (activeService == null) {
            message = "onStartCommandImplが呼ばれました";

            // 永続化に必要な手続きらしい
            //
            Intent activityIntent = new Intent(this, FusedGPSControlActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, activityIntent, 0);

            Notification notification = new Notification.Builder(this)
                    .setContentTitle("FusedGPSLogger")
                    .setContentText("タップして設定")
                    .setContentIntent(pendingIntent)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .build();
            startForeground(startId, notification);

            //
            // 変数の初期化
            //
            mLocationRequest = LocationRequest.create();

            // 更新周期を LocationRequest に設定。
            // ここで設定した周期で更新するよう Google Play Service は努力すると思われる
            mLocationRequest.setInterval(interval);

            // 高精度を利用 (これも Google API Reference での推奨値
            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

            // 最短周期もintervalにし、極力interval間隔でupdateされるようにする
            mLocationRequest.setFastestInterval(interval);
        }

        if (mGoogleApiClient == null) {
            //mGoogleApiClient
            //fusedLocationProviderApi = LocationServices.FusedLocationApi;
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        if ((!mGoogleApiClient.isConnected())&&(!mGoogleApiClient.isConnecting())) {
            // connectに電力や時間を使いそうなので、つなぎっぱなしにする
            mGoogleApiClient.connect();
            message = "Google API Service に connect しました";

            // connect() は非同期につながるらしい
            // つながったら、onConnect() が呼ばれ、startLocationUpdates() を呼ぶ。
        }
        return START_STICKY;
    }

    /**
     * このサービスが終了するときの処理
     * Google Location Service に要求していた周期的な update を停止し、GoogleApiService を切断
     */
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        stopLocationUpdates();
        stopResidentIfActive(this);
        mGoogleApiClient.disconnect();
    }

    /**
     * この interval はexecTask() を呼ぶ(画面上のステータスの表示)周期
     *
     * @return  execTask() を呼ぶ周期(msec)
     */
    @Override
    protected long getIntervalMS() {
        return WAKEUP_INTERVAL;
    }

    @Override
    protected void execTask() {
        activeService = this;

        // ※もし毎回の処理が重い場合は，メインスレッドを妨害しないために
        // ここから下を別スレッドで実行する。

        // ログ出力（ここに定期実行したい処理を書く）

        makeNextPlan();
    }


    @Override
    public void makeNextPlan() {
        // アラームに interval(msec)後、起こしてもらう設定をする
        this.scheduleNextTime();
    }

/*------------------
 * instance methods
 */
    /**
     * これにより onLocationChanged が呼ばれるようになる
     * https://developer.android.com/training/location/receive-location-updates.html
     */
    protected void startLocationUpdates() {
        Log.d(TAG,"startLocationUpdates");
        if (!mRequestingLocationUpdates) {
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    mGoogleApiClient, mLocationRequest, this);
            mRequestingLocationUpdates = true;
            Log.d(TAG, "startLocationUpdates:requestLocationUpdates");
        }
        message = "Google Location Service に情報更新を要求しています";
    }

    /**
     * これにより onLocationChanged が呼ばれないようになる
     * https://developer.android.com/training/location/receive-location-updates.html
     */
    protected void stopLocationUpdates() {
        Log.d(TAG,"stopLocationUpdates");
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, this);
        mRequestingLocationUpdates = false;
    }

/*------------------------------
 * implements(LocationListener)
 */
    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG,"onLocationChanged:Location="+location);
        message = "Location 情報が更新されました"+location;
        if (location != null) {
            if (location.equals(mLastLocation)) Log.d(TAG, "same Location to the last");
            else mLastLocation = location;
        }
        if (location != null) {
            //
            // GPS情報を各変数に取得
            //
            double lat = location.getLatitude();
            double lng = location.getLongitude();
            long time = location.getTime();

            float accuracy = -999.0f;
            if (location.hasAccuracy()) accuracy = location.getAccuracy();

            double altitude = -999.0;
            if (location.hasAltitude()) altitude = location.getAltitude();

            float bearing = -999.0f;
            if (location.hasBearing()) bearing = location.getBearing();

            float speed = -999.0f;
            if (location.hasSpeed()) speed = location.getSpeed();

            //
            // ファイルオープンし、追記
            //
            PrintWriter p = null;
            File logFile = new File(LOG_FILE + yyMMdd.format(new Date()) + ".txt");
            try {
                if (!logFile.exists()) {
                    FileWriter w = new FileWriter(logFile);
                    p = new PrintWriter(w);
                    p.println("日時,TIME,正確さ,緯度,経度,高度,方角,速度");
                } else {
                    FileWriter w = new FileWriter(logFile, true);
                    p = new PrintWriter(w);
                }

                p.print(sdf.format(new Date(time))); p.write(',');
                p.print(time); p.write(',');
                p.print(accuracy); p.write(',');
                p.print(lat); p.write(',');
                p.print(lng); p.write(',');
                p.print(altitude); p.write(',');
                p.print(bearing); p.write(',');
                p.println(speed);
                Log.d("GPSLogger", "ログ書き込み");
                message = "ログを正常に書き込んでいます";
            } catch (IOException e) {
                Log.d("GPSLogger", "ファイル書き込みエラー:"+e);
                message = "エラー:"+e;
            } finally {
                try {
                    p.close();
                } catch (Exception ignored) {
                    // nothing to do (can occur NullPointerException)
                }
            }
            // removeUpdate はしない(Google Serviceから自動で呼び続けてもらう)
        }
    }

/*-------------------------------------------------
 * implements(GoogleApiClient.ConnectionCallbacks)
 */
    @Override
    public void onConnected(Bundle connectionHint) {
        Log.d(TAG,"onConnected:connectionHint="+connectionHint);
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient); // null になることがまれにあるらしい
        if (!mRequestingLocationUpdates) startLocationUpdates();
        mRequestingLocationUpdates = true;
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.d(TAG, "onConnectionSuspended:cause="+cause);
    }

/*--------------------------------------------------------
 * implements(GoogleApiClient.OnConnectionFailedListener)
 */
    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.d(TAG,"onConnectionFailed:result="+result);
    }

/*---------------
 * class methods
 */
    /**
     * もし起動していたら，常駐を解除する
     */
    public static void stopResidentIfActive(Context context) {
        if( activeService != null ) {
            activeService.stopResident(context);
            activeService = null; // Yusuke Added
        }
    }

    public static void setInterval(long interval) {
        if (interval < INTERVAL) return; // 最小値は INTERVAL
        if (activeService != null) {
            activeService.interval = interval;

            // 新しい interval を LocationRequest に設定
            activeService.mLocationRequest.setInterval(interval);
            activeService.mLocationRequest.setFastestInterval(interval);

            // 再度新しいLocationRequestでupdateを要求
            activeService.stopLocationUpdates();
            activeService.startLocationUpdates();
        }
    }

    public static long getInterval() {
        if (activeService != null) return activeService.interval;
        return -1;
    }

    public static String getMessage() {
        if (activeService != null) return activeService.message;
        return "activeServiceがありません";
    }

}
