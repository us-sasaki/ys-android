package abdom.dip.jp.anki;

import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URLDecoder;
import java.util.ArrayList;

/**
 * 暗記プログラム android 版
 *
 * @author Yusuke
 */
public class AnkiActivity extends Activity {

    /*------------------------
     * 定数テーブルのキー情報
     */
    private static final String INDEX = "i";
    private static final String PROB_FILE = "probFile";

/*--------------------
 * instance variables
 */
    private DBHelper    db      	= null;  // 固定値(index, probFile)を保存するDB
    private SoundPlayer player	= null; // サウンドプレイヤー
    private int index            = 0; // 出題中の問題
    private int lastCharacter   = -1; // レベルアップしたか判定用

    private ArrayList<Problem> problems = null; // 問題集

    /* 問題を格納する構造体 */
    private static class Problem {
        String prob;
        String ans;
        int rate;
    }

    private String probFile; // 問題ファイル名 probFile == null はproblemsがない状態を意味する
    private String probTitle = "問題を読み込んでいません"; // 問題のタイトル(ファイルの1行目)
    private String probDesc = "問題を読み込んでいません"; // 問題の説明(ファイルの2行目)

    private boolean isWaitForClick = false; // 問題に答えた後、メッセージを読ませている状態か

/*--------------------------
 * 状態遷移系オーバーライド
 */
    /**
     * onCreate
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        err("onCreate");
        setContentView(R.layout.activity_anki);

        // 変数の初期化
        problems = new ArrayList<Problem>();

        // 答え入力部分に ActionListener を登録
        EditText a = (EditText) findViewById(R.id.answerField);
        a.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (((event != null)&&(event.getKeyCode() == KeyEvent.KEYCODE_ENTER)&&(event.getAction() == KeyEvent.ACTION_UP))
                    ||(actionId == EditorInfo.IME_ACTION_DONE)) {
                    // ソフトキーボードを隠す
                    ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(), 0);
                    AnkiActivity.this.onClickEnter(v);
                    return true;
                }
                return false;
            }
        });

        // Intent でファイルを渡された時の処理
        // 2016/05/28 画面回転時にも呼ばれることが判明。対応。
        if (!getIntent().getAction().equals(Intent.ACTION_MAIN)) {
            Uri uri = getIntent().getData();
            if ("file".equals(uri.getScheme())) {
                probFile = uri.getPath();
                if (db == null) db = new DBHelper(this); // 必ずnullのはずだが、、と思ったが回転時nullでない
                String lastProbFile = db.getConstant(PROB_FILE);
                if (!probFile.equals(lastProbFile)) {
                    // 前回と異なるファイルが指定された場合、リセットし、ファイルを読み込む。
                    // 画面回転時や同じファイルが指定された場合、index をリセット
                    index = 0;
                    db.putConstant(PROB_FILE, probFile);
                    db.putConstant(INDEX, String.valueOf(index));
                    int retCode = readProb();
                    if (retCode < 0) errToast(retCode);
                    messageClear();
                }
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume(){
        super.onResume();
        err("onResume");

        initState(); // instance変数の値をDB/ファイルから読み込み、画面を再表示
    }

    @Override
    public void onPause() {
        super.onPause();
        err("onPause");

        saveState();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

/*-----------------------
 * instance state 保存系
 */
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        err("onRestoreInstanceState");
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        err("onSaveInstanceState");
        super.onSaveInstanceState(outState);

//        saveState();
    }

