package uk.org.rc0.helloandroid;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;

public class Map extends View {

  private final Paint my_paint;

  public Map(Context context, AttributeSet attrs) {
    super(context, attrs);
    my_paint = new Paint();
    my_paint.setStrokeWidth(2);
    my_paint.setColor(Color.RED);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    canvas.drawText("hello", getWidth() / 2, getHeight() / 2, my_paint);
  }
}

