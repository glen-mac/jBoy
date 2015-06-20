import javax.swing.JFrame;

public class GPU {

	public static final int TILE_SIZE = 16;

	/* Every two bits in the palette data byte represent a colour. 
	Bits 7-6 maps to colour id 11, bits 5-4 map to colour id 10, 
	bits 3-2 map to colour id 01 and bits 1-0 map to colour id 00.*/
	public static final int CLR_PLT_BKG = 0xFF47;
	public static final int CLR_PLT_SPRT_1 = 0xFF48;
	public static final int CLR_PLT_SPRT_2 = 0xFF49;

	public static final int SCROLL_Y = 0xFF42;
	public static final int SCROLL_X = 0xFF43;
	public static final int WINDOW_Y = 0xFF4A;
	public static final int WINDOW_X = 0xFF4B; // true value is this - 7

	public static final int SCANLINE_NUM = 0xFF44;
	public static final int SCANLINE_COMP = 0xFF45;

	public static final int LCD_CONTR_REG = 0xFF40;
	/* Bit 7 - LCD Display Enable (0=Off, 1=On)
	Bit 6 - Window Tile Map Display Select (0=9800-9BFF, 1=9C00-9FFF)
	Bit 5 - Window Display Enable (0=Off, 1=On)
	Bit 4 - BG & Window Tile Data Select (0=8800-97FF, 1=8000-8FFF)
	Bit 3 - BG Tile Map Display Select (0=9800-9BFF, 1=9C00-9FFF)
	Bit 2 - OBJ (Sprite) Size (0=8x8, 1=8x16)
	Bit 1 - OBJ (Sprite) Display Enable (0=Off, 1=On)
	Bit 0 - BG Display (for CGB see below) (0=Off, 1=On) */


	public static final int LCD_STATUS = 0xFF41;
	/*
	Bits 0&1: 	00: H-Blank
				01: V-Blank
				10: Searching Sprites Atts
				11: Transfering Data to LCD Driver 
	Bit 2: Coincidence Interupt Request
	Bit 3: Mode 0 Interupt Enabled
	Bit 4: Mode 1 Interupt Enabled
	Bit 5: Mode 2 Interupt Enabled 
	Bit 6: Coincidence Interupt Enabled */

	private boolean second = false;

	private int mode = 2;
	private int modeClock;
	private int line;
	public int tileSet[][][] = new int[160][144][3];
	public Z80 cpu;
	private gbScreen screen;

	public void step(int cycles) {

		int status = cpu.memory.readByte(LCD_STATUS);
		boolean reqInt = false;

		System.out.println("GPU MODE = " + mode);

		if (isLCDEnabled()) {
			modeClock += cycles;
			System.out.println("LCD ENABLED");
		}
		else {
			System.out.println("LCD DISABLED");
			status = cpu.bitSet(status & 0xFC, 0); //1111 1101
			//mode = 1;
			modeClock = 0;
			line = 0;
			cpu.memory.writeByte(SCANLINE_NUM, 0);
			cpu.memory.writeByte(LCD_STATUS, status);
			return;
		}

			System.out.println("MODECLOCK = " + modeClock);
			System.out.println("LINE (scaline) = " + (int) cpu.memory.portsIO[SCANLINE_NUM - 0xFF00] + " (0x"+Integer.toHexString(cpu.memory.portsIO[SCANLINE_NUM - 0xFF00]).toUpperCase()+")");
			System.out.println("ZERO FLAG = " + cpu.getFlag(cpu.FLAG_ZERO));

		switch (mode) {

			case 0:
				 //Horizontal blank
					if (modeClock >= 204) {
						modeClock = 0;
						System.out.println("SCAN Before = " + cpu.memory.portsIO[SCANLINE_NUM - 0xFF00]);
						cpu.memory.portsIO[SCANLINE_NUM - 0xFF00]++;
						System.out.println("Scan After = " + cpu.memory.portsIO[SCANLINE_NUM - 0xFF00]);
						//line++;
						if (cpu.memory.portsIO[SCANLINE_NUM - 0xFF00] == 143) { //reached height
							cpu.requestInterupt(0);

							mode = 1; //begin vertical blank
							status = cpu.bitSet(status, 0);
							status = cpu.bitReset(status, 1);
							reqInt = cpu.bitTest(status, 4);
							renderFrame(); //render image
						} else {
							renderScan();
							mode = 2;
							status = cpu.bitSet(status, 1);
							status = cpu.bitReset(status, 0);
							reqInt = cpu.bitTest(status, 5);
						}
					}
				break;
				case 1:
				//Vertical blank
					//if (second == true)
						


					if (modeClock >= 456) {
						modeClock = 0;
						//line++;
						cpu.memory.portsIO[SCANLINE_NUM - 0xFF00]++;
						System.out.println("LINE (scaline) = " + (int) cpu.memory.portsIO[SCANLINE_NUM - 0xFF00]);
						//System.exit(1);
						if (cpu.memory.portsIO[SCANLINE_NUM - 0xFF00] == 153) { //Full frame (scans and vblank)
							cpu.memory.portsIO[SCANLINE_NUM - 0xFF00] = 0;
							mode = 2;
							status = cpu.bitSet(status, 1);
							status = cpu.bitReset(status, 0);
							reqInt = cpu.bitTest(status, 5);
						}
					}
				break;
				case 2:
				//Scanline (accessing OAM)
					if (modeClock >= 80) {
						mode = 3;
						modeClock = 0;
					}
				break;
				case 3:
				//Scanline (accessing VRAM)
					if (modeClock >= 172) {

						// Enter hblank
						mode = 0;
						status = cpu.bitReset(status, 1);
						status = cpu.bitReset(status, 0);
						reqInt = cpu.bitTest(status, 3);

						modeClock = 0;
					}
				break;
			}

			if (reqInt)
				cpu.requestInterupt(1);

			if (cpu.memory.readByte(SCANLINE_NUM) == cpu.memory.readByte(SCANLINE_COMP)){
				status = cpu.bitSet(status, 2);
				if(cpu.bitTest(status, 6))
					cpu.requestInterupt(1);
			}
			else
				status = cpu.bitReset(status, 2);

			cpu.memory.writeByte(LCD_STATUS, status);

		}

