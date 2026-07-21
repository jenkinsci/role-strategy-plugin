package org.jenkinsci.plugins.rolestrategy.playwright.config;

import com.microsoft.playwright.junit.Options;
import com.microsoft.playwright.junit.OptionsFactory;

/**
 * Playwright options for the role-strategy UI tests. Runs Chromium headless by
 * default; pass {@code -Dplaywright.headless=false} to watch the browser.
 */
public class PlaywrightConfig implements OptionsFactory {

  @Override
  public Options getOptions() {
    boolean headless = Boolean.parseBoolean(System.getProperty("playwright.headless", "true"));
    return new Options().setBrowserName("chromium").setHeadless(headless);
  }
}
