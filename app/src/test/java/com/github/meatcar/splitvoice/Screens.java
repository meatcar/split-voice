package com.github.meatcar.splitvoice;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.TypedValue;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;

final class Screens {
    private Screens() {}

    /** Renders the whole scrolling screen with its 16dp gutter, so CI artifacts show every state. */
    static void capture(Activity activity, String name) throws Exception {
        View root = activity.findViewById(R.id.content);
        int width = 360;
        int gutter = 16;
        root.measure(View.MeasureSpec.makeMeasureSpec(width - 2 * gutter, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
        Bitmap bitmap = Bitmap.createBitmap(width, root.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        var background = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.colorBackground, background, true);
        canvas.drawColor(background.data);
        canvas.translate(gutter, 0);
        root.draw(canvas);
        File directory = new File("build/reports/ui");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        bitmap.recycle();
    }
}
