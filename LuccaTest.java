import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.client.ClientUtil;
import net.lightbody.bmp.core.har.Har;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;


public class LuccaTest {

  private static final String LOGIN = "";
  private static final String PASSWORD = "";
  private static final String LUCCA_FACE_ADRESS = "";
  // map to fill with hash and names from logs
  static Map<String, String> names = new HashMap<>() {{
    put("someHash", "someName");
  }};


  static Pattern pattern = Pattern.compile("questions/\\d+/picture");

  private static WebDriver getWebDriver(BrowserMobProxy proxy) {
    System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver");
    ChromeOptions options = new ChromeOptions();
    options.setProxy(ClientUtil.createSeleniumProxy(proxy));
    options.addArguments("--disable-logging");
    options.addArguments("--no-sandbox");
    options.addArguments("--disable-dev-shm-usage");
    options.addArguments(
        "user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.81 Safari/537.36");
    return new ChromeDriver(options);
  }

  private static void navigateToGameAndStart(WebDriver driver) throws InterruptedException {

    driver.get(LUCCA_FACE_ADRESS);
    WebElement param = driver.findElement(By.id("details-button"));
    param.click();

    WebElement conf = driver.findElement(By.id("proceed-link"));
    conf.click();

    WebElement usernameField = driver.findElement(By.id("username-input"));
    WebElement passwordField = driver.findElement(By.id("password-input"));

    usernameField.sendKeys(LOGIN);
    passwordField.sendKeys(PASSWORD);
    WebElement loginButton = driver.findElement(By.id("login-submit-button"));
    loginButton.click();
    Thread.sleep(2000);

  }

  public static void main(String[] args) throws InterruptedException {
    while (true) {
      BrowserMobProxy proxy = new BrowserMobProxyServer();
      proxy.start();
      WebDriver driver = getWebDriver(proxy);
      proxy.newHar(LUCCA_FACE_ADRESS);
      navigateToGameAndStart(driver);

      Set<Cookie> cookies = driver.manage().getCookies();
      Thread.sleep(2000);
      WebElement start = driver.findElement(By.className("rotation-loader"));
      start.click();
      Set<String> printed = new HashSet<>();
      Set<String> nameset = new HashSet<>();
      Har har = proxy.getHar();

      while (nameset.size() < 10) {
        try {
          if (har.getLog().getEntries() == null) {
            return;
          }
          // check network requests
          har.getLog().getEntries().parallelStream().forEach(entry -> {
            // check if request unseen and with content
            if (entry.getRequest() != null && entry.getResponse() != null && !printed.contains(
                entry.getRequest().getUrl())) {
              // add to seen to avoid reprocess
              printed.add(entry.getRequest().getUrl());
              // check it's a picture request
              Matcher matcher = pattern.matcher(entry.getRequest().getUrl());
              if (!matcher.find()) {
                return;
              }
              // compute picture hash
              String h;
              try {
                h = downloadImageAndComputeHash(entry.getRequest().getUrl(), cookies);
              } catch (IOException ex) {
                throw new RuntimeException(ex);
              }
              System.out.println("h ->" + h);

              // get name and add to result to detect end game
              String name = names.get(h);
              System.out.println("name : " + name);
              nameset.add(name);
              if (name == null) {
                System.out.println("missing : " + h);
              }

              // keep trying to click on button with name inside
              boolean keep = true;
              while (keep) {
                try {
                  driver.findElement(By.xpath("//button[contains(text(), '" + name + "')]"))
                      .click();
                  keep = false;
                } catch (Exception f) {
                }
              }
            }
          });
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      Thread.sleep(10_000);
      driver.quit();
      proxy.stop();
    }

  }


  public static String downloadImageAndComputeHash(String imageUrl, Set<Cookie> cookies)
      throws IOException {
    URL url = new URL(imageUrl);

    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(5000);
    for (Cookie cookie : cookies) {
      connection.addRequestProperty("Cookie", cookie.getName() + "=" + cookie.getValue());
    }

    if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
      try (InputStream inputStream = connection.getInputStream()) {
        return computeHashFromStream(inputStream);
      }
    } else {
      throw new IOException("Failed to download image: HTTP " + connection.getResponseCode());
    }
  }

  private static String computeHashFromStream(InputStream inputStream) {
    try {
      MessageDigest digest = MessageDigest.getInstance("MD5");

      byte[] buffer = new byte[4096];
      int bytesRead;
      while ((bytesRead = inputStream.read(buffer)) != -1) {
        digest.update(buffer, 0, bytesRead);
      }
      byte[] hashBytes = digest.digest();
      StringBuilder sb = new StringBuilder();
      for (byte b : hashBytes) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException | IOException e) {
      e.printStackTrace();
      return null;
    }
  }
}
