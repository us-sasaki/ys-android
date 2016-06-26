package abdom.dip.jp.anki;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * @author Yusuke
 *
 */
public class MyView extends View {
    private Bitmap bitmap = null;

    /**
     * @param context   context
     * @param attrs     attributes
     * @param defStyle  defStyle
     */
    public MyView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    /**
     * @param context   context
     * @param attrs     attrs
     */
    public MyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    /**
     * @param context   context
     */
    public MyView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        Resources res = context.getResources();
        bitmap = BitmapFactory.decodeResource(res, R.drawable.i1);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
//        canvas.drawColor(Color.WHITE);
        if (bitmap != null) {
            Paint paint = new Paint();
            canvas.drawBitmap(bitmap,  0,  0, paint);
        }
    }

    /**
     * 設定した絵番号の絵に変更する
     * 絵番号は1-14が有効で、これ以外の値の場合、1の絵が設定される。
     *
     * @param context	リソースを保持しているContext
     * @param picno		絵番号(1-14)
     */
    public void setPicture(Context context, int picno) {
        Resources res = context.getResources();

        int id;
        switch (picno) {

            case 1:
                id = R.drawable.i1; break;
            case 2:
                id = R.drawable.i2; break;
            case 3:
                id = R.drawable.i3; break;
            case 4:
                id = R.drawable.i4; break;
            case 5:
                id = R.drawable.i5; break;
            case 6:
                id = R.drawable.i6; break;
            case 7:
                id = R.drawable.i7; break;
            case 8:
                id = R.drawable.i8; break;
            case 9:
                id = R.drawable.i9; break;
            case 10:
                id = R.drawable.i10; break;
            case 11:
                id = R.drawable.i11; break;
            case 12:
                id = R.drawable.i12; break;
            case 13:
                id = R.drawable.i13; break;
            case 14:
                id = R.drawable.i14; break;
            default:
                id = R.drawable.i1; break;
        }
        bitmap = BitmapFactory.decodeResource(res, id);

        // 表示更新(UIスレッドから呼ぶ場合このメソッドらしい)
        invalidate();
    }

}
