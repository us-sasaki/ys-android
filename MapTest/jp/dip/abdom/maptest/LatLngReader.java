package jp.dip.abdom.maptest;

import android.net.Uri;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Yusuke on 2016/05/04.
 */
public class LatLngReader {
    // 読み込むファイルのpath (CSV形式、読み取りのみ)
    public static final File LOG_FILE = new File("/storage/sdcard0/Download/");
	// 同じ色で描画する線を構成する点の数。両端は Marker がつく。
    public static final int POLY_POINTS = 30;
    public static final int[] COLOR = new int[16*16*16];
    static {
        int b = 0;
        int bs = 7;
        int r = 0;
        int rs = 13;
        int g = 0;
        int gs = 19;
        for (int i = 0; i < 16 * 16 * 16; i++) {
            COLOR[i] = 0xFF000000 + b * 65536 + r * 256 + g;
            b += bs;
            if ((b < 0) || (b > 255)) {
                bs = -bs;
                b += bs;
            }
            r += rs;
            if ((r < 0) || (r > 255)) {
                rs = -rs;
                r += rs;
            }
            g += gs;
            if ((g < 0) || (g > 255)) {
                gs = -gs;
                g += gs;
            }
        }
    }

    static class Plot {
        double latitude;
        double longitude;
        double distance; // 単位は m
        double velocity; // 単位は m/s
        boolean isOutlier = false;
        long time; // 単位はmsec
        String date;

        public Plot(double latitude, double longitude, boolean isOutlier, long time, String date) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.isOutlier = isOutlier;
            this.time = time;
            this.date = date;
        }
    }

    protected GoogleMap mMap;

/*-------------
 * constructor
 */
    public LatLngReader(GoogleMap map) {
        mMap = map;
    }

/*------------------
 * instance methods
 */
    public String[] files() {
        if (!LOG_FILE.isDirectory()) {
            return new String[]{"no file matches"};
        }
        List<String> list = new ArrayList<String>();
        String[] l = LOG_FILE.list();
        for (String s : l) {
            if (s.matches("GPSLog.+txt")) list.add(s);
        }
        Collections.sort(list);

        return list.toArray(new String[0]);
    }


    /**
     * outlierを除いて、前地点との距離、速度をPlotに設定
     *
     * @param list
     * @return
     */
    private List<Plot> calcDistanceVelocity(List<Plot> list) {
        // distance, velocity を格納
        // はじめの要素はともに必ず 0 とする。

        Plot prePlot = null;
        for (Plot plot : list) {
            if (plot.isOutlier) continue;
            if (prePlot == null) prePlot = plot;
            plot.distance = Coord.calcDistHubeny(plot, prePlot);
            long msec = plot.time - prePlot.time;
            if (msec == 0L) {
                plot.velocity = 0d;
            } else {
                plot.velocity = plot.distance * 1000d / msec;
            }
            prePlot = plot;
        }
        return list;
    }

    /**
     * ファイルからGPS情報を読み取る
     * しかしながら、かなり飛び飛びの場所が発生する。
     * 速度を計算し、ワープしているようなものは除外(補正)が必要。
     * (参考)飛行機の加速度：5.6m/s^2
     */
    public void readFile(String fname) {
        // ファイルから読み込み
        List<Plot> list = readFileImpl(fname);
        if ((list == null)||(list.size() == 0)) return;

        list = calcDistanceVelocity(list);

        // 外れ値をカット
        //int outlierCount = cutOutlierBySmirnovGrubbs(list); // この方法はいまいちなことが判明
        // 
        // 明らかに飛んでいるものはカットできている。
        // 近距離の不自然なワープ、Ｕターン、位置ずれは残っている
        int outlierCount = cutOutlierByYusuke(list);

        // そのうち、道路情報を取得できたら、速度から想定される
        // 経路を推定し、外れ値を補完値で埋めたい

        // GoogleMap に設定
        PolylineOptions p = new PolylineOptions();
        int n = 0;
        int col = 0;

        for (Plot plot : list) {
            if (!plot.isOutlier) { // 外れ値ならスキップ
                LatLng pos = new LatLng(plot.latitude, plot.longitude);
                p.add(pos);
                n++;
                if (n >= POLY_POINTS) {
                    // Polylineの色を設定
                    p.color(COLOR[col]);
                    col++; // 次の色
                    if (col >= COLOR.length) col = 0;

                    // 時刻を表すマーカーも設定
                    mMap.addMarker(new MarkerOptions().position(pos).title(plot.date));

                    mMap.addPolyline(p);
                    n = 0;
                    p = new PolylineOptions();
                    p.add(pos);
                }
            }
        }

    }

    /**
     * CSVファイルを読み込み、PlotのListに格納する。
     *
     * @return CSVファイルを読み込んだ結果
     */
    public List<Plot> readFileImpl(String fname) {
        List<Plot> list = new ArrayList<Plot>();

        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(new File(LOG_FILE, fname)));

            // タイトル行読み捨て
            br.readLine();
            while (true) {
                String line = br.readLine();
                if (line == null) break;
                String[] columns = line.split(",");
                double latitude = Double.parseDouble(columns[3]);
                double longitude = Double.parseDouble(columns[4]);
                list.add(new Plot(latitude, longitude, false, Long.parseLong(columns[1]), columns[0]));
            }
        } catch (IOException ioe) {
            Log.d("LatLngReader", "IOE:"+ioe);
        } catch (NumberFormatException nfe) {
            Log.d("LatLngReader", "NFE:"+nfe);
        } finally {
            try {
                if (br != null) br.close();
            } catch (IOException ignored) {

            }
        }
        return list;
    }

