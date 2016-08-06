package io.reactors
package debugger



import org.openqa.selenium._
import org.openqa.selenium.chrome._
import org.openqa.selenium.support.ui._



object DebuggerTest {
  def main(args: Array[String]) {
    // Initialize driver.
    System.setProperty("webdriver.chrome.driver", "tools/chromedriver")
    val options = new ChromeOptions
    options.setBinary("/usr/bin/chromium-browser")
    val driver = new ChromeDriver(options)

    // Initialize debugger.
    val config = ReactorSystem.customConfig("""
      debug-api = {
        name = "io.reactors.debugger.WebDebugger"
      }
    """)
    val bundle = new ReactorSystem.Bundle(Scheduler.default, config)
    val system = new ReactorSystem("web-debugger-test-system", bundle)

    // Run tests.
    runTests(driver, system)

    // Quit.
    Thread.sleep(2000)
    driver.quit()
    system.shutdown()
    Thread.sleep(1000)
    System.exit(0)
  }

  def runTests(driver: WebDriver, system: ReactorSystem) {
    driver.get("localhost:8888")
    Thread.sleep(2000)
  }
}
