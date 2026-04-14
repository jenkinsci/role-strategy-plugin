package org.jenkinsci.plugins.rolestrategy.playwright;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class RoleStrategyPage<T extends RoleStrategyPage<T>> {
  private static final Logger log = LoggerFactory.getLogger(RoleStrategyPage.class);
  protected final String pageUrl;
  protected final Page page;

  protected RoleStrategyPage(Page page, String pageUrl) {
    this.page = page;
    this.pageUrl = pageUrl;
  }

  @SuppressWarnings("unchecked")
  T waitForLoaded() {
    isAtUrl(pageUrl);
    return (T) this;
  }

  public T goTo() {
    log.info("Navigating to {}", pageUrl);
    // Retry navigation if it's aborted due to a concurrent redirect (e.g. form submission)
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        page.navigate(pageUrl);
        return waitForLoaded();
      } catch (PlaywrightException e) {
        if (e.getMessage().contains("ERR_ABORTED") && attempt < 2) {
          log.info("Navigation aborted, retrying (attempt {})", attempt + 1);
          page.waitForTimeout(1000);
          continue;
        }
        throw e;
      }
    }
    return waitForLoaded();
  }

  void isAtUrl(String url) {
    log.info("Waiting for url to be {}", url);
    try {
      page.waitForURL(url + "**");
    } catch (TimeoutError e) {
      throw new TimeoutError(
          "Timeout waiting for URL to be " + url + " but it was " + page.url(), e);
    }
  }
}
