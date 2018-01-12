package com.novomind.webtest;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;

public class GraphGui extends Canvas {

  private final Main main;

  private Font font = new Font("Courier", Font.BOLD, 12);

  public GraphGui(Main main) {
    this.main = main;
  }

  public void paint(Graphics g) {
    Dimension d = this.getSize();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, d.width, d.height);
    g.setColor(Color.GREEN);
    {
      int x = 0;
      while (x < d.width) {
        g.drawLine(x, 0, x, d.height - 1);
        x += 50;
      }
    }
    {
      int y = 0;
      while (y < d.height) {
        g.drawLine(0, y, d.width - 1, y);
        y += 50;
      }
    }
    int end = main.througput.size();
    double sum = 0.0;
    if (main.maxThrougput > 0) {
      int combine = end / d.width;
      if (combine * d.width < end) {
        combine++;
      }
      g.setColor(Color.RED);
      {
        int x = 0;
        int j = 0;
        while (j < end) {
          int k = 0;
          long l = 0;
          for (int c = 0; c < combine; c++) {
            if (j < end) {
              long t = main.througput.get(j++);
              l += t;
              sum += t;
              k++;
            }
          }
          int y = (int) ((l * d.height) / (main.maxThrougput * k));
          g.drawLine(x, d.height - 1, x, d.height - 1 - y);
          x++;
        }
      }
      int lasty = 0;
      g.setColor(Color.BLUE);
      int x = 0;
      int j = combine - 1;
      while (j < end) {
        int y = (int) (d.height - 1 - ((main.avgThrougput.get(j) * d.height) / main.maxThrougput));
        if (x == 0) {
          g.drawLine(x, y, x, y);
        } else {
          g.drawLine(x - 1, lasty, x, y);
        }
        lasty = y;
        x++;
        j += combine;
      }
    }
    g.setColor(Color.BLACK);
    g.setFont(font);
    double mbit = (System.nanoTime() - Main.start);
    mbit = main.totalLen / mbit; // byte per ns
    mbit *= 1000000000.0; // byte per s
    mbit /= (128 * 1024); // mbit per s
    g.drawString(String.format("avg. MBit/s %.2f", mbit), 0, d.height - 70);
    g.drawString("total requests " + Main.totalRequests, 0, d.height - 50);
    if (end > 0) {
      g.drawString(String.format("avg. requests/s %.2f", (sum / end)), 0, d.height - 30);
    }
    g.drawString("max. requests/s " + main.maxThrougput, 0, d.height - 10);
  }

}

