import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class gbScreen extends JPanel{

	private BufferedImage canvas;
	GPU gpu;

	public gbScreen(int width, int height, GPU gpu){
		this.gpu = gpu;
		canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		fillCanvas();
	}

	public Dimension getPreferredSize() {
		return new Dimension(canvas.getWidth(), canvas.getHeight());
	}

	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;
		g2.drawImage(canvas, null, null);
	}


	public void fillCanvas() {
		for (int y = 0; y < canvas.getHeight(); y++) {
			for (int x = 0; x < canvas.getWidth(); x++) {
				int r = gpu.tileSet[x][y][0];
				int g = gpu.tileSet[x][y][1];
				int b = gpu.tileSet[x][y][2];
				int col = (r << 16) | (g << 8) | b;
				canvas.setRGB(x, y, col);
			}
		}
		repaint();
	}

    // public void drawLine(Color c, int x1, int y1, int x2, int y2) {
    //     // Implement line drawing
    //     repaint();
    // }

    // public void drawRect(Color c, int x1, int y1, int width, int height) {
    //     int color = c.getRGB();
    //     // Implement rectangle drawing
    //     for (int x = x1; x < x1 + width; x++) {
    //         for (int y = y1; y < y1 + height; y++) {
    //             canvas.setRGB(x, y, color);
    //         }
    //     }
    //     repaint();
    // }

    // public void drawOval(Color c, int x1, int y1, int width, int height) {
    //     // Implement oval drawing
    //     repaint();
    // }

}