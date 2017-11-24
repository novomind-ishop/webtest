package com.novomind.webtest;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import sun.misc.BASE64Encoder;

public class Main implements Runnable {
  static int users = 300;
  static int runtime_minutes = 1;
  static double avgWait = 0;

  static String username;
  static String password;

  static ArrayList<String> urls = new ArrayList<String>();
  static Set<String> singleUseUrls = new HashSet<String>();
  static Map<Integer, Map<String, Boolean>> urlForUserAlreadyUsed = new HashMap<Integer, Map<String, Boolean>>();

  static int active_users = 0;

  static boolean verbose = false;

  static long start;
  static long end_time;
  static int active_requests = 0;
  static long total_requests = 0;
  static long delta_requests = 0;

  static String[] SessionID;
  static String[] CsrfTokens;
  static Hashtable urltimes = new Hashtable();
  static Hashtable urlcounts = new Hashtable();
  static Hashtable urlsize = new Hashtable();

  static int[] distribution = new int[100000];

  static long last_msg = System.nanoTime();
  static long first_msg = last_msg;
  static long last_len;
  static long total_len = 0;
  static int error_count = 0;
  static final int MAX_ERRORS = 100;

  static Object o = new Object();

  static boolean rampstart = true;
  static double ramp_delay;

  static HostnameVerifier hostnameVerifier;
  static TrustManager[] trustAllCerts;
  static SSLContext sslContext;
  static SSLSocketFactory sslSocketFactory;

  /** Request timeout in milliseconds */
  static final int REQUEST_TIMEOUT = 30000;

  // For each Runnable
  int userId;
  long start_time;

  ///////////////////////
  /// Constructor
  ///////////////////////
  public Main(int user_id) {
    this.userId = user_id;
  }

