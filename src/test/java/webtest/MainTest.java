package webtest;

import java.util.ArrayList;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

public class MainTest {

  @Rule
  public final ExpectedSystemExit exit = ExpectedSystemExit.none();

  @Test
  public void test() {
    exit.expectSystemExitWithStatus(1);
    ArrayList<String> args = new ArrayList<>();
    try {
      Main.main(args.toArray(new String[args.size()]));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
