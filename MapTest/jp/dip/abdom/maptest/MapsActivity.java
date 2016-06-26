package jp.dip.abdom.maptest;

import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.List;

/**
 *  GPS Logger 系の csv ファイルを読み込み、Google Map 上に表示させる。
 *
 *  @author Yusuke
 */
public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, AdapterView.OnItemSelectedListener {

    public static final int MENU_SELECT_OPEN = 0;
    public static final int MENU_SELECT_OPTION = 1;

    private GoogleMap mMap;
    private Spinner spinner;
    private LatLngReader llreader = null;
    private String selectedFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        spinner = (Spinner)findViewById(R.id.fileList);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        llreader = new LatLngReader(mMap);

        // 特定のフォルダにある、GPSLog*txt にマッチするファイルのリストをspinnerに登録
        ArrayAdapter<String> flist = new ArrayAdapter<String>(this, R.layout.file_item, llreader.files());
        spinner.setAdapter(flist);
        spinner.setOnItemSelectedListener(this);
        selectedFile = "GPSLog.txt";

        //
        // 軌跡を追加
        //
        addPolyline();

        // Add a marker in Sydney and move the camera
        LatLng myHome = new LatLng(35.6317907,139.3704638);
        mMap.addMarker(new MarkerOptions().position(myHome).title("Marker in Home"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myHome, 15));

    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Spinner s = (Spinner)parent;
        if (s != spinner) {
            Toast.makeText(this, "Internal Error", Toast.LENGTH_SHORT);
            return;
        }
        selectedFile = (String)s.getSelectedItem();
        addPolyline();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        Toast.makeText(this, "nothing selected", Toast.LENGTH_SHORT);
    }


    /**
     * 軌跡を追加します
     */
    private void addPolyline() {
        mMap.clear();
        llreader.readFile(selectedFile);
    }

}