		private boolean isLCDEnabled() {
			return cpu.bitTest(cpu.memory.readByte(LCD_CONTR_REG), 7);
		}

		public void renderFrame() {
			screen.fillCanvas();
		}

		public void renderScan(){
			int control = cpu.memory.readByte(LCD_CONTR_REG);
			if (cpu.bitTest(control, 0))
				renderTiles();
			if (cpu.bitTest(control, 1))
				renderSprites();
		}

		private void renderSprites(){

		}

		private void renderTiles(){
			int scrollY = cpu.memory.readByte(SCROLL_Y);
			int scrollX = cpu.memory.readByte(SCROLL_X);
			int windowX = cpu.memory.readByte(WINDOW_X) - 7; //shifted 7 pixels
			int windowY = cpu.memory.readByte(WINDOW_Y);
			int scanLine = cpu.memory.readByte(SCANLINE_NUM);
			boolean signed = false;
			boolean inWindow = false;
			int tileData;
			int tileListMem;
			int tileAddr;
			int tileDataAddr;
			int yPos;
			int xPos;
			int tileCol;
			int tileRow;
			int tileLine;
			int colourBit;
			int data1;
			int data2;
	/* Bit 7 - LCD Display Enable (0=Off, 1=On)
	Bit 6 - Window Tile Map Display Select (0=9800-9BFF, 1=9C00-9FFF)
	Bit 5 - Window Display Enable (0=Off, 1=On)
	Bit 4 - BG & Window Tile Data Select (0=8800-97FF, 1=8000-8FFF)  8800 IS SIGNED
	Bit 3 - BG Tile Map Display Select (0=9800-9BFF, 1=9C00-9FFF)
	Bit 2 - OBJ (Sprite) Size (0=8x8, 1=8x16)
	Bit 1 - OBJ (Sprite) Display Enable (0=Off, 1=On)
	Bit 0 - BG Display (for CGB see below) (0=Off, 1=On) */

			if (cpu.bitTest(LCD_CONTR_REG, 5) && (scanLine >= windowY))
				inWindow = true;

			if (cpu.bitTest(LCD_CONTR_REG, 4))
				tileData = 0x8000;
			else {
				signed = true;
				tileData = 0x8800;
			}

			if (inWindow)
				tileListMem = (cpu.bitTest(LCD_CONTR_REG, 6)) ? 0x9C00 : 0x9800;
			else
				tileListMem = (cpu.bitTest(LCD_CONTR_REG, 3)) ? 0x9C00 : 0x9800;

			
			if (inWindow)
				yPos = scanLine - windowY;
			else
				yPos = scrollY + scanLine;

			tileRow = yPos / 8;

			for(int pixel = 0; pixel < 160; pixel++){

				if (inWindow)
					xPos = pixel - windowX;
				else
					xPos = scrollX + pixel;


				tileCol = xPos / 8;

				tileAddr = tileListMem + tileCol + (32 * tileRow); 

				if (signed)
					tileDataAddr = (cpu.memory.readByte(tileAddr) + 128) * TILE_SIZE;
				else
					tileDataAddr = cpu.memory.readByte(tileAddr) * TILE_SIZE;

				tileLine = (yPos % 8) * 2;
				data1 = cpu.memory.readByte(tileAddr + tileLine);
				data2 = cpu.memory.readByte(tileAddr + tileLine + 1);

				colourBit = xPos % 8;

				colourBit = (data2 >>> colourBit) | (data1 >>> colourBit);

				tileSet[pixel][scanLine] = getColour(colourBit);

	/* Every two bits in the palette data byte represent a colour. 
	Bits 7-6 maps to colour id 11, bits 5-4 map to colour id 10, 
	bits 3-2 map to colour id 01 and bits 1-0 map to colour id 00.*/


			}
		}

		private int[] getColour(int colourID){

			int colNum = 0;
			int[] returnCol = new int[] {0, 0, 0};
			int palette = cpu.memory.readByte(CLR_PLT_BKG);

			switch(colourID){
				case 0: colNum = palette & 0x3; break;
				case 1: colNum = (palette & 0xC) >>> 2; break;
				case 2: colNum = (palette & 0x30) >>> 4; break;
				case 3: colNum = (palette & 0xC0) >>> 6; break;
			}

			switch(colNum){
				case 0: returnCol = new int[] {255, 255, 255}; break;
				case 1: returnCol = new int[] {0xCC, 0xCC, 0xCC}; break;
				case 2: returnCol = new int[] {0x77, 0x77, 0x77}; break;
				case 3: returnCol = new int[] {0, 0, 0}; break;
			}
			return returnCol; 
		}

		public void reset() {
			//tileSet = new int[384][8];
			for (int i = 0; i < 384; i++) {
				for (int j = 0; j < 8; j++) {
				//tileSet[i][j] = {0, 0, 0, 0, 0, 0, 0, 0};
				}
			}
		}

		public GPU(Z80 cpu) {
			this.cpu = cpu;
			int width = 160;
			int height = 144;
			JFrame frame = new JFrame("jBoy");

			screen = new gbScreen(width, height, this);

			frame.add(screen);
			frame.pack();
			frame.setVisible(true);
			frame.setResizable(false);
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		}

	}