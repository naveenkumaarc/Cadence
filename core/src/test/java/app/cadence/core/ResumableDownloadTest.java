package app.cadence.core;

import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.*;
import okio.Buffer;
import org.junit.*;

public class ResumableDownloadTest {
  private MockWebServer server;
  private File partial;
  private final OkHttpClient client=new OkHttpClient();
  private final byte[] audio=new byte[128*1024];
  @Before public void setup() throws Exception {
    for(int i=0;i<audio.length;i++)audio[i]=(byte)(i%251);
    server=new MockWebServer();server.start();partial=Files.createTempFile("cadence-transfer-",".part").toFile();
  }
  @After public void cleanup() throws Exception {server.shutdown();Files.deleteIfExists(partial.toPath());}
  private MockResponse body(byte[] bytes){return new MockResponse().setBody(new Buffer().write(bytes)).setHeader("ETag","\"audio-v1\"");}
  private boolean download(String validator,java.util.function.BooleanSupplier stop,ResumableDownload.Progress progress) throws Exception {
    return ResumableDownload.transfer(client,server.url("/audio").toString(),partial,validator,stop,progress,call->{});
  }
  @Test public void pausesAndContinuesFromSavedBytes() throws Exception {
    server.enqueue(body(audio));AtomicBoolean stop=new AtomicBoolean();String[] validator={""};
    assertFalse(download("",stop::get,(done,total,tag)->{validator[0]=tag;if(done>=8192)stop.set(true);}));
    long offset=partial.length();assertTrue(offset>0&&offset<audio.length);server.takeRequest();
    server.enqueue(body(java.util.Arrays.copyOfRange(audio,(int)offset,audio.length)).setResponseCode(206)
        .setHeader("Content-Range","bytes "+offset+"-"+(audio.length-1)+"/"+audio.length));
    assertTrue(download(validator[0],()->false,(done,total,tag)->assertEquals(audio.length,total)));
    RecordedRequest resumed=server.takeRequest();assertEquals("bytes="+offset+"-",resumed.getHeader("Range"));
    assertEquals("\"audio-v1\"",resumed.getHeader("If-Range"));assertArrayEquals(audio,Files.readAllBytes(partial.toPath()));
  }
  @Test public void serverIgnoringRangeRestartsWithoutAppending() throws Exception {
    Files.write(partial.toPath(),new byte[]{1,2,3});server.enqueue(body(audio));
    assertTrue(download("\"audio-v1\"",()->false,(a,b,c)->{}));assertArrayEquals(audio,Files.readAllBytes(partial.toPath()));
  }
  @Test public void noValidatorDownloadsFreshRepresentation() throws Exception {
    Files.write(partial.toPath(),new byte[]{1,2,3});server.enqueue(body(audio));
    assertTrue(download("",()->false,(a,b,c)->{}));assertNull(server.takeRequest().getHeader("Range"));assertArrayEquals(audio,Files.readAllBytes(partial.toPath()));
  }
  @Test public void rejectsIncorrectRangeWithoutChangingPartial() throws Exception {
    byte[] prefix={1,2,3};Files.write(partial.toPath(),prefix);
    server.enqueue(body(new byte[]{4,5}).setResponseCode(206).setHeader("Content-Range","bytes 0-1/5"));
    try{download("\"audio-v1\"",()->false,(a,b,c)->{});fail("Invalid range accepted");}catch(IOException expected){}
    assertArrayEquals(prefix,Files.readAllBytes(partial.toPath()));
  }
  @Test public void alreadyCompleteRangeIsAccepted() throws Exception {
    Files.write(partial.toPath(),audio);server.enqueue(new MockResponse().setResponseCode(416).setHeader("Content-Range","bytes */"+audio.length));
    assertTrue(download("\"audio-v1\"",()->false,(a,b,c)->{}));assertArrayEquals(audio,Files.readAllBytes(partial.toPath()));
  }
  @Test public void changedValidatorDiscardsTheOldRepresentation() throws Exception {
    Files.write(partial.toPath(),new byte[]{1,2,3});
    server.enqueue(body(new byte[]{4,5}).setResponseCode(206).setHeader("Content-Range","bytes 3-4/5").setHeader("ETag","\"audio-v2\""));
    try{download("\"audio-v1\"",()->false,(a,b,c)->{});fail("Mixed representations accepted");}catch(IOException expected){}
    assertEquals(0,partial.length());
  }
  @Test public void cancelledBeforeStartMakesNoRequest() throws Exception {
    assertFalse(download("",()->true,(a,b,c)->fail("Unexpected progress")));assertEquals(0,server.getRequestCount());
  }
  @Test public void exposesExpiredSourceForResolutionRetry() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(403));
    try{download("",()->false,(a,b,c)->{});fail("Expected failure");}catch(ResumableDownload.HttpFailure error){assertEquals(403,error.code);}
  }
}
