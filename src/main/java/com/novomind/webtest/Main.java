package com.novomind.webtest;

import java.awt.AWTEvent;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.swing.JFrame;

/**
 * novomind Web Test. A load test tool designed to test java based web applications.
 */
public class Main implements Runnable {

  private static GraphGui gui;
  protected static List<Long> througput = new ArrayList<>();
  protected static List<Double> avgThrougput = new ArrayList<>();
  protected static Long maxThrougput = 0L;
  static int users = 300;
  static int runtimeMinutes = 1;
  static double avgWait = 0;

  static String username;
  static String password;

  static String csrfUrl;
  static ArrayList<String> urls = new ArrayList<>();
  static Set<String> singleUseUrls = new HashSet<>();
  static Map<Integer, Map<String, Boolean>> urlForUserAlreadyUsed = new HashMap<>();

  static int activeUsers = 0;

  static boolean verbose = false;

  static long start;
  static long endTime;
  static int activeRequests = 0;
  static long totalRequests = 0;
  static long deltaRequests = 0;

  static String[] sessionIDs;
  static String[] csrfTokens;
  static Hashtable urltimes = new Hashtable();
  static Hashtable urlcounts = new Hashtable();
  static Hashtable urlsize = new Hashtable();

  static int[] distribution = new int[100000];

  static long lastMsg = System.nanoTime();
  static long lastLen;
  static long totalLen = 0;
  static int errorCount = 0;
  static final int MAX_ERRORS = 100;

  static Object lock = new Object();

  static boolean rampstart = true;
  static double rampDelay;

  static HostnameVerifier hostnameVerifier;
  static TrustManager[] trustAllCerts;
  static SSLContext sslContext;
  static SSLSocketFactory sslSocketFactory;

  /** Request timeout in milliseconds */
  static final int REQUEST_TIMEOUT = 30000;

  /** Java session cookie only **/
  static final String SESSIONID_COOKIE = "JSESSIONID=";
  static boolean showGui = false;

  // For each Runnable
  int userId;
  long startTime;

  public Main(int userId) {
    this.userId = userId;
  }

