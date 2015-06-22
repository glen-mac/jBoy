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

	private Color randomColor() {
		return new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
	}

	public void fillCanvas() {
		for (int y = 0; y < canvas.getHeight(); y++) {
			for (int x = 0; x < canvas.getWidth(); x++) {
				/*gpu.tileSet[x][y][0] = 0 ;
				gpu.tileSet[x][y][1] = 0 ;
				gpu.tileSet[x][y][2] = 0 ;
				int r = gpu.tileSet[x][y][0];
				int g = gpu.tileSet[x][y][1];
				int b = gpu.tileSet[x][y][2];
				int col = (r << 16) | (g << 8) | b;*/
				canvas.setRGB(x, y, gpu.tileSet[x][y]);
				//if (!(gpu.cpu.memory._inBIOS))
				//	System.out.println("x="+x+" y="+y+" colour="+gpu.tileSet[x][y]);
			}
		}
		repaint();
	}

	public void resetScreen(){

	}

}