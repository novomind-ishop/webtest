package com.novomind.webtest;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Class that requests all supplied urls at once and then waits that all urls are answered.
 * Prints out the required time for each request.
 * As all urls are requested at once, there is no guarantee about the order of the requested urls.
 */
public class RequestAndMeasure {

  static HostnameVerifier hostnameVerifier;
  static TrustManager[] trustAllCerts;
  static SSLContext sslContext;
  static SSLSocketFactory sslSocketFactory;

  static Collection<String> urls = new LinkedList<>();
  static Set<Runnable> tasks = new HashSet<>();

  /** Request timeout in milliseconds */
  static final int REQUEST_TIMEOUT = 90000;

  ///////////////////////
  /// MAIN
  ///////////////////////
  public static void main(String[] args) throws Exception {
    System.out.println("Hello to " + RequestAndMeasure.class.getName());
    System.out.println("This programm will request all urls (can be load from a file) at once and measures the time");
    System.out.println("Usage: " + RequestAndMeasure.class.getName() + " <fileWithUrls>");
    System.out.println("");

    System.out.println("Init...");

    ///////////
    /// Use urls from file
    urls = loadUrlsFromFile(args[0]);

    /// Or overwrite manually
    // urls.clear();
    // for (int i = 1; i <= 50; ++i) {
    // urls.add("http://10.21.97.33/iview/GetImage?imageName=L1003867_" + i + ".jpg&template=image_512x512");
    // }

    /** Thread start synchronization */
    final CountDownLatch countDownLatch = new CountDownLatch(urls.size());

    //////////////////////
    /// Init SSL
    hostnameVerifier = new HostnameVerifier() {
      public boolean verify(String hostname, SSLSession session) {
        return true;
      }
    };
    trustAllCerts = new TrustManager[] {
        new X509TrustManager() {
          public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
          }

          public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
          }

          public X509Certificate[] getAcceptedIssuers() {
            return null;
          }
        }
    };
    sslContext = SSLContext.getInstance("SSL");
    sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
    sslSocketFactory = sslContext.getSocketFactory();
    HttpURLConnection.setFollowRedirects(false);
    System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    System.setProperty("http.maxConnections", "" + urls.size());

    //////////////////////
    /// Build all requests
    String postData = "";
    boolean post = !postData.isEmpty();

    for (final String url : urls) {

      /////////////////////////////
      // Pre-Init url
      URL u = new URL(url);
      final HttpURLConnection hc = (HttpURLConnection) u.openConnection();
      if (url.startsWith("https")) {
        ((HttpsURLConnection) hc).setHostnameVerifier(hostnameVerifier);
        ((HttpsURLConnection) hc).setSSLSocketFactory(sslSocketFactory);
      }

      hc.setReadTimeout(REQUEST_TIMEOUT);

      hc.addRequestProperty("User-Agent", "novomind webtest");
      hc.addRequestProperty("Accept-Encoding", "gzip");

      if (post) {
        hc.setRequestMethod("POST");
        hc.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        hc.setRequestProperty("Content-Length", "" + Integer.toString(postData.getBytes().length));
        hc.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(hc.getOutputStream());
        wr.writeBytes(postData);
        wr.flush();
        wr.close();
      } else {
        hc.setRequestMethod("GET");
      }

      final int taskId = tasks.size();

      ////////////////////////////////
      /// Build task
      Runnable task = new Runnable() {
        @Override
        public void run() {
          {
            try {
              countDownLatch.countDown();
              countDownLatch.await();

              long start = System.currentTimeMillis();
              int responseCode = hc.getResponseCode();
              String data = slurp(hc.getInputStream(), 1_000_000);
              long end = System.currentTimeMillis();

              System.out.println(
                  "" + taskId + ": (Status: " + responseCode + ", " + data.length() + "B in " + (end - start)
                      + "ms) for " + url);
            } catch (IOException | InterruptedException e) {
              e.printStackTrace();
            }
          }
        }
      };
      tasks.add(task);
    }

    /////////////////////
    // Wrap tasks in threads
    List<Thread> threads = new ArrayList<>();
    for (Runnable task : tasks) {
      Thread thread = new Thread(task);
      threads.add(thread);
    }

    ///////////////////////
    // Run threads
    long start = System.currentTimeMillis();
    for (int i = 0; i < threads.size(); ++i) {
      threads.get(i).start();
    }
    System.out.println("Urls are getting requested...");
    for (int i = 0; i < threads.size(); ++i) {
      threads.get(i).join();
    }
    long end = System.currentTimeMillis();

    ////////////////////////
    // Final output
    System.out.println("Total time: " + (end - start) + "ms");
  }

  /**
   * Convert Inputstream to string
   */
  static String slurp(final InputStream is, final int bufferSize) {
    final char[] buffer = new char[bufferSize];
    final StringBuilder out = new StringBuilder();
    try (Reader in = new InputStreamReader(is, "UTF-8")) {
      for (;;) {
        int rsz = in.read(buffer, 0, buffer.length);
        if (rsz < 0)
          break;
        out.append(buffer, 0, rsz);
      }
    } catch (IOException ex) {
      System.out.println("IOException: " + ex.getLocalizedMessage());
    }
    return out.toString();
  }

  private static Collection<String> loadUrlsFromFile(String filepath) throws IOException {
    FileInputStream fis = new FileInputStream(filepath);
    BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

    Collection<String> urls = new ArrayList<String>();

    try {
      String line = reader.readLine();
      while (line != null) {
        String[] lineParts = line.split(" ");

        // The last part is always the url
        String url = lineParts[lineParts.length - 1];
        urls.add(url);

        line = reader.readLine();
      }

    } catch (IOException ex) {
      System.out.println("Unable to open urlfile: " + filepath + " ; " + ex.getLocalizedMessage());
    } finally {
      reader.close();
      fis.close();
    }

    return urls;
  }
}