/*------------------
 * instance methods
 */
      private void initState() {
        err("initState");
        if (db == null) db = new DBHelper(this);
          err("db = "+db);

        if (player == null) player = new SoundPlayer(this);

        // index 復旧
        String indexStr = db.getConstant(INDEX);
        err("indexStr="+indexStr);

//        if (indexStr == null) {
//            index = 0;
//            db.putConstant(INDEX, String.valueOf(index));
//        }
        if (indexStr != null) index = Integer.parseInt(indexStr);

        // ファイル名復旧、読み込み
        probFile = db.getConstant(PROB_FILE);
        err("probFile="+probFile);
        if (probFile != null) {
            readProb();
            messageClear();
        }
        setProblem(); // 画面に設定、表示
    }

    /**
     * 画面回転のとき、FREETELではonPause()が呼ばれないため別だし
     */
    private void saveState() {
        writeProb();    // ファイルに問題を記録

        if (db != null) {
            db.putConstant(INDEX, String.valueOf(index));
            if (probFile != null) db.putConstant(PROB_FILE, probFile);
            db.close();
        }
        db = null;
        if (player != null) player.release();
        player = null;
    }

    /**
     * デバッグ用出力
     * @param str
     */
    private void err(String str) {
        Log.i("Anki.Yusuke:", str);
    }

    /**
     * 問題読み込み
     *
     * @return  retCode 0..success  -1..error
     */
    private int readProb() {
        err("readProb probFile="+probFile);
        problems.clear();
        if (probFile == null) return 0;

        int ret = 0;
        // ファイルがあるか
        File f = new File(probFile);
        if (f.exists()) {
            BufferedReader br = null;
            try {
                Reader r = new InputStreamReader(new FileInputStream(probFile),"Shift_JIS");
                br = new BufferedReader(r);

                // タイトル読み込み
                probTitle = br.readLine();
                probDesc = br.readLine();

                err("readProb() probTitle = "+probTitle+probDesc);

                while (true) {
                    String prob = br.readLine();
                    if ((prob == null) || ("".equals(prob))) break;
                    String ans = br.readLine();
                    String rateStr = br.readLine();
                    if ("".equals(rateStr)) rateStr="0";
                    err("問題/答え/rate："+prob+"/"+ans+"/"+rateStr);
                    int rate = Integer.parseInt(rateStr);

                    Problem p = new Problem();
                    p.prob = prob;
                    p.ans   = ans;
                    p.rate = rate;
                    problems.add(p);
                }
                err("readProb() db = "+ db);
                err("readProb() probFile = " + probFile);
//                db.putConstant(PROB_FILE, probFile); // 上手くいったので保存

            } catch (NumberFormatException nfe) {
                err( "問題ファイルフォーマットエラー(数値)" + nfe);
                probFile = null;
                ret = -1;
            } catch (NullPointerException npe) {
                err("問題ファイルフォーマットエラー(途中で終わっている)"+npe);
                probFile = null;
                ret = -2;
            } catch (IOException ioe) {
                err("問題ファイル読込エラー"+ioe);
                probFile = null;
                ret = -3;
            } catch(Exception e) {
                err( "問題ファイル読込時その他エラー" + e);
                probFile = null;
                ret = -4;
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException ioe) {
                        // ignored
                        probFile = null;
                        ret = -5;
                    }
                }
            }
        } else {
            err("問題が読み込まれていません");
            ret = 0;
        }
        return ret;
    }

    /**
     * ファイル書き込み
     */
    private void writeProb() {
        err("writeProb : probFile="+probFile);
        if (probFile == null) return;
        PrintWriter p = null;
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(probFile), "Shift_JIS");
            p = new PrintWriter(w);
            p.println(probTitle);
            p.println(probDesc);
            for (Problem prob : problems) {
                p.println(prob.prob);
                p.println(prob.ans);
                p.println(prob.rate);
            }
        } catch (IOException e) {
            Toast toast = Toast.makeText(this, "ファイル書き込みエラー:"+e,Toast.LENGTH_LONG);
            toast.show();
        } finally {
            if (p != null) p.close();
        }
    }

    /**
     * onCreateOptionsMenu
     */
