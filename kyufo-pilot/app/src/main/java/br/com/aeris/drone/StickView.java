package br.com.aeris.drone;

import android.content.Context;
import android.graphics.*;
import android.view.*;

final class StickView extends View {
    interface Listener {void update(float x,float y);}
    private final Paint p=new Paint(3);
    private Listener listener;
    private final String label;
    private float x=0,y=0;
    StickView(Context c,String label){super(c);this.label=label;setMinimumHeight((int)(190*getResources().getDisplayMetrics().density));}
    void setListener(Listener l){listener=l;}
    void reset(){x=0;y=0;invalidate();if(listener!=null)listener.update(0,0);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float w=getWidth(),h=getHeight(),radius=Math.min(w,h)*.36f,cx=w/2,cy=h*.53f;
        p.setColor(Color.rgb(28,44,62));p.setStyle(Paint.Style.FILL);canvas.drawCircle(cx,cy,radius,p);
        p.setColor(Color.rgb(66,101,124));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);canvas.drawCircle(cx,cy,radius,p);
        canvas.drawLine(cx-radius,cy,cx+radius,cy,p);canvas.drawLine(cx,cy-radius,cx,cy+radius,p);
        p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(70,223,227));canvas.drawCircle(cx+x*radius,cy-y*radius,radius*.29f,p);
        p.setColor(Color.WHITE);p.setTextSize(13*getResources().getDisplayMetrics().scaledDensity);p.setTextAlign(Paint.Align.CENTER);canvas.drawText(label,cx,24*getResources().getDisplayMetrics().density,p);
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent e) {
        if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){reset();return true;}
        if(e.getActionMasked()!=MotionEvent.ACTION_DOWN&&e.getActionMasked()!=MotionEvent.ACTION_MOVE)return true;
        float radius=Math.min(getWidth(),getHeight())*.36f;
        x=Math.max(-1,Math.min(1,(e.getX()-getWidth()/2)/radius));
        y=Math.max(-1,Math.min(1,(getHeight()*.53f-e.getY())/radius));
        invalidate();if(listener!=null)listener.update(x,y);
        return true;
    }
}
