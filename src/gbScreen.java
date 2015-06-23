import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.util.Random;


public class gbScreen extends JPanel{

	private static final Random random = new Random();

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
		for (int y = 0; y < 144; y++) {
			for (int x = 0; x < 160; x++) {
				canvas.setRGB(x, y, gpu.tileSet[x][y]);
				//if (!(gpu.cpu.memory._inBIOS))
				//	System.out.println("x="+x+" y="+y+" colour="+gpu.tileSet[x][y]);
			}
		}
		repaint();
	}

	public void resetScreen(){}

}