//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        getMenuInflater().inflate(R.menu.activity_anki, menu);
//        return true;
//    }

    /**
     * 問題に答えた後、スクリーンをクリックした場合、メッセージをクリア
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (this.isWaitForClick) {
            messageClear();
            chooseNextProblem();
            this.isWaitForClick = false;
            return true;
        }
        return false;
    }

    /*
     * メッセージをクリア
     */
    private void messageClear() {
        TextView messageField = (TextView)findViewById(R.id.messageField1);
        messageField.setText(probTitle + "/" + probDesc);
    }

    /**
     * 次の問題を選択し、問題を表示させる
     */
    private void chooseNextProblem() {
        int count = problems.size();
        if (count == 0) {
            TextView af = (TextView)findViewById(R.id.answerField);
            af.setText("");
        } else {
            while (true) {
                index++;
                if (index >= count) index = 0;
                Problem prob = problems.get(index);
                int rate = prob.rate;
                if (((int) (Math.random() * 100) + 1) >= rate) break;
            }
            TextView af = (TextView) findViewById(R.id.answerField);
            af.setText("");
            setProblem();
        }
    }

    /**
     * 問題・Rate等に従い、画面表示させる
     */
    private void setProblem() {
        // Rateに応じて絵を設定
        lastCharacter = setPicture();

        // Rateに応じてレーティングバー(☆☆☆☆☆)を設定
        setRatingBar();

        TextView qField = (TextView)findViewById(R.id.questionField);
        if (problems.size() == 0){
            qField.setText("問題がありません");
            // 答えフィールドを無効に
            TextView af = (TextView)findViewById(R.id.answerField);
            af.setEnabled(false);
        } else {
            String q = problems.get(index).prob;
            qField.setText(q);

            // フォーカスを答えフィールドに
            TextView af = (TextView) findViewById(R.id.answerField);
            af.setEnabled(true);
            af.requestFocus();

            // IMEをオンにする
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(af, 0);
        }
    }

    /**
     * 暗記レベルを算出し、レベルに応じたキャラクタを表示させる
     */
    private int setPicture() {
        int totalRate = 0;
        for (Problem p : problems) totalRate += p.rate;

        int count = problems.size();

        // 暗記レベル(1～100)の測定
        int level = 0;
        if (count > 0) level = (int)(totalRate / count + 1);
        if (level > 100) level = 100;

        // レベルに応じたキャラクタ表示
        int maxIndex = 14;

        if (count < 5) maxIndex -= 10; // 問題数が少ないときはキャラが限られる
        else if (count < 10) maxIndex -= 8;
        else if (count < 15) maxIndex -= 5;
        else if (count < 20) maxIndex -= 3;
        else if (count < 40) maxIndex -= 1;

        if (maxIndex < 1) maxIndex = 1;

        int character = maxIndex * level / 100;
        if (character >= maxIndex) character = maxIndex - 1;

        // 絵を設定
        MyView pic = (MyView)findViewById(R.id.myview);
        pic.setPicture(this, character + 1);

        return character + 1;
    }

    /**
     * この問題に対するレートを算出し、RatingBar を設定する
     */
    private void setRatingBar() {
        RatingBar bar = (RatingBar)findViewById(R.id.ratingBar1);
        if (problems.size() == 0) {
            bar.setRating(0);
        } else {
            Problem prob = problems.get(index);

            // 0...0 101..5.5
            float r = ((float) prob.rate) * (5.5f / 101f);
            r = ((float) (int) (r * 2f)) / 2f;
            bar.setRating(r);
        }
    }

    /**
     * 入力ボタンを押したときの処理
     *
     * @param v
     */
    public void onClickEnter(View v) {
        if (this.isWaitForClick) {
            messageClear();
            chooseNextProblem();
            this.isWaitForClick = false;
            return;
        }
        if (problems.size() == 0) {
            // 答えフィールドはsetEnabled(false)なので、入力できないが「入力」ボタンを押すことができる
            return;
        }

        // IMEをオフにする
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(), 0);

        TextView af = (TextView)findViewById(R.id.answerField);
        String answer = af.getText().toString();

        Problem prob = problems.get(index);

        if (answer.equals(prob.ans)){
            // 正解時の処理
            // rateを上げる
            prob.rate = 100 - ((100 - prob.rate)*70/100);

            TextView messageField = (TextView)findViewById(R.id.messageField1);
            messageField.setText("正解！(レート:"+prob.rate+")");

            int character = setPicture();
            setRatingBar();

            // 正解音を鳴らす
            if (lastCharacter != character) {
                if (character < 4) player.play(R.raw.chimes);
                else if (character < 7) player.play(R.raw.harpme2);
                else if (character < 10) player.play(R.raw.finditem);
                else if (character < 11) player.play(R.raw.win);
                else if (character < 13) player.play(R.raw.levelup);
                else player.play(R.raw.fanfare);
            } else {
                player.play(R.raw.cashreg);
            }
        } else {
            // 不正解時の処理
            // rateを下げる
            prob.rate = prob.rate*75/100;

            TextView messageField = (TextView)findViewById(R.id.messageField1);
            messageField.setText("はずれ。正解は \"" + prob.ans + "\"(レート:"+prob.rate+")");

            int character = setPicture();
            setRatingBar();

            // はずれ音を鳴らす
            if (lastCharacter != character) {
                player.play(R.raw.curse);
            } else {
                player.play(R.raw.explode);
            }
        }
        this.isWaitForClick = true;
    }

/*--------------------------------------------
 * Intentを使ってテキストファイルを選択させる
 */

    // 識別用のコード
    private final static int CHOSE_FILE_CODE = 99892525;

    /**
     * 問題ファイル選択ボタン押下時の処理
     *
     * @param v view
     */
    public void onClickToInput(View v) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("file/*");
        startActivityForResult(intent, CHOSE_FILE_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            if (requestCode == CHOSE_FILE_CODE && resultCode == RESULT_OK) {
                String filePath = data.getDataString().replace("file://", "");
                err("filePath = "+filePath);
                probFile = URLDecoder.decode(filePath, "utf-8");
                err("onActivityResult : probFile = " +probFile);
                int retCode = readProb();
                err("onActivityResult : db = " + db);
                err("onActivityResult : probFile = "+probFile);
                if (db == null) db = new DBHelper(this);
                db.putConstant(PROB_FILE, probFile);
                if (retCode == 0) {
                    index = 0;
                    db.putConstant(INDEX, String.valueOf(index)); // 番号リセット
                } else {
                    errToast(retCode);
                }
                messageClear();
                initState();
            }
        }catch (java.io.UnsupportedEncodingException e) {
            Toast toast = Toast.makeText(this, "activity Result受け取りエラー:"+e,Toast.LENGTH_LONG);
            toast.show();
        }
    }

    private void errToast(int retCode) {
        String errStr = "その他エラー";
        switch (retCode) {

            case -1:
                errStr = "ファイルフォーマットエラー(数値)";
                break;
            case -2:
                errStr = "ファイルが途中で終わっています";
                break;
            case -3:
                errStr = "ファイルI/Oエラー";
                break;
            case -5:
                errStr = "ファイルクローズ失敗";
                break;
            default:
        }

        Toast toast = Toast.makeText(this, probFile + ":" + errStr,Toast.LENGTH_LONG);
        toast.show();
    }
}