/*
 * 外れ値カットプログラム
 *
 */

    private static final double ACCEPTANCE = 0.0000000000000001d;
    /**
     * 外れ値をカットする。
     * GPSの特性として、検知できないとき前回と同じ値を返す挙動をすることを考慮。
     * ①前回と全く同じ値の場合、カットする
     * ②ワープしている(バスでありうる最高速度 42m/s 以上のものはカット
     *
     * これで、大きな外しはなくなった。ただぱっと見て変じゃない、程度。
     * 徒歩、バス等の軌跡はかなり不自然。
     */
    private int cutOutlierByYusuke(List<Plot> list) {
        int outlierCount = 0;
        // 前回と同じ点は削除
        Plot prePlot = null;
        for (Plot p : list) {
            if (p.isOutlier) continue;
            if (prePlot == null) {
                prePlot = p;
                continue; // 初回はカットできない
            }
            if ((Math.abs(p.latitude - prePlot.latitude) < ACCEPTANCE) &&
                (Math.abs(p.longitude - prePlot.longitude) < ACCEPTANCE) ) {
                p.isOutlier = true;
            } else {
                prePlot = p;
            }
        }

        // ワープしているものを除去(適当, 42m/s (150km/h) 以上のものを除去)
        boolean nextNeedFix = false;
        prePlot = null;
        for (Plot p : list) {
            if (p.isOutlier) continue;
            if (nextNeedFix) {
                // ひとつ前の plot が破棄されたため、dist,velo 再計算
                p.distance = Coord.calcDistHubeny(p, prePlot);
                long msec = p.time - prePlot.time;
                if (msec == 0L) {
                    p.velocity = 0d;
                } else {
                    p.velocity = p.distance * 1000d / msec;
                }
            }


            if (p.velocity > 42) {
                p.isOutlier = true;
                nextNeedFix = true;
                outlierCount++;
            } else {
                // 問題ない場合、prePlot を設定
                prePlot = p;
                nextNeedFix = false;
            }
        }
        return outlierCount;
    }
    
/*------------------------------------------
 * 以下、没になったスミルノフ・グラブス検定
 */
    
    /**
     * 外れ値をカットする(スミルノフ・グラブス検定)
     *
     * 単純に速度に対して適用している。
     * 色々と問題あり。GPSの挙動として、ワープしてしばらくそのまま、またワープして戻る
     * ようなことがあるが、ワープの行き(これはカットしてよい)、ワープからの帰り場(カットNG)
     * をともにカットしてしまう。しばらくそのまま、のところはカットされないことが多い。
     *
     * @return 外れ値の数
     */
    private int cutOutlierBySmirnovGrubbs(List<Plot> list) {
        int initSize = list.size(); // 不変のはずだが、今後数を変えるかも知れないので
        int outlierCount = 0;
        for (int i = 0; i < initSize / 3; i++) { // 多くとも initSize/3 までしか除外しない
            if (!cutOneOutlier(list)) break; // 再帰的に計算することが必要らしい
            outlierCount++;
        }
        Log.d("LatLngReader:cutOutlier", "outlierCount="+outlierCount);

        return outlierCount;
    }

    /**
     *
     * Plotの速度に関し、スミルノフ・グラブス検定を行う
     * ここでは、スミルノフ・グラブスのgammaを超えたサンプルのうち、
     * 最も外れたもの１つを除外(isOutlier=true)する。
     *
     * https://ja.wikipedia.org/wiki/%E5%A4%96%E3%82%8C%E5%80%A4
     * 外れ値は 1/20～1/30 くらいあるので、t=95% とする
     *
     *
     * @param list Plotのリスト
     * @return 除外された場合true, 外れ値がなく除外されなかった場合false
     */
    private boolean cutOneOutlier(List<Plot> list) {

        // 要素数と平均を求める
        int cnt = 0;
        double sum = 0d;

        for (Plot plot : list) {
            if (!plot.isOutlier) {
                sum += plot.velocity;
                cnt++;
            }
        }

        Log.d("LatLngReader:cutOutlier", "cnt="+cnt+" sum="+sum);

        // 平均値
        double mean = sum / cnt;
        Log.d("LatLngReader:cutOutlier", "mean="+mean);

        // 分散を求める
        double variance = 0d;

        for (Plot plot : list) {
            if (!plot.isOutlier) {
                double deviation = plot.velocity - mean;
                variance += deviation * deviation;
            }
        }

        // 標準偏差を求める
        double sd = Math.sqrt(variance / cnt);
        Log.d("LatLngReader:cutOutlier", "sd="+sd);

        // スミルノフ・グラブスのガンマを求める
        double t = 0.95d; // 定数
        double gamma = ((cnt-1) * t)/Math.sqrt(cnt * (cnt-2) + cnt * t * t);
        gamma = 2.5d;
        Log.d("LatLngReader:cutOutlier", "gamma="+gamma);

        // 判定する
        double mostOut = 0d;
        Plot mostOutlied = null;
        for (Plot plot : list) {
            if (!plot.isOutlier) {
                // 外れ具合を測定。外れ具合が gamma 以上のものを候補として抽出
                double e = Math.abs((plot.velocity - mean) / sd);
                if ((e > mostOut)&&(e >= gamma)) {
                    mostOut = e;
                    mostOutlied = plot;
                }
            }
        }
        if (mostOutlied != null) {
            // 最も外れたものを outlier と判定
            mostOutlied.isOutlier = true;
            return true;
        } else {
            return false;
        }
    }

}
