package com.lumepdf.reader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.pdf.PdfRenderer;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int OPEN_PDF = 101;
    private static final int BLUE = Color.rgb(54,108,244);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayList<PageSize> sizes = new ArrayList<>();
    private final ArrayList<FrameLayout> frames = new ArrayList<>();
    private final ArrayList<ImageView> images = new ArrayList<>();
    private final HashSet<Integer> pendingPages = new HashSet<>();

    private LinearLayout root;
    private PdfScrollView vertical;
    private HorizontalScrollView horizontal;
    private TextView pageCounter;
    private PdfRenderer renderer;
    private ParcelFileDescriptor descriptor;
    private Uri currentUri;
    private String currentName = "Documento.pdf";
    private int documentToken = 0;
    private int zoomToken = 0;
    private float zoom = 1.0f;
    private boolean dark;

    private static final class PageSize {
        final int width, height;
        PageSize(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        dark = getPreferences(MODE_PRIVATE).getBoolean("dark", false);
        styleBars();
        if (state != null && state.getParcelable("document") != null) {
            openPdf((Uri) state.getParcelable("document"));
        } else {
            showHome();
            handleExternalIntent(getIntent());
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleExternalIntent(intent);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (currentUri != null) out.putParcelable("document", currentUri);
    }

    private int dp(float v) {
        return (int)(v * getResources().getDisplayMetrics().density + .5f);
    }
    private int background() { return dark ? 0xFF11151D : 0xFFF6F8FC; }
    private int panel() { return dark ? 0xFF1B2230 : Color.WHITE; }
    private int ink() { return dark ? 0xFFF3F6FF : 0xFF222A3B; }
    private int muted() { return dark ? 0xFFA7B2C4 : 0xFF778397; }

    private void styleBars() {
        getWindow().setStatusBarColor(panel());
        getWindow().setNavigationBarColor(panel());
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private void newRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background());
        setContentView(root);
        styleBars();
    }

    private ImageButton icon(int drawable, String label, View.OnClickListener click) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(drawable);
        b.setColorFilter(ink());
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setPadding(dp(11), dp(11), dp(11), dp(11));
        b.setBackground(rounded(panel(), 13));
        b.setContentDescription(label);
        b.setOnClickListener(click);
        return b;
    }

    private LinearLayout header(boolean reading) {
        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(panel());
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(5), dp(12), dp(5));
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(62)));

        if (reading) {
            bar.addView(icon(R.drawable.ic_back, "Voltar", v -> closeDocument()),
                    new LinearLayout.LayoutParams(dp(44), dp(44)));
        }
        LinearLayout titleGroup = new LinearLayout(this);
        titleGroup.setOrientation(LinearLayout.VERTICAL);
        titleGroup.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0,-1,1);
        titleLp.leftMargin = dp(9);
        bar.addView(titleGroup, titleLp);
        TextView title = text(reading ? currentName : "LUME PDF", reading ? 15 : 21, ink(), true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleGroup.addView(title, new LinearLayout.LayoutParams(-1, dp(26)));
        TextView sub = text(reading ? "Leitor de documentos" : "Simples. Leve. Seu.", 11, muted(), false);
        titleGroup.addView(sub, new LinearLayout.LayoutParams(-1, dp(19)));

        if (reading) {
            bar.addView(icon(R.drawable.ic_share, "Compartilhar PDF", v -> sharePdf()),
                    new LinearLayout.LayoutParams(dp(44), dp(44)));
            bar.addView(icon(R.drawable.ic_print, "Imprimir PDF", v -> printPdf()),
                    new LinearLayout.LayoutParams(dp(44), dp(44)));
        }
        ImageButton more = icon(R.drawable.ic_more, "Mais opções", null);
        more.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, more);
            if (reading) menu.getMenu().add("Abrir outro PDF").setOnMenuItemClickListener(item -> {
                pickPdf(); return true;
            });
            menu.getMenu().add(dark ? "Modo claro" : "Modo escuro").setOnMenuItemClickListener(item -> {
                dark = !dark;
                getPreferences(MODE_PRIVATE).edit().putBoolean("dark",dark).apply();
                if (currentUri == null) showHome(); else buildReader();
                return true;
            });
            menu.getMenu().add("Sobre o LUME PDF").setOnMenuItemClickListener(item -> {
                new AlertDialog.Builder(this).setTitle("LUME PDF")
                    .setMessage("Versão 1.0\nUm leitor de PDF offline, sem anúncios e sem cadastro.")
                    .setPositiveButton("OK",null).show();
                return true;
            });
            menu.show();
        });
        bar.addView(more, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return bar;
    }

    private void showHome() {
        currentUri = null;
        zoom = 1.0f;
        newRoot();
        header(false);
        FrameLayout area = new FrameLayout(this);
        root.addView(area, new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(-1,-2, Gravity.CENTER);
        centerLp.leftMargin = dp(26);
        centerLp.rightMargin = dp(26);
        area.addView(center,centerLp);

        TextView document = text("PDF", 29, BLUE, true);
        document.setGravity(Gravity.CENTER);
        document.setBackground(rounded(dark ? 0xFF203352 : 0xFFE7EEFF, 25));
        center.addView(document,new LinearLayout.LayoutParams(dp(100),dp(116)));

        TextView big = text("Seus PDFs, sem complicação.", 23, ink(),true);
        big.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bigLp = new LinearLayout.LayoutParams(-1,-2);
        bigLp.topMargin = dp(29);
        center.addView(big,bigLp);

        TextView hint = text("Abra, leia, compartilhe ou imprima.\nTudo em um lugar só.",14,muted(),false);
        hint.setGravity(Gravity.CENTER);
        hint.setLineSpacing(dp(4),1.0f);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1,-2);
        hintLp.topMargin = dp(12);
        center.addView(hint,hintLp);

        TextView open = text("Abrir PDF",16,Color.WHITE,true);
        open.setGravity(Gravity.CENTER);
        open.setBackground(rounded(BLUE,17));
        open.setOnClickListener(v -> pickPdf());
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1,dp(54));
        btnLp.topMargin = dp(35);
        center.addView(open,btnLp);

        TextView footer = text("Offline  •  Privado  •  Sem anúncios",12,muted(),false);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer,new LinearLayout.LayoutParams(-1,dp(52)));
    }

    private void showLoading() {
        newRoot();
        header(false);
        LinearLayout content = new LinearLayout(this);
        content.setGravity(Gravity.CENTER);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        ProgressBar loading = new ProgressBar(this);
        content.addView(loading,new LinearLayout.LayoutParams(dp(40),dp(40)));
        TextView msg = text("Abrindo documento...",15,muted(),false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2,-2);
        lp.topMargin = dp(16);
        content.addView(msg,lp);
    }

    private void pickPdf() {
        Intent select = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        select.setType("application/pdf");
        select.addCategory(Intent.CATEGORY_OPENABLE);
        select.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(select, OPEN_PDF);
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req,result,data);
        if (req == OPEN_PDF && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) { }
            openPdf(uri);
        }
    }

    private void handleExternalIntent(Intent intent) {
        if (intent == null) return;
        Uri uri = null;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) uri = intent.getData();
        else if (Intent.ACTION_SEND.equals(action)) {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            } else {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
        }
        if (uri != null) openPdf(uri);
    }

    private String nameFor(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,null,null,null,null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return cursor.getString(i);
            }
        } catch (Exception ignored) { }
        String last = uri.getLastPathSegment();
        return last == null ? "Documento.pdf" : last;
    }

    private void openPdf(Uri uri) {
        showLoading();
        int token = ++documentToken;
        ++zoomToken;
        pendingPages.clear();
        PdfRenderer oldRenderer = renderer;
        ParcelFileDescriptor oldFd = descriptor;
        renderer = null;
        descriptor = null;
        currentUri = null;
        worker.execute(() -> {
            try {
                if (oldRenderer != null) oldRenderer.close();
            } catch (Exception ignored) { }
            try {
                if (oldFd != null) oldFd.close();
            } catch (Exception ignored) { }

            ParcelFileDescriptor fd = null;
            PdfRenderer pdf = null;
            try {
                fd = getContentResolver().openFileDescriptor(uri,"r");
                if (fd == null) throw new IOException("Arquivo indisponível");
                pdf = new PdfRenderer(fd);
                ArrayList<PageSize> found = new ArrayList<>();
                for (int i=0;i<pdf.getPageCount();i++) {
                    try (PdfRenderer.Page p = pdf.openPage(i)) {
                        found.add(new PageSize(p.getWidth(),p.getHeight()));
                    }
                }
                PdfRenderer finalPdf = pdf;
                ParcelFileDescriptor finalFd = fd;
                runOnUiThread(() -> {
                    if (token != documentToken || isFinishing() || isDestroyed()) {
                        worker.execute(() -> {
                            try { finalPdf.close(); finalFd.close(); } catch (Exception ignored) { }
                        });
                        return;
                    }
                    renderer = finalPdf;
                    descriptor = finalFd;
                    sizes.clear();
                    sizes.addAll(found);
                    currentUri = uri;
                    currentName = nameFor(uri);
                    zoom = 1.0f;
                    buildReader();
                });
            } catch (Exception e) {
                try { if(pdf != null) pdf.close(); } catch (Exception ignored) { }
                try { if(fd != null) fd.close(); } catch (Exception ignored) { }
                runOnUiThread(() -> {
                    if (token == documentToken) {
                        showHome();
                        Toast.makeText(this,"Não foi possível abrir este PDF.",Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void buildReader() {
        if (renderer == null) { showHome(); return; }
        ++zoomToken;
        pendingPages.clear();
        frames.clear();
        images.clear();
        newRoot();
        header(true);
        horizontal = new HorizontalScrollView(this);
        horizontal.setFillViewport(true);
        horizontal.setHorizontalScrollBarEnabled(false);
        root.addView(horizontal, new LinearLayout.LayoutParams(-1,0,1));

        vertical = new PdfScrollView();
        vertical.setFillViewport(true);
        vertical.setVerticalScrollBarEnabled(false);
        vertical.setBackgroundColor(background());
        horizontal.addView(vertical, new FrameLayout.LayoutParams(-2,-1));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(14),dp(10),dp(14),dp(16));
        vertical.addView(column,new ScrollView.LayoutParams(-2,-2));

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int pageWidth = Math.max(dp(170),(int)((screenWidth-dp(28))*zoom));
        for (int i=0;i<sizes.size();i++) {
            PageSize size = sizes.get(i);
            int pageHeight = Math.max(1, Math.round(pageWidth*(size.height/(float)size.width)));
            FrameLayout frame = new FrameLayout(this);
            frame.setBackgroundColor(Color.WHITE);
            frame.setElevation(dp(2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(pageWidth,pageHeight);
            lp.bottomMargin = dp(12);
            column.addView(frame,lp);
            ImageView image = new ImageView(this);
            image.setBackgroundColor(Color.WHITE);
            image.setScaleType(ImageView.ScaleType.FIT_XY);
            frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
            frames.add(frame);
            images.add(image);
        }

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        bottom.setBackgroundColor(panel());
        root.addView(bottom,new LinearLayout.LayoutParams(-1,dp(47)));
        pageCounter = text("Página 1 de "+sizes.size(),12,muted(),true);
        pageCounter.setGravity(Gravity.CENTER);
        bottom.addView(pageCounter, new LinearLayout.LayoutParams(-1,-1));
        vertical.setOnScrollChangeListener((View.OnScrollChangeListener) (v, sx, sy, oldx, oldy) -> renderVisible());
        vertical.post(this::renderVisible);
    }

    private void renderVisible() {
        if (renderer == null || vertical == null || frames.isEmpty()) return;
        int from = vertical.getScrollY() - vertical.getHeight();
        int to = vertical.getScrollY() + 2*vertical.getHeight();
        int middle = vertical.getScrollY() + vertical.getHeight()/2;
        int current = 0;
        int token = documentToken;
        int revision = zoomToken;
        PdfRenderer pdf = renderer;
        for (int i=0;i<frames.size();i++) {
            FrameLayout frame = frames.get(i);
            int top = frame.getTop();
            int bottom = frame.getBottom();
            if (top <= middle) current = i;
            ImageView image = images.get(i);
            boolean nearby = bottom >= from && top <= to;
            if (!nearby) {
                if (image.getDrawable() != null) image.setImageDrawable(null);
            } else if (image.getDrawable() == null && !pendingPages.contains(i)) {
                final int index = i;
                int displayW = frame.getWidth();
                if (displayW == 0) continue;
                pendingPages.add(index);
                worker.execute(() -> {
                    if (token != documentToken || revision != zoomToken) return;
                    Bitmap bmp = null;
                    try (PdfRenderer.Page page = pdf.openPage(index)) {
                        int desiredWidth = Math.min(2500,Math.max(1,Math.round(displayW*1.35f)));
                        int desiredHeight = Math.max(1,Math.round(desiredWidth*(page.getHeight()/(float)page.getWidth())));
                        if ((long)desiredWidth*desiredHeight > 6500000L) {
                            float shrink = (float)Math.sqrt(6500000.0/(desiredWidth*(double)desiredHeight));
                            desiredWidth = Math.max(1,(int)(desiredWidth*shrink));
                            desiredHeight = Math.max(1,(int)(desiredHeight*shrink));
                        }
                        bmp = Bitmap.createBitmap(desiredWidth,desiredHeight,Bitmap.Config.ARGB_8888);
                        bmp.eraseColor(Color.WHITE);
                        page.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    } catch (Exception ignored) { }
                    Bitmap finalBitmap = bmp;
                    runOnUiThread(() -> {
                        pendingPages.remove(index);
                        if (token == documentToken && revision == zoomToken && index < images.size()) {
                            if (finalBitmap != null) images.get(index).setImageBitmap(finalBitmap);
                        } else if (finalBitmap != null) finalBitmap.recycle();
                    });
                });
            }
        }
        if (pageCounter != null) pageCounter.setText(String.format(Locale.getDefault(),
                "Página %d de %d",current+1,sizes.size()));
    }

    private class PdfScrollView extends ScrollView {
        private final ScaleGestureDetector scaleDetector;
        private float proposedZoom;
        PdfScrollView() {
            super(MainActivity.this);
            proposedZoom = zoom;
            scaleDetector = new ScaleGestureDetector(MainActivity.this,
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                    proposedZoom = zoom;
                    return true;
                }
                @Override public boolean onScale(ScaleGestureDetector detector) {
                    proposedZoom = Math.max(1f,Math.min(3f,proposedZoom*detector.getScaleFactor()));
                    return true;
                }
                @Override public void onScaleEnd(ScaleGestureDetector detector) {
                    float target = proposedZoom;
                    post(() -> applyZoom(target));
                }
            });
        }
        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            if (scaleDetector.isInProgress() && event.getPointerCount() > 1) return true;
            return super.dispatchTouchEvent(event);
        }
    }

    private void applyZoom(float target) {
        if (renderer == null || Math.abs(target-zoom)<.04f) return;
        int previousScroll = vertical == null ? 0 : vertical.getScrollY();
        float ratio = target/zoom;
        zoom = target;
        buildReader();
        if (vertical != null) vertical.post(() -> {
            vertical.scrollTo(0,Math.round(previousScroll*ratio));
            renderVisible();
        });
    }

    private void sharePdf() {
        if (currentUri == null) return;
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/pdf");
            send.putExtra(Intent.EXTRA_STREAM,currentUri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newUri(getContentResolver(),currentName,currentUri));
            startActivity(Intent.createChooser(send,"Compartilhar PDF"));
        } catch(Exception e) {
            Toast.makeText(this,"Não foi possível compartilhar.",Toast.LENGTH_SHORT).show();
        }
    }

    private void printPdf() {
        if (currentUri == null) return;
        try {
            PrintManager manager = (PrintManager)getSystemService(PRINT_SERVICE);
            manager.print(currentName,new PdfPrintAdapter(currentUri,currentName),
                    new PrintAttributes.Builder().build());
        } catch (Exception e) {
            Toast.makeText(this,"Impressão indisponível.",Toast.LENGTH_SHORT).show();
        }
    }

    private class PdfPrintAdapter extends PrintDocumentAdapter {
        private final Uri fileUri;
        private final String name;
        PdfPrintAdapter(Uri fileUri, String name) { this.fileUri=fileUri; this.name=name; }
        @Override public void onLayout(PrintAttributes oldAttrs, PrintAttributes newAttrs,
                CancellationSignal cancellation, LayoutResultCallback callback, Bundle extras) {
            if (cancellation.isCanceled()) { callback.onLayoutCancelled(); return; }
            PrintDocumentInfo info = new PrintDocumentInfo.Builder(name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build();
            callback.onLayoutFinished(info,true);
        }
        @Override public void onWrite(android.print.PageRange[] pages, ParcelFileDescriptor destination,
                CancellationSignal cancellation, WriteResultCallback callback) {
            new Thread(() -> {
                try (InputStream input = getContentResolver().openInputStream(fileUri);
                     OutputStream output = new FileOutputStream(destination.getFileDescriptor())) {
                    if (input == null) throw new IOException("Arquivo indisponível");
                    byte[] buffer = new byte[16384];
                    int n;
                    while ((n=input.read(buffer))!=-1) {
                        if (cancellation.isCanceled()) {
                            callback.onWriteCancelled(); return;
                        }
                        output.write(buffer,0,n);
                    }
                    output.flush();
                    callback.onWriteFinished(new android.print.PageRange[]{android.print.PageRange.ALL_PAGES});
                } catch (Exception e) {
                    callback.onWriteFailed("Erro ao preparar o PDF.");
                }
            }).start();
        }
    }

    private void closeDocument() {
        ++documentToken;
        ++zoomToken;
        pendingPages.clear();
        PdfRenderer old = renderer;
        ParcelFileDescriptor fd = descriptor;
        renderer = null;
        descriptor = null;
        sizes.clear();
        showHome();
        worker.execute(() -> {
            try { if (old != null) old.close(); } catch (Exception ignored) { }
            try { if (fd != null) fd.close(); } catch (Exception ignored) { }
        });
    }

    @Override public void onBackPressed() {
        if (currentUri != null) closeDocument();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        ++documentToken;
        ++zoomToken;
        PdfRenderer old = renderer;
        ParcelFileDescriptor fd = descriptor;
        renderer = null;
        descriptor = null;
        worker.execute(() -> {
            try { if(old != null) old.close(); } catch (Exception ignored) { }
            try { if(fd != null) fd.close(); } catch (Exception ignored) { }
        });
        worker.shutdown();
        super.onDestroy();
    }
}
