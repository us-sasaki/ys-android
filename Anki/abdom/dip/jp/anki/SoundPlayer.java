package abdom.dip.jp.anki;

import android.content.Context;
import android.media.MediaPlayer;

/**
 * 音を鳴らす便利クラス
 * ＜利用方法＞
 * 　SoundPlayer player = new SoundPlayer(Activity);
 * 　player.play(resid);
 * すでに音がなっている場合、停止後リソースを開放し、新しい
 * MediaPlayer インスタンスで音を鳴らす。
 * (MediaPlayerではresidを変更できないためこのような処理となる)
 *
 * @author Yusuke
 *
 */
public class SoundPlayer {
    private Context			context	=  null;
    private MediaPlayer		mp		= null;

    public SoundPlayer(Context context) {
        this.context = context;
    }

    public void play(int resid) {
        if (mp != null) {
            if (mp.isPlaying()) {
                mp.stop();
                try {
                    mp.prepare();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                } catch (java.io.IOException e) {
                    e.printStackTrace();
                }
            }
            mp.release();
            mp = null;
        }
        mp = MediaPlayer.create(context, resid); // in status "prepared"
        mp.start();
    }

    public void release() {
        if (mp != null) mp.release();
        mp = null;
    }
}

