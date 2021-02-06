package com.example.myapplication;

        import android.content.Context;
        import android.graphics.Canvas;
        import android.graphics.Color;
        import android.graphics.Paint;
        import android.graphics.RectF;
        import android.util.AttributeSet;
        import android.util.TypedValue;
        import android.view.View;

        import java.util.LinkedList;
        import java.util.List;

//simple View providing a render callback to other classes
public class OverlayView extends View {
    private final List<DrawCallback> callbacks = new LinkedList<DrawCallback>();
    //private Paint paint;
    //private float detectionResultsViewHeight;
    //private ImageClassifier.Recognition detectionResult;
    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // paint = new Paint();
    }

    //private int INPUT_SIZE=300;

    //interface to define callback of client class
    public interface DrawCallback {
        public void drawCallback(Canvas canvas);
    }

    public void addCallback(DrawCallback callback) {
        callbacks.add(callback);
    }

    @Override
    public synchronized void draw(final Canvas canvas) {
        //super.draw(canvas);
        for (final DrawCallback callback : callbacks) {
            callback.drawCallback(canvas);
        }
    }

    /*
    //draw boundary box for detected objects
    @Override
    public synchronized void draw(Canvas canvas) {
        super.draw(canvas);
        for (final DrawCallback callback : callbacks) {
            callback.drawCallback(canvas);
        }

        /*RectF box = recalculateSize(detectionResult.getLocation()); //get the location of the detected objects
        String title = detectionResult.getTitle() + String.format(" %2.2f", detectionResult.getConfidence()*100) + "%"; //text to display
        paint.setColor(Color.BLUE); //color of text
        paint.setStyle(Paint.Style.STROKE); //set style
        canvas.drawRect(box, paint); //draw rectangle for boundary box
        paint.setStrokeWidth(3.0f); // thickness
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        canvas.drawText(title, box.left, box.bottom, paint); //location of text
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics())); // text size

    }

    public void setdetectionResult(ImageClassifier.Recognition detectionResult) {
        this.detectionResult = detectionResult;
        postInvalidate();
    }

    //calculate rectangle coordinate
    public RectF recalculateSize(RectF rect) {
        int padding = 5;
        float overlayViewHeight = getHeight() - detectionResultsViewHeight;
        float sizeToMultiply = Math.min((float) getWidth() / (float) INPUT_SIZE, overlayViewHeight / (float) INPUT_SIZE);

        float offsetX = (getWidth() - INPUT_SIZE * sizeToMultiply) / 2;
        float offsetY = (overlayViewHeight - INPUT_SIZE * sizeToMultiply) / 2 + detectionResultsViewHeight;

        //find the coordinate for 4 points
        float left = Math.max(padding, sizeToMultiply * rect.left + offsetX);
        float top = Math.max(offsetY + padding, sizeToMultiply * rect.top + offsetY);

        float right = Math.min(rect.right * sizeToMultiply, getWidth() - padding);
        float bottom = Math.min(rect.bottom * sizeToMultiply + offsetY, getHeight() - padding);

        RectF newRect = new RectF(left, top, right, bottom);
        return newRect;
    }
 */
}
