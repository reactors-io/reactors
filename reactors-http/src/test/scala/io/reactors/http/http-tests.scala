package io.reactors
package http



import io.reactors.test._
import org.openqa.selenium._
import org.openqa.selenium.chrome._
import org.openqa.selenium.interactions._
import org.openqa.selenium.support.ui._
import org.scalatest._
import scala.collection.JavaConverters._



class HttpTest extends FunSuite {
  test("test basic http scenarios") {
    runXvfbTest("io.reactors.http.HttpTest")
  }
}


object HttpTest {
  def main(args: Array[String]) {
    // Initialize driver.
    System.setProperty("webdriver.chrome.driver", "tools/chromedriver")
    val options = new ChromeOptions
    options.setBinary("/usr/bin/chromium-browser")
    val driver = new ChromeDriver(options)

    // Initialize http server.
    val config = ReactorSystem.customConfig("")
    val bundle = new ReactorSystem.Bundle(JvmScheduler.default, config)
    val system = new ReactorSystem("http-test-system", bundle)

    var error: Throwable = null
    try {
      // Run tests.
      runTests(driver, system)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        error = t
    } finally {
      // Quit.
      Thread.sleep(2000)
      driver.quit()
      system.shutdown()
      Thread.sleep(1000)
      if (error == null) System.exit(0)
      else System.exit(1)
    }
  }

  def runTests(driver: WebDriver, system: ReactorSystem) {
    driver.get("localhost:9500")

    // Run shell tests.
  }
}
