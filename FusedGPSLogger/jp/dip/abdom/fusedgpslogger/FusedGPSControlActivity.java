package jp.dip.abdom.fusedgpslogger;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class FusedGPSControlActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION = 10;

    TextView status;
    EditText interval;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fused_gpscontrol);

        // 画面オブジェクトを検索しておく
        status = (TextView)findViewById(R.id.status);
        interval = (EditText)findViewById(R.id.interval);

        // 画面表示
        refresh();

        // パーミッションの確認
        if(Build.VERSION.SDK_INT >= 23){
            checkPermission(Manifest.permission.ACCESS_FINE_LOCATION);
            checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
            checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

    }

    /**
     * ログスタートボタンを押したときの処理
     *
     * @param view
     */
    public void doStart(View view) {
        new FusedGPSLogger().startResident(this);
        refresh();
    }

    /**
     * ログストップボタンを押したときの処理
     *
     * @param view
     */
    public void doStop(View view) {
        FusedGPSLogger.stopResidentIfActive(this);
        refresh();
    }

/*------------------
 * instance methods
 */
    /**
     * 間隔(秒)設定ボタンを押したときの処理
     *
     * @param view
     */
    public void doRefresh(View view) {
        int second = 300;
        try {
            String text = interval.getText().toString();
            second = Integer.parseInt(text);
        } catch (Exception ignored) {
            // nothing to do
        }
        if (second > 0) FusedGPSLogger.setInterval((long)second*1000);
        refresh();
    }

    /**
     * 間隔(秒)表示とメッセージを更新
     */
    protected void refresh() {
        interval.setText(String.valueOf(FusedGPSLogger.getInterval()/1000));
        String text = FusedGPSLogger.getMessage() + ": 間隔(" + (FusedGPSLogger.getInterval()/1000)+ "秒)";
        status.setText(text);
    }


/*-----------------------------------------------------------------
 * check permission (Android6.0(API23)～ の Runtime Permission 対応
 */
    // 位置情報許可の確認
    private void checkPermission(String permissionType) {
        // 既に許可している場合、何もしない
        if (ActivityCompat.checkSelfPermission(this, permissionType)== PackageManager.PERMISSION_GRANTED){
            //locationActivity();
        }
        // 拒否していた場合、request する
        else {
            requestPermission(permissionType);
        }
    }

    // 許可を求める
    private void requestPermission(String permissionType) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissionType)) {
            ActivityCompat.requestPermissions(FusedGPSControlActivity.this,
                    new String[]{permissionType}, REQUEST_PERMISSION);

        } else {
            Toast toast = Toast.makeText(this, "許可されないとアプリが実行できません", Toast.LENGTH_SHORT);
            toast.show();

            ActivityCompat.requestPermissions(this, new String[]{permissionType}, REQUEST_PERMISSION);
        }
    }

    // 結果の受け取り
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION) {
            // 使用が許可された
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 何もしなくて良いのでは
                //locationActivity();

            } else {
                // それでも拒否された時の対応
                Toast toast = Toast.makeText(this, "これ以上なにもできません", Toast.LENGTH_SHORT);
                toast.show();
            }
        }
    }

    // Intent でLocation
    // 一つでやらせるため、変更が必要そう
    //private void locationActivity() {
    //    Intent intent = new Intent(getApplication(), LocationActivity.class);
    //    startActivity(intent);
    //}


}
