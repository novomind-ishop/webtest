package com.novomind.webtest;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

import com.google.common.base.Splitter;

public class MainTest {

  @Rule
  public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();
  @Rule
  public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();
  @Rule
  public final ExpectedSystemExit exit = ExpectedSystemExit.none();

  @Test
  public void testHelp() throws Exception {
    exit.expectSystemExitWithStatus(1);
    exit.checkAssertionAfterwards(() -> assertEquals("This is the novomind webtest tool", outLines().get(0)));
    exit.checkAssertionAfterwards(() -> assertEquals("", errLines().get(0)));
    main("--help");
  }

  @Test
  public void test() throws Exception {
    exit.expectSystemExitWithStatus(1);
    exit.checkAssertionAfterwards(() -> assertEquals("This is the novomind webtest tool", outLines().get(0)));
    exit.checkAssertionAfterwards(() -> assertEquals("", errLines().get(0)));
    main();
  }

  @Test
  public void testFromatLong() {
    assertEquals("22", Main.format(22L));
    assertEquals("222", Main.format(222L));
    assertEquals("1.122", Main.format(1122L));
    assertEquals("2.463.345", Main.format(2463345L));
  }

  @Test
  public void testEncode() {
    assertEquals("d3d3", Main.encode("www"));
    assertEquals("YXNkZg==", Main.encode("asdf"));
  }

  private List<String> outLines() {
    return lines(systemOutRule.getLogWithNormalizedLineSeparator());
  }

  private List<String> errLines() {
    return lines(systemErrRule.getLogWithNormalizedLineSeparator());
  }

  private List<String> lines(String string) {
    return Splitter.on("\n").splitToList(string);
  }

  private void main(String... argsv) throws Exception {
    Main.main(argsv);
  }
}
