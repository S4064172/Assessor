package TestCases.PO;

// Generated by Selenium IDE
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNot.not;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Alert;
import org.openqa.selenium.Keys;
import java.util.*;
import java.net.MalformedURLException;
import java.net.URL;

public class Login {

    WebDriver driver;

    JavascriptExecutor js;

    Map<String, Object> vars;

    public Login(WebDriver driver, JavascriptExecutor js, Map<String, Object> vars) {
        this.driver = driver;
        this.js = js;
        this.vars = vars;
    }

    public void ineer1(String key1) {
        By elem = By.name("_username");
        MyUtils.WaitForElementLoaded(driver, elem);
        driver.findElement(elem).clear();
        driver.findElement(elem).sendKeys(key1);
    }

    public void inner2(String key2) {
        By elem = By.name("1");
        MyUtils.WaitForElementLoaded(driver, elem);
        driver.findElement(elem).clear();
        driver.findElement(elem).sendKeys(key2);
    }

    public void inner3(String key3) {
        By elem = By.name("2");
        MyUtils.WaitForElementLoaded(driver, elem);
        driver.findElement(elem).clear();
        driver.findElement(elem).sendKeys(key3);
    }

    public void TEST1(String key1, String key2, String key3, String key4, boolean key5) {
        if (key1 != null)
            ineer1(key1);
        if (key2 != null)
            inner2(key2);
        if (key3 != null)
            inner3(key3);
        if (key4 != null)
            ineer1_1(key4);
        if (key5 != false)
            checkCorrectPage();
    }

    public void TEST2(String key1, String key2) {
        ineer1(key1);
        inner2(key2);
    }

    public void ineer1_1(String key1) {
        By elem = By.name("_username");
        MyUtils.WaitForElementLoaded(driver, elem);
        driver.findElement(elem).click();
        driver.findElement(elem).clear();
        driver.findElement(elem).sendKeys(key1);
    }

    public void checkCorrectPage() {
        By elem = By.cssSelector(".logo-lg");
        MyUtils.WaitForElementLoaded(driver, elem);
    }

    public String set_CSSSELECTOR_logo_lg() {
        By elem = By.cssSelector(".logo-lg");
        MyUtils.WaitForElementLoaded(driver, elem);
        return driver.findElement(elem).getText();
    }

    public void inner2_1(String key1) {
        By elem = By.name("1");
        MyUtils.WaitForElementLoaded(driver, elem);
        driver.findElement(elem).click();
        driver.findElement(elem).clear();
        driver.findElement(elem).sendKeys(key1);
    }
}
