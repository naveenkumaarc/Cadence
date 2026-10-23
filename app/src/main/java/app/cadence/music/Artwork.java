package app.cadence.music;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
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
  private LinearGradient gradient;
  private final float density;
  private String source = "";
  private boolean circular = false;

  void circle() {
    circular = true;
    requestLayout();
    invalidate();
  }
  void reset() { source=""; setImageDrawable(null); invalidate(); }

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
                      if (view.source.equals(requested)) view.setImageBitmap(bitmap);
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
    gradient =
        new LinearGradient(
            0,
            height,
            Math.max(width, 1),
            0,
            new int[] {Color.rgb(17, 46, 44), Color.rgb(71, 108, 87), Color.rgb(166, 245, 208)},
            null,
            Shader.TileMode.CLAMP);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    float width = getWidth(),
        height = getHeight(),
        corner = circular ? width / 2f : Math.min(14 * density, width * 0.16f);
    canvas.save();
    canvas.clipPath(clip);
    if (getDrawable() == null) {
      paint.setShader(gradient);
      canvas.drawRect(0, 0, width, height, paint);
      paint.setShader(null);
      paint.setColor(Color.argb(100, 12, 29, 26));
      for (int i = 0; i < 6; i++) {
        float offset = width * (-0.3f + i * 0.16f);
        oval.set(offset, height * 0.12f, offset + width * 1.15f, height * 1.6f);
        canvas.drawOval(oval, paint);
      }
      paint.setColor(Color.argb(185, 231, 255, 238));
      paint.setStrokeWidth(width * 0.018f);
      paint.setStyle(Paint.Style.STROKE);
      canvas.drawCircle(width * 0.48f, height * 0.43f, width * 0.19f, paint);
      paint.setStyle(Paint.Style.FILL);
    } else super.onDraw(canvas);
    canvas.restore();
  }
}