  public static void main(String[] args) throws Exception {
    hostnameVerifier = (hostname, session) -> true;
    trustAllCerts = new TrustManager[] { new X509TrustManager() {
      public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
      }

      public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
      }

      public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
      }
    } };
    sslContext = SSLContext.getInstance("SSL");
    sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
    sslSocketFactory = sslContext.getSocketFactory();
    HttpURLConnection.setFollowRedirects(false);
    System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    boolean isUsername = false;
    boolean isPassword = false;
    boolean isSingleUrl = false;
    boolean isUrlFile = false;
    boolean isCsrf = false;

    if (args.length < 4) {
      printHelp();
      System.exit(1);
    }

    for (int i = 0; i < args.length; i++) {
      if (!args[i].startsWith("-") && !isPassword && !isUsername && !isUrlFile) {
        if (0 == i)
          users = Integer.parseInt(args[i]);
        if (1 == i)
          runtimeMinutes = Integer.parseInt(args[i]);
        if (2 == i)
          avgWait = Double.parseDouble(args[i]);
        if (2 < i) {
          urls.add(args[i]);
          if (isSingleUrl) {
            singleUseUrls.add(args[i]);
            isSingleUrl = false;
          } else if (isCsrf) {
            singleUseUrls.add(args[i]);
            csrfUrl = args[i];
            isCsrf = false;
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

        if (args[i].equals("--csrf")) {
          isCsrf = true;
        }
        if (args[i].equals("--gui")) {
          showGui = true;
        }
      }

    }

    System.setProperty("http.maxConnections", "" + users);

    rampDelay = runtimeMinutes * 60000.0 / users / 2;
    rampstart = users >= 100;
    System.out.println("Version: "+getVersion());
    System.out.println("Simulated users: " + users);
    System.out.println("Runtime in minutes: " + runtimeMinutes);
    System.out.println("Average waiting time in seconds: " + String.format("%,f",avgWait));
    System.out.println("Estimated clicks/s " + String.format("%,f",users / avgWait));
    System.out.println("Decimal mark: " + String.format("%,d",1000));
    System.out.println("Decimal separator: " + String.format("%,f",0.1));
    if (username != null && password != null) {
      System.out.println("Using " + username + " to log in.");
    }
    System.out.println();

    Main[] m = new Main[users + 1];
    Thread[] t = new Thread[users + 1];
    sessionIDs = new String[users + 1];
    csrfTokens = new String[users + 1];
    for (int i = 0; i < m.length; i++)
      m[i] = new Main(i - 1);
    start = System.nanoTime();
    for (int i = 0; i < m.length; i++)
      t[i] = new Thread(m[i]);
    start = System.nanoTime();
    long duration = 60L * runtimeMinutes;
    duration *= 1000000000;
    endTime = System.nanoTime() + duration;
    lastMsg = 0;
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
      System.out.println("url " + k + " requested " + tc + " times, " + "total time: " + nanoString(tt) + " s, avg time: "
          + nanoString((tt / tc)) + " s, bytes: " + format(sz) + " avg bytes: " + format(sz / tc));
    }

    System.out.println("avg requests/s: " + (totalRequests * 1000000000 / (System.nanoTime() - start)));

    long req = 0;
    int percentile = 90;
    long avg = 0;
    for (int i = 0; i < distribution.length; i++) {
      if (distribution[i] > 0) {
        avg += i * distribution[i];
      }
    }
    avg /= totalRequests;

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
          if (newreq * 100 > percentile * totalRequests) {
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

  static public String getVersion() {
    String version = Main.class.getPackage().getImplementationVersion();
    if (version==null) {
      version = "DEBUG";
    }
    return version;
  }

  /**
   * Shows command line help.
   */
  static void printHelp() {
    System.out.println("This is the novomind webtest tool");
    System.out.println("Version "+getVersion());
    System.out.println();
    System.out
        .println("It benchmarks the performance of a webservice, by requesting URLs and measuring the time of each request.");
    System.out.println("When all request are finished/have answered, a small statistic is presented.");
    System.out.println("");
    System.out.println("Usage:");
    System.out.println(" java -jar webtest.jar users runtime delay [OPTION...] URL...");
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
    System.out.println(" --gui     Show graphic output.");
    System.out.println(
        " -f file   The file from which URLs get loaded. One url per line. -s option is allowed in each line beginning.");
    System.out.println(" -v        Verbose output.");
    System.out.println("");
    System.out.println("URLs: [-s] URL[POST[POST-BODY]]");
    System.out.println(" -s        This URL will be requested only once, but for each user (i.e. add to basket, login etc)");
    System.out.println(
        " --csrf     This URL will be requested only once per user to retrieve the CSRF token that the page at this url holds.");
    System.out.println(" URL       URLs are expected in the general http://site/file.html?page=123 way.");
    System.out.println("           Also https is supported, even though the certificate is not validated!");
    System.out.println(" POST      Add POST at the end of the URL and all data after POST will be posted.");

    System.out.println("");
    System.out.println("Output:");
    System.out.println(
        " The output are pairs of two numbers, where the first number is an answer time in milliseconds and the second number, how often this time was achieved.");
    System.out.println(" In between are certain separating lines, like ---percentile---, for simple statistic reasons.");
    System.out.println("");
    System.out.println("");
    System.out.println("Examples:");
    System.out.println("java -jar webtest.jar 5 1 1 http://www.novomind.com/");
    System.out.println(
        " Five users request the site http://www.novomind.com/ for 1 minute with a 1 second delay between each request.");
    System.out.println("");
    System.out.println(
        "java -jar webtest.jar 25 3 0 http://www.novomind.com/hiddenarea/ https://www.google.com/ -u fritz -p geheim");
    System.out.println(
        " 25 users request the site http://www.novomind.com/hiddenarea/ and https://www.google.com/ for 3 minutes with a 0 second delay (creates a lot of requests!). For all (both) sites the user 'fritz' and password 'geheim' is used as authentication.");
    System.out.println("");
    System.out.println(
        "java -jar webtest.jar 1 1 10 http://www.shop.com/ -s https://www.shop.com/add/product/POSTitemId=dummyId");
    System.out.println(
        " 1 user requests for one minute with a request delay of 10 seconds the site www.shop.com over and over. The second url is requested only once (-s) and has additional POST information.");
    System.out.println(
        "java -jar webtest.jar 1 1 10 https://www.shop.com/add/product/POSTitemId=dummyId --csrf https://www.shop.com/home");
    System.out.println(
        " 1 user requests for one minute with a request delay of 10 seconds and sends POST requests to the url https://www.shop.com/add/product/ over and over. The second url is requested only once to retrieve the csrf token for each user (--csrf).");
  }

  private static Collection<String> loadUrlsFromFile(String filepath) throws IOException {
    FileInputStream fis = new FileInputStream(filepath);
    BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

    Collection<String> urls = new ArrayList<>();

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

  static String format(long l) {
    return String.format("%,d", l);
  }

  public void run() {
    if (showGui) {
      startGui();
    }
    int step = 0;
    long now;
    while ((now = System.nanoTime()) <= endTime) {
      if (userId == -1) {
        long t2 = now - start;
        if (step > 0)
          synchronized (lock) {
            double delta = (t2 - lastMsg);
            delta = delta * 0.000000128;
            double delta2 = t2;
            delta2 = delta2 * 0.000000128;
            double delta3 = (t2 - lastMsg);
            delta3 = delta3 * 0.000000001;
            long thru = Math.round((totalRequests - deltaRequests) / delta3);
            double avgThru = totalRequests * 1e9 / t2;
            if (gui != null) {
              if (thru > maxThrougput) {
                maxThrougput = thru;
              }
              througput.add(thru);
              avgThrougput.add(avgThru);
              gui.repaint();
            }
            message("Requests: " + totalRequests + ", requests/s: " + Math.round((totalRequests - deltaRequests) / delta3)
                + ", KBit: " + Math.round((totalLen - lastLen) / delta) + ", KBit avg: " + Math.round((totalLen) / delta2)
                + ", req/s avg: " + String.format("%,f",totalRequests * 1e9 / t2));

            deltaRequests = totalRequests;
            lastLen = totalLen;
            lastMsg = t2;
          }
        t2 = ++step;
        t2 *= 1000000000;
        t2 += start;
        t2 -= System.nanoTime();
        realSleep(t2 / 1000000);
      } else {
        doStep(userId, step++);
        if (errorCount >= MAX_ERRORS) {
          break;
        }
      }
    }
  }

  private synchronized void startGui() {
    if (userId == -1) {
      gui = new GraphGui(this);
      JFrame f = new JFrame() {
        protected void processWindowEvent(WindowEvent e) {
          super.processWindowEvent(e);
          if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            System.exit(0);
          }
        }

        synchronized public void setTitle(String title) {
          super.setTitle(title);
          enableEvents(AWTEvent.WINDOW_EVENT_MASK);
        }
      };

      f.add(gui);
      f.setTitle("novomind webtest");
      f.setSize(968, 533);
      f.setLocation(50, 50);
      f.setVisible(true);
    }
  }

  private void doStep(int user_id, int step) {
    doStep(user_id, step, true);
  }

  private void doStep(int user_id, int step, boolean wait) {
    if (rampstart && step == 0) {
      realSleep(Math.round(user_id * rampDelay));
      synchronized (lock) {
        activeUsers++;
      }
    }
    if (wait) {
      sleep(user_id, step);
    }
    if (System.nanoTime() > endTime) {
      return;
    }
    String url = "";
    url = urls.get(step % urls.size());

    if (!wait) {
      message(url);
    }
    boolean newSession = false;

    String sid = sessionIDs[user_id];
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

    boolean isCsrfUrl = csrfUrl != null && csrfUrl.equals(url);
    if (singleUseUrls.contains(url)) {
      if (urlAlreadyUsedMap == null) {
        urlAlreadyUsedMap = new HashMap<>();
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
    int response = -1;

    synchronized (lock) {
      activeRequests++;
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

      if (sessionIDs[user_id] != null && !newSession) {
        hc.addRequestProperty("Cookie", sessionIDs[user_id]);
      } else {
        message("create new session");
      }
      hc.addRequestProperty("User-Agent", "novomind/webtest");
      hc.addRequestProperty("Accept-Encoding", "gzip");

      if (username != null && password != null) {
        hc.addRequestProperty("Authorization", "Basic " + encode(username + ":" + password));
      }
      // else
      if (post) {
        hc.setRequestMethod("POST");
        hc.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        hc.setRequestProperty("Content-Length", "" + Integer.toString(postData.getBytes().length));
        hc.setRequestProperty("X-Csrf-Token", csrfTokens[user_id]);
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
            if (sessionIDs[user_id] != null)
              extra = extra + "\nsession " + sessionIDs[user_id];
            if (!"".equals(postData))
              extra = extra + "\npost data " + postData;
            message(extra + "\n" + url + " -> " + response);
          }
        for (int i = 1; ; i++) {
          String val = hc.getHeaderField(i);
          if (val == null)
            break;
          if (val.startsWith(SESSIONID_COOKIE)) {
            String session = val.substring(0, val.indexOf(";"));
            if (!session.equals(sessionIDs[user_id])) {
              sessionIDs[user_id] = val.substring(0, val.indexOf(";"));
              message("saved session id " + sessionIDs[user_id] + ", cookie was " + val);
            }
            break;
          }
        }
        if (response == 302 && nexturl.length() != 0) {
          String s = sessionIDs[user_id];
          if (s != null) {
            s = s.substring(s.indexOf("=") + 1);
          }
          nexturl = nexturl.replace("##>SESSIONID<##", s);
          String location = hc.getHeaderField("Location");
          if (!nexturl.equals(location))
            message("OUT OF SYNC (302 redirect mismatch): expected " + nexturl + ", got " + location);
        }
        if (isCsrfUrl && csrfTokens[user_id] == null) {
          try {
            BufferedReader buff = new BufferedReader(new InputStreamReader(hc.getInputStream()));
            String line;
            String csrf;
            while ((line = buff.readLine()) != null) {
              String csrfText = "name=\"_csrf\" value=\"";
              int index = line.indexOf(csrfText);

              if (index == -1) {
                csrfText = "name=\"_csrf\" content=\"";
                index = line.indexOf(csrfText);
              }

              if (index != -1) {
                String t = line.split(csrfText)[1];
                csrf = t.split("\"")[0];
                csrfTokens[user_id] = csrf;
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
      boolean first = true;
      while (true) {
        int j = is.read(buffer);

        if (j < 0)
          break;

        len += j;
        String s = new String(buffer, 0, j);

        synchronized (lock) {
          totalLen += j;
        }
      }
      is.close();
    } catch (Exception e) {
      message(e.toString() + " for " + url);
      synchronized (lock) {
        errorCount++;
      }
    }
    if (time == 0)
      time = System.nanoTime() - requestStart;
    synchronized (lock) {
      // if (time >= 1000000000) message ("url " + url + " time " + time);
      int idx = (int) Math.round(time / 1000000.0);
      if (idx >= distribution.length - 1)
        idx = distribution.length - 1;
      distribution[idx]++;
      activeRequests--;

      Long l = (Long) urltimes.get(url);
      if (l == null)
        l = new Long(0);
      urltimes.put(url, new Long(l + time));
      l = (Long) urlcounts.get(url);
      if (l == null)
        l = new Long(0);
      urlcounts.put(url, new Long(l + 1));
      l = (Long) urlsize.get(url);
      if (l == null)
        l = new Long(0);
      urlsize.put(url, new Long(l + len));
      totalRequests++;

    }
    buffer = null;
    return time;
  }

  static String encode(String source) {
    return Base64.getEncoder().encodeToString(source.getBytes());
  }

  private void message(String s) {
    if (verbose) {
      System.out.println("users: " + activeUsers + " active: " + activeRequests + " time: " + nanoString(System.nanoTime() - start)
          + (userId != -1 ? (" user " + userId + ": ") : " ") + s);
    }
  }

  private static String nanoString(long l) {
    double elapsedTimeInSeconds = TimeUnit.MILLISECONDS.convert(l, TimeUnit.NANOSECONDS) / 1000.0;
    return (String.format("%,f  ",elapsedTimeInSeconds));
  }

  private void sleep(int user_id, int step) {
    if (avgWait > 0) {
      long now = System.nanoTime();
      if (step == 0)
        startTime = now;
      long max_end = Math.round(startTime + (step + 2) * avgWait * 1000000000);
      long waittime;
      if (max_end < now) {
        // message ("out of sync, server overloaded");
        return;
      } else {
        waittime = Math.round((max_end - now) * 0.000001 * Math.random());
      }
      long resttime = (endTime - now) / 1000000;
      if (resttime < 0)
        resttime = 0;
      if (resttime < waittime)
        waittime = resttime + 1;
      realSleep(waittime);
    }
  }

  private void realSleep(long waittime) {
    if (waittime > 0)
      try {
        Thread.sleep(waittime);
      } catch (Exception e) {
      }
  }
}
