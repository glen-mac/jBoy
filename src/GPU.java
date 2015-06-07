import javax.swing.JFrame;

public class GPU{

	public int mode;
	public int modeClock;
	public int line;
	public int tileSet[][];
	Z80 cpu;

	public void step(){
	switch(mode){
		case 0:	{	//Horizontal blank
			if(modeClock>=204){
				modeClock = 0;
				line++;
				if (line == 143){ //reached height
					mode = 1;	//begin vertical blank
					renderFrame(); //render image
				}
				else {
					mode = 2;
				}
			}

			break;
		}
		case 1:	{	//Vertical blank
			if (modeClock >= 456){
				modeClock = 0;
				line++;
				if (line == 153){	//Full frame (scans and vblank)
					mode = 2;
					line = 0;
				}
			}
			break;
		}
		case 2:	{	//Scanline (accessing OAM)
			if (modeClock >= 80){
				mode = 3;
				modeClock = 0;
			}
			break;
		}
		case 3:	{	//Scanline (accessing VRAM)
			if (modeClock >= 172){

				// Enter hblank
				mode = 0;
				modeClock = 0;
				renderScan();
			}
			break;
		}
	}	
	}

	public void renderFrame(){}

	public void renderScan(){}

	public void reset(){
		tileSet = new int[384][8];
		for(int i = 0; i< 384; i++){
			for(int j = 0; j< 8; j++){
				//tileSet[i][j] = {0, 0, 0, 0, 0, 0, 0, 0};
			} 
		} 
	}

	public GPU(Z80 cpu){
		this.cpu = cpu;
		int width = 160;
		int height = 144;
		JFrame frame = new JFrame("jBoy");

		gbScreen screen = new gbScreen(width, height);

		frame.add(screen);
		frame.pack();
		frame.setVisible(true);
		frame.setResizable(false);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}

}