package app.cadence.music;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.LruCache;
import android.widget.ImageView;
import app.cadence.core.HttpDownloader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.Request;

final class Artwork extends ImageView {
  private static final ExecutorService IMAGES = Executors.newFixedThreadPool(3);
  private static final LruCache<String, Bitmap> CACHE =
      new LruCache<>(12 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
          return bitmap.getByteCount() / 1024;
        }
      };
  private static okhttp3.OkHttpClient imageClient = HttpDownloader.CLIENT;
  private static final java.util.concurrent.ConcurrentHashMap<
          String, java.util.concurrent.CopyOnWriteArrayList<java.lang.ref.WeakReference<Artwork>>>
      PENDING = new java.util.concurrent.ConcurrentHashMap<>();

  static void configure(Context c) {
    if (imageClient == HttpDownloader.CLIENT)
      imageClient =
          HttpDownloader.CLIENT
              .newBuilder()
              .cache(
                  new okhttp3.Cache(
                      new java.io.File(c.getCacheDir(), "artwork"), 40L * 1024 * 1024))
              .build();
  }

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path clip = new Path();
  private final RectF oval = new RectF();
  private final float density;
  private String source = "";
  private boolean circular = false;
  private java.util.function.Consumer<Integer> paletteListener;

  void circle() {
    circular = true;
    requestLayout();
    invalidate();
  }
  void reset() { source=""; setImageDrawable(null); invalidate(); }
  void onPalette(java.util.function.Consumer<Integer> listener) { paletteListener=listener; }

  Artwork(Context context) {
    super(context);
    density = getResources().getDisplayMetrics().density;
    setScaleType(ScaleType.CENTER_CROP);
    setContentDescription("Track artwork");
  }

  void load(String url) {
    if (url == null) url = "";
    if (url.equals(source)) return;
    source = url;
    setImageDrawable(null);
    if (!url.startsWith("https://")) {
      invalidate();
      return;
    }
    Bitmap cached = CACHE.get(url);
    if (cached != null) {
      setImageBitmap(cached);
      publishPalette(cached);
      return;
    }
    String requested = url;
    var waiter = new java.lang.ref.WeakReference<Artwork>(this);
    var waiting =
        new java.util.concurrent.CopyOnWriteArrayList<java.lang.ref.WeakReference<Artwork>>();
    waiting.add(waiter);
    var existing = PENDING.putIfAbsent(requested, waiting);
    if (existing != null) {
      existing.add(waiter);
      return;
    }
    IMAGES.execute(
        () -> {
          Bitmap found = null;
          try (var response =
              imageClient.newCall(new Request.Builder().url(requested).build()).execute()) {
            if (response.isSuccessful() && response.body() != null) {
              BitmapFactory.Options options = new BitmapFactory.Options();
              options.inSampleSize = 2;
              found = BitmapFactory.decodeStream(response.body().byteStream(), null, options);
            }
          } catch (Exception ignored) {
            try (var cachedResponse =
                imageClient
                    .newCall(
                        new Request.Builder()
                            .url(requested)
                            .cacheControl(
                                new okhttp3.CacheControl.Builder()
                                    .onlyIfCached()
                                    .maxStale(
                                        Integer.MAX_VALUE, java.util.concurrent.TimeUnit.SECONDS)
                                    .build())
                            .build())
                    .execute()) {
              if (cachedResponse.isSuccessful() && cachedResponse.body() != null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2;
                found =
                    BitmapFactory.decodeStream(cachedResponse.body().byteStream(), null, options);
              }
            } catch (Exception unavailable) {
            }
          }
          if (found != null) CACHE.put(requested, found);
          Bitmap bitmap = found;
          var targets = PENDING.remove(requested);
          if (targets != null && bitmap != null)
            for (var ref : targets) {
              Artwork view = ref.get();
              if (view != null)
                view.post(
                    () -> {
                      if (view.source.equals(requested)) {
                        view.setImageBitmap(bitmap);
                        view.publishPalette(bitmap);
                      }
                    });
            }
        });
  }

  @Override
  protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    float corner = circular ? width / 2f : Math.min(14 * density, width * 0.16f);
    clip.reset();
    oval.set(0, 0, width, height);
    clip.addRoundRect(oval, corner, corner, Path.Direction.CW);
  }

  private void publishPalette(Bitmap bitmap) {
    if (paletteListener == null || bitmap == null || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) return;
    int step = Math.max(1, Math.min(bitmap.getWidth(), bitmap.getHeight()) / 24);
    long red=0, green=0, blue=0, count=0;
    for(int y=step/2;y<bitmap.getHeight();y+=step) for(int x=step/2;x<bitmap.getWidth();x+=step) {
      int colour=bitmap.getPixel(x,y); if(Color.alpha(colour)<128) continue;
      red+=Color.red(colour);green+=Color.green(colour);blue+=Color.blue(colour);count++;
    }
    if(count>0) paletteListener.accept(Color.rgb((int)(red/count),(int)(green/count),(int)(blue/count)));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    float width = getWidth(),
        height = getHeight(),
        corner = circular ? width / 2f : Math.min(14 * density, width * 0.16f);
    canvas.save();
    canvas.clipPath(clip);
    if (getDrawable() == null) {
      UiStyle.Palette palette=UiStyle.palette(getContext());
      paint.setColor(palette.raised()); canvas.drawRect(0,0,width,height,paint);
      paint.setColor(palette.accent()); paint.setStrokeWidth(Math.max(2,width*.035f));
      paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND);
      canvas.drawLine(width*.55f,height*.28f,width*.55f,height*.65f,paint);
      canvas.drawLine(width*.55f,height*.28f,width*.75f,height*.22f,paint);
      paint.setStyle(Paint.Style.FILL); canvas.drawCircle(width*.43f,height*.69f,width*.12f,paint);
    } else super.onDraw(canvas);
    canvas.restore();
  }
}