  ///////////////////////
  /// MAIN
  ///////////////////////
  public static void main(String[] args) throws Exception {
    hostnameVerifier = new HostnameVerifier() {

      public boolean verify(String hostname, SSLSession session) {
        return true;
      }
    };
    trustAllCerts = new TrustManager[] { new X509TrustManager() {
      public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
      }

      public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
      }

      public X509Certificate[] getAcceptedIssuers() {
        return null;
      }
    } };
    sslContext = SSLContext.getInstance("SSL");
    sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
    sslSocketFactory = sslContext.getSocketFactory();
    HttpURLConnection.setFollowRedirects(false);
    System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    // System.setProperty("http.keepAlive", "false");
    boolean isUsername = false;
    boolean isPassword = false;
    boolean isSingleUrl = false;
    boolean isUrlFile = false;

    if (args.length < 4) {
      printHelp();
      System.exit(1);
    }

    for (int i = 0; i < args.length; i++) {
      if (!args[i].startsWith("-") && !isPassword && !isUsername && !isUrlFile) {
        if (0 == i)
          users = Integer.parseInt(args[i]);
        if (1 == i)
          runtime_minutes = Integer.parseInt(args[i]);
        if (2 == i)
          avgWait = Double.parseDouble(args[i]);
        if (2 < i) {
          urls.add(args[i]);
          if (isSingleUrl) {
            singleUseUrls.add(args[i]);
            isSingleUrl = false;
          }
        }
      } else {
        if (isUsername) {
          username = args[i];
          isUsername = false;
        }

        if (isPassword) {
          password = args[i];
          isPassword = false;
        }

        if (isUrlFile) {
          urls.addAll(loadUrlsFromFile(args[i]));
          isUrlFile = false;
        }

        if (args[i].equals("-u")) {
          isUsername = true;
        }

        if (args[i].equals("-p")) {
          isPassword = true;
        }

        if (args[i].equals("-f")) {
          isUrlFile = true;
        }

        if (args[i].equals("-s")) {
          isSingleUrl = true;
        }

        if (args[i].equals("-v")) {
          verbose = true;
        }
      }

    }

    System.setProperty("http.maxConnections", "" + users);

    ramp_delay = runtime_minutes * 60000.0 / users / 2;
    rampstart = users >= 100;
    // qrampstart = true;
    System.out.println("USERS " + users);
    System.out.println("RUNTIME_MINUTES " + runtime_minutes);
    System.out.println("AVG_WAIT " + avgWait);
    System.out.println("estimated clicks/s " + users / avgWait);
    if (username != null && password != null) {
      System.out.println("using " + username + " to log in.");
    }

    Main m[] = new Main[users + 1];
    Thread t[] = new Thread[users + 1];
    SessionID = new String[users + 1];
    CsrfTokens = new String[users + 1];
    for (int i = 0; i < m.length; i++)
      m[i] = new Main(i - 1);
    start = System.nanoTime();
    for (int i = 0; i < m.length; i++)
      t[i] = new Thread(m[i]);
    // m[0].initCache ();
    start = System.nanoTime();
    long duration = 60 * runtime_minutes;
    duration *= 1000000000;
    end_time = System.nanoTime() + duration;
    last_msg = 0;
    for (int i = 0; i < m.length; i++)
      t[i].start();
    m[0].message(users + " threads started");
    for (int i = 0; i < m.length; i++)
      t[i].join();
    m[0].message(users + " threads terminated");
    Enumeration e = urltimes.keys();
    while (e.hasMoreElements()) {
      String k = (String) e.nextElement();
      Long tt = (Long) urltimes.get(k);
      Long tc = (Long) urlcounts.get(k);
      Long sz = (Long) urlsize.get(k);
      System.out.println("url " + k + " requested " + tc.longValue() + " times " + " total time " + x(tt.longValue()) + " avg "
          + x((tt.longValue() / tc.longValue())) + " bytes " + x(sz.longValue()) + " avg " + x(sz.longValue() / tc.longValue()));
    }

    System.out.println("avg requests per second " + (total_requests * 1000000000 / (System.nanoTime() - start)));

    long req = 0;
    int percentile = 90;
    long avg = 0;
    for (int i = 0; i < distribution.length; i++) {
      if (distribution[i] > 0) {
        avg += i * distribution[i];
      }
    }
    avg /= total_requests;

    int lastDistribution = 0;

    for (int i = 0; i < distribution.length; i++) {
      if (distribution[i] > 0) {
        long newreq = req + distribution[i];
        if (i > avg) {
          if (!verbose) {
            System.out.println("Average: " + lastDistribution);
          } else {
            System.out.println("^^^ average");
          }
          avg = distribution.length;
        }
        for (; ; ) {
          if (newreq * 100 > percentile * total_requests) {
            if (!verbose) {
              System.out.println(percentile + "% percentile: " + lastDistribution);
            } else {
              System.out.println("^^^ " + percentile + "% precentile");
            }
            switch (percentile) {
            case 90:
              percentile = 95;
              break;
            case 95:
              percentile = 99;
              break;
            case 99:
              percentile = 100;
              break;
            default:
              throw new Exception("percentile failed");
            }
          }
          break;
        }
        lastDistribution = i;
        if (verbose) {
          System.out.println(i + " " + distribution[i]);
        }
        req = newreq;
      }
    }
    System.out.println("The timeout was at: " + REQUEST_TIMEOUT);

    System.exit(0);
  }

  public static void printHelp() {
    System.out.println("This is the novomind webtest tool");
    System.out
        .println("It benchmarks the performance of a webservice, by requesting URLs and measuring the time of each request.");
    System.out.println("When all request are finished/have answered, a small statistic is presented.");
    System.out.println("");
    System.out.println("Usage:");
    System.out.println(" webtest users runtime delay [OPTION...] URL...");
    System.out.println("");
    System.out.println(" users     The number of users/clients that simultaneously request the URLs.");
    System.out.println(" runtime   The duration of this test in minutes.");
    System.out.println(
        " delay     Delay between two requests of the same user. If 0, then URLs are requested as fast as possible (dangerous!)");
    System.out.println(" URL...    One or more URLs that should be requested. See URLs section.");
    System.out.println("");
    System.out.println("Options:");
    System.out.println(" -u user   The username, that will be used for basic authentication.");
    System.out.println(" -p pass   The password, that will be used for basic authentication.");
    System.out.println(
        " -f file   The file from which URLs get loaded. One url per line. -s option is allowed in each line beginning.");
    System.out.println(" -v        Verbose output.");
    System.out.println("");
    System.out.println("URLs: [-s] URL[POST[POST-BODY]]");
    System.out.println(" -s        This URL will be requested only once, but for each user (i.e. add to basket, login etc)");
    System.out.println(" URL       URLs are expected in the general http://site/file.html?page=123 way.");
    System.out.println("           Also https is supported, even though the certificate is not validated!");
    System.out.println(" POST      Add POST at the end of the URL and all data after POST will be posted.");
    System.out.println("");
    System.out.println("Output:");
    System.out.println(
        " The output are pairs of two numbers, where the first number is an answer time in milliseconds and the second number, who often this time was achieved.");
    System.out.println(" In between are certain separating lines, like ---percentile---, for simple statistic reasons.");
    System.out.println("");
    System.out.println("");
    System.out.println("Examples:");
    System.out.println("webtest 5 1 1 http://www.novomind.com/");
    System.out.println(
        " Five users request the site http://www.novomind.com/ for 1 minute with a 1 second delay between each request.");
    System.out.println("");
    System.out.println("webtest 25 3 0 http://www.novomind.com/hiddenarea/ https://www.google.com/ -u fritz -p geheim");
    System.out.println(
        " 25 users request the site http://www.novomind.com/hiddenarea/ and https://www.google.com/ for 3 minutes with a 0 second delay (creates a lot of requests!). For all (both) sites the user 'fritz' and password 'geheim' is used as authentication.");
    System.out.println("");
    System.out.println("webtest 1 1 10 http://www.shop.com/ -s https://www.shop.com/add/product/POSTitemId=dummyId");
    System.out.println(
        " 1 user requests for one minute with a request delay of 10 seconds the site www.shop.com over and over. The second url is requested only once (-s) and has additional POST information.");
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

        if (2 <= lineParts.length) {
          // There is an option specified
          if (lineParts[0].equals("-s")) {
            singleUseUrls.add(url);
          }
        }

        line = reader.readLine();
      }

    } catch (IOException ex) {
      System.out.println("Unable to open urlfile: " + filepath);
    } finally {
      reader.close();
      fis.close();
    }

    return urls;
  }

  private static String x(long l) {
    String s = "" + l;
    String ret = "";
    int j = 0;
    for (int i = s.length() - 1; i >= 0; i--) {
      ret = s.charAt(i) + ret;
      if ((++j % 3) == 0 && i > 0)
        ret = "." + ret;
    }
    return ret;
  }

  ///////////////////////
  /// RUN for Runnable
  ///////////////////////
  public void run() {
    int step = 0;
    long now;
    while ((now = System.nanoTime()) <= end_time) {
      if (userId == -1) {
        long t2 = now - start;
        if (step > 0)
          synchronized (o) {
            double delta = (t2 - last_msg);
            delta = delta * 0.000000128;
            double delta2 = t2;
            delta2 = delta2 * 0.000000128;
            double delta3 = (t2 - last_msg);
            delta3 = delta3 * 0.000000001;

            message("Requests: " + total_requests + " requests/s " + Math.round((total_requests - delta_requests) / delta3)
                + " KBit: " + Math.round((total_len - last_len) / delta) + " KBit avg:" + Math.round((total_len) / delta2)
                + " req/s avg " + total_requests * 1e9 / t2);

            delta_requests = total_requests;
            last_len = total_len;
            last_msg = t2;
          }
        t2 = ++step;
        t2 *= 1000000000;
        t2 += start;
        t2 -= System.nanoTime();
        realsleep(t2 / 1000000);
      } else {
        do_step(userId, step++);
        if (error_count >= MAX_ERRORS)
          break;
      }
    }
  }

  private void do_step(int user_id, int step) {
    do_step(user_id, step, true);
  }

  private void do_step(int user_id, int step, boolean wait) {
    if (rampstart && step == 0) {
      realsleep(Math.round(user_id * ramp_delay));
      synchronized (o) {
        active_users++;
      }
    }
    if (wait)
      sleep(user_id, step);
    if (System.nanoTime() > end_time)
      return;
    String url = "";
    url = urls.get(step % urls.size());

    if (!wait)
      message(url);
    boolean newSession = (step % urls.size()) == 0;
    newSession = false;

    String sid = SessionID[user_id];
    if (sid == null)
      sid = "";
    if (sid != null) {
      sid = sid.substring(sid.indexOf("=") + 1);
    }
    url = url.replace("##>SESSIONID<##", sid);
    String nexturl = "";
    int idx = (step + 1) % urls.size();
    if (idx != 0) {
      nexturl = urls.get(idx);
    }
    requestUrl(url, user_id, newSession, nexturl);
  }

  private long requestUrl(String url, int user_id, boolean newSession, String nexturl) {
    Map<String, Boolean> urlAlreadyUsedMap = urlForUserAlreadyUsed.get(user_id);

    if (singleUseUrls.contains(url)) {
      if (urlAlreadyUsedMap == null) {
        urlAlreadyUsedMap = new HashMap<String, Boolean>();
        urlForUserAlreadyUsed.put(user_id, urlAlreadyUsedMap);
      } else {
        Boolean used = urlAlreadyUsedMap.get(url);
        if (used != null && used) {
          return 0;
        }
      }
      urlAlreadyUsedMap.put(url, true);

    }

    url = url.replaceAll("USER", String.format("%04d", new Object[] { Integer.valueOf(user_id + 1) }));
    byte buffer[] = new byte[65536];
    long requestStart = System.nanoTime();
    long len = 0;
    long time = 0;
    long t2;
    int response = -1;

    synchronized (o) {
      active_requests++;
    }
    try {
      int postIdx = url.indexOf("POST");
      boolean post = postIdx > 0;
      String postData = "";
      if (post) {
        postData = url.substring(postIdx + 4);
        url = url.substring(0, postIdx);
      }

      URL u = new URL(url);
      HttpURLConnection hc = (HttpURLConnection) u.openConnection();
      if (url.startsWith("https")) {
        ((HttpsURLConnection) hc).setHostnameVerifier(hostnameVerifier);
        ((HttpsURLConnection) hc).setSSLSocketFactory(sslSocketFactory);
      }

      hc.setReadTimeout(REQUEST_TIMEOUT);

      if (SessionID[user_id] != null && !newSession) {
        // message ("using session id " + SessionID [user_id]);

        hc.addRequestProperty("Cookie", SessionID[user_id]);
      } else {
        message("create new session");
      }
      hc.addRequestProperty("User-Agent", "novomind webtest");
      hc.addRequestProperty("Accept-Encoding", "gzip");

      if (username != null && password != null) {
        hc.addRequestProperty("Authorization", "Basic " + encode(username + ":" + password));
      }
      // else
      if (post) {
        hc.setRequestMethod("POST");
        hc.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        hc.setRequestProperty("Content-Length", "" + Integer.toString(postData.getBytes().length));
        hc.setRequestProperty("X-Csrf-Token", CsrfTokens[user_id]);
        hc.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(hc.getOutputStream());
        wr.writeBytes(postData);
        wr.flush();
        wr.close();
      } else {
        hc.setRequestMethod("GET");
      }
      {
        response = hc.getResponseCode();
        if (response != 200)
          if (response != 302) {
            String extra = "";
            if (SessionID[user_id] != null)
              extra = extra + "\nsession " + SessionID[user_id];
            if (!"".equals(postData))
              extra = extra + "\npost data " + postData;
            message(extra + "\n" + url + " -> " + response);
          }
        for (int i = 1; ; i++) {
          String val = hc.getHeaderField(i);
          if (val == null)
            break;
          // message ("header field #" + i + " >>>" + val + "<<<");
          if (val.startsWith("JSESSIONID=")) {
            String session = val.substring(0, val.indexOf(";"));
            if (!session.equals(SessionID[user_id])) {
              SessionID[user_id] = val.substring(0, val.indexOf(";"));
              message("saved session id " + SessionID[user_id] + ", cookie was " + val);
            }
            break;
          }
        }
        if (response == 302 && nexturl.length() != 0) {
          String s = SessionID[user_id];
          if (s != null) {
            s = s.substring(s.indexOf("=") + 1);
          }
          nexturl = nexturl.replace("##>SESSIONID<##", s);
          String location = hc.getHeaderField("Location");
          if (!nexturl.equals(location))
            message("OUT OF SYNC (302 redirect mismatch): expected " + nexturl + ", got " + location);
        }
        if (CsrfTokens[user_id] == null) {
          try {
            BufferedReader buff = new BufferedReader(new InputStreamReader(hc.getInputStream()));
            String line;
            String csrf;
            while ((line = buff.readLine()) != null) {
              int index = line.indexOf("type=\"hidden\" name=\"_csrf\" value=\"");
              if (index != -1) {
                String t = line.split("type=\"hidden\" name=\"_csrf\" value=\"")[1];
                csrf = t.split("\"")[0];
                CsrfTokens[user_id] = csrf;
                message("Found CSRF-Token for User[" + user_id + "]: " + csrf);
                break;
              }
            }
          } catch (IOException e) {
            message("Could not find CSRF-Token for User[" + user_id
                + "] - POST Requests will not work if CSRF-Protection is enabled");
          }
        }
      }
      InputStream is = hc.getInputStream();
      // GZIPInputStream zis = new GZIPInputStream (is);
      boolean first = true;
      while (true) {
        int j = is.read(buffer);

        if (j < 0)
          break;

        len += j;
        String s = new String(buffer, 0, j);

        synchronized (o) {
          total_len += j;
        }
      }
      is.close();
      // hc.disconnect ();
    } catch (Exception e) {
      message(e.toString() + " for " + url);
      synchronized (o) {
        error_count++;
      }
    }
    if (time == 0)
      time = System.nanoTime() - requestStart;
    synchronized (o) {
      // if (time >= 1000000000) message ("url " + url + " time " + time);
      int idx = (int) Math.round(time / 1000000.0);
      if (idx >= distribution.length - 1)
        idx = distribution.length - 1;
      distribution[idx]++;
      active_requests--;

      Long l = (Long) urltimes.get(url);
      if (l == null)
        l = new Long(0);
      urltimes.put(url, new Long(l.longValue() + time));
      l = (Long) urlcounts.get(url);
      if (l == null)
        l = new Long(0);
      urlcounts.put(url, new Long(l.longValue() + 1));
      l = (Long) urlsize.get(url);
      if (l == null)
        l = new Long(0);
      urlsize.put(url, new Long(l.longValue() + len));
      total_requests++;

    }
    buffer = null;
    return time;
  }

  private static String encode(String source) {
    BASE64Encoder enc = new sun.misc.BASE64Encoder();
    return (enc.encode(source.getBytes()));
  }

  private void message(String s) {
    if (verbose) {
      System.out.println("users " + active_users + " active " + active_requests + " time " + nanoString(System.nanoTime() - start)
          + (userId != -1 ? (" user " + userId + ": ") : " ") + ": " + s);
    }
  }

  private static String nanoString(long l) {
    String ret = ("" + l).trim();
    while (ret.length() < 10)
      ret = "0" + ret;
    return ret.substring(0, ret.length() - 9) + "," + ret.substring(ret.length() - 9);
  }

  private void sleep(int user_id, int step) {
    if (avgWait > 0) {
      long now = System.nanoTime();
      if (step == 0)
        start_time = now;
      long max_end = Math.round(start_time + (step + 2) * avgWait * 1000000000);
      long waittime;
      if (max_end < now) {
        // message ("out of sync, server overloaded");
        return;
      } else {
        waittime = Math.round((max_end - now) * 0.000001 * Math.random());
      }
      long resttime = (end_time - now) / 1000000;
      if (resttime < 0)
        resttime = 0;
      if (resttime < waittime)
        waittime = resttime + 1;
      realsleep(waittime);
    }
  }

  private void realsleep(long waittime) {
    if (waittime > 0)
      try {
        Thread.sleep(waittime);
      } catch (Exception e) {
      }
  }
}
