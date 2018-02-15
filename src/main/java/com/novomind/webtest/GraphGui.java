package com.novomind.webtest;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;

public class GraphGui extends Canvas {

  private final Main main;

  private static Font font = new Font("Courier", Font.BOLD, 20);

  private static Color colorGrid = Color.GREEN;
  private static Color colorStatsText = Color.BLACK;
  private static Color colorBackground = Color.WHITE;
  private static Color colorAvgThrougput = Color.BLUE;
  private static Color colorThrougput = Color.RED;

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
    g.drawString(String.format("avg. MBit/s %.2f", mbit), 10, d.height - 70);
    g.drawString("total requests " + Main.totalRequests, 10, d.height - 50);
    if (end > 0) {
      g.drawString(String.format("avg. requests/s %.2f", (sum / end)), 10, d.height - 30);
    }
    g.drawString("max. requests/s " + main.maxThrougput, 10, d.height - 10);
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

