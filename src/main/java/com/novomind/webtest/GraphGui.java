package com.novomind.webtest;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;

public class GraphGui extends Canvas {

  private final Main main;

  private static Font font = new Font("Courier", Font.BOLD, 18);

  private static Color colorGrid = new Color(0, 43, 54);
  private static Color colorStatsText = new Color(147, 161, 161);
  private static Color colorBackground = new Color(7, 54, 66);
  private static Color colorAvgThrougput = new Color(38, 139, 210);
  private static Color colorThrougput = new Color(220, 50, 47);

  public GraphGui(Main main) {
    this.main = main;
  }

  public void paint(Graphics g) {
    Dimension d = getSize();
    drawGrid(g, d);
    int end = main.througput.size();
    double sum = 0.0;
    if (main.maxThrougput > 0) {
      int combine = end / d.width;
      if (combine * d.width < end) {
        combine++;
      }
      sum = drawThrougput(g, d, end, sum, combine);

      drawAvgThrougput(g, d, end, combine);
    }
    drawStats(g, d, end, sum);
  }

  private void drawAvgThrougput(Graphics g, Dimension d, int end, int combine) {
    int lasty = 0;
    g.setColor(GraphGui.colorAvgThrougput);
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

  private double drawThrougput(Graphics g, Dimension d, int end, double sum, int combine) {
    g.setColor(colorThrougput);

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
    return sum;
  }

  private void drawStats(Graphics g, Dimension d, int end, double sum) {
    g.setColor(GraphGui.colorStatsText);
    g.setFont(font);
    double mbit = (System.nanoTime() - Main.start);
    mbit = main.totalLen / mbit; // byte per ns
    mbit *= 1000000000.0; // byte per s
    mbit /= (128 * 1024); // mbit per s
    int x = 25;
    int y = 15;
    g.drawString(String.format("avg.  MBit/s %.2f", mbit), x, y + 40);
    g.drawString("total requests " + Main.totalRequests, x, y + 20);
    if (end > 0) {
      g.drawString(String.format("avg.  requests/s %.2f", (sum / end)), x, y + 60);
    }
    g.drawString("max.  requests/s " + main.maxThrougput, x, y + 80);
  }

  private void drawGrid(Graphics g, Dimension d) {
    g.setColor(GraphGui.colorBackground);
    g.fillRect(0, 0, d.width, d.height);
    g.setColor(GraphGui.colorGrid);

    int x = 0;
    while (x < d.width) {
      g.drawLine(x, 0, x, d.height - 1);
      x += 50;
    }

    int y = 0;
    while (y < d.height) {
      g.drawLine(0, y, d.width - 1, y);
      y += 50;
    }

  }

}

