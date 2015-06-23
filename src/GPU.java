import javax.swing.JFrame;
import java.awt.Color;

public class GPU {

	public static final int TILE_SIZE = 16;

	/* Every two bits in the palette data byte represent a colour. 
	Bits 7-6 maps to colour id 11, bits 5-4 map to colour id 10, 
	bits 3-2 map to colour id 01 and bits 1-0 map to colour id 00.*/
	/* 0  White
 1  Light gray
 2  Dark gray
 3  Black*/
 public static final int CLR_PLT_BKG = 0xFF47;
 public static final int CLR_PLT_SPRT_1 = 0xFF48;
 public static final int CLR_PLT_SPRT_2 = 0xFF49;

 public static final int SCROLL_Y = 0xFF42;
 public static final int SCROLL_X = 0xFF43;
 public static final int WINDOW_Y = 0xFF4A;
	public static final int WINDOW_X = 0xFF4B; // true value is this - 7

	public static final int SCANLINE_NUM = 0xFF44;
	public static final int SCANLINE_COMP = 0xFF45;

	public static final int DMA_REQUEST = 0xFF46;

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

	private int _LCDmode = 2;
	private int _modeClock = 0;
	public int tileSet[][] = new int[160][144];

	public Z80 cpu;
	private gbScreen screen;

	public void updateGraphics(int cycles) {
		
		boolean reqInt = false;	//represents if a LCD interupt is going to be requested

		int status = cpu.memory.readByte(LCD_STATUS);

		//System.out.println("GPU MODE = " + _LCDmode);

		if (isLCDEnabled()) {
			_modeClock += cycles;
			//System.out.println("LCD ENABLED");
		}
		else {
			//System.out.println("LCD DISABLED");
			status = cpu.bitSet(status & 0xFC, 0); //1111 1101
			_LCDmode = 1;
			_modeClock = 0;
			resetScanLine();
			cpu.memory.writeByte(LCD_STATUS, status);
			return;
		}

		//System.out.println("MODECLOCK = " + _modeClock);
		//System.out.println("LINE (scaline) = " + (int) cpu.memory.portsIO[SCANLINE_NUM - 0xFF00] + " (0x"+Integer.toHexString(cpu.memory.portsIO[SCANLINE_NUM - 0xFF00]).toUpperCase()+")");
		//System.out.println("ZERO FLAG = " + cpu.getFlag(cpu.FLAG_ZERO));

		switch (_LCDmode) {

			case 0:	//Horizontal blank mode
			//CPU can access both the video RAM (8000h-9FFFh) and OAM (FE00h-FE9Fh)
			
			if (_modeClock >= 204) {		//required clock cycles for H-blank period
				_modeClock = 0;				//reset the clock
				incScanLine(); 				//increment line number
				renderScan();				//render current scanline
				if (getScanLine() == 143) { //reached line number for V-blank
					_LCDmode = 1; 						//set mode as vertical blank
					cpu.requestInterupt(0);				//request V-blank interupt
					status = cpu.bitSet(status, 0);  	//set LCD mode flag: X1
					status = cpu.bitReset(status, 1);	//set LCD mode flag: 01
					reqInt = cpu.bitTest(status, 4);	//if mode 1 interupt enabled: request LCD interupt
					//cpu.bitReset(LCD_CONTR_REG, 7);  	//disable the LCD during V-blank
					renderFrame(); 						//render image
				} else {
					_LCDmode = 2;						//set mode as access OAM mode
					status = cpu.bitSet(status, 1);		//set LCD mode flag: 1X
					status = cpu.bitReset(status, 0);	//set LCD mode flag: 10
					reqInt = cpu.bitTest(status, 5);	//if mode 2 interupt enabled: request interupt
				}
			}
			break;
			
			case 1:	//Vertical blank mode
			//CPU can access both the display RAM (8000h-9FFFh) and OAM (FE00h-FE9Fh)

			if (_modeClock >= 456) {					//required clock cycles for full H-blank cycle (used for invisible scanlines)
				_modeClock = 0;							//reset the clock
				incScanLine();							//increment line number
				//System.out.println("LINE (scaline) = " + (int) cpu.memory.portsIO[SCANLINE_NUM - 0xFF00]);
				if (getScanLine() == 153) { 			//reached full frame (including invisible lines)
					resetScanLine();					//reset scan line number
					_LCDmode = 2;						//set mode as access OAM mode
					status = cpu.bitSet(status, 1);		//set LCD mode flag: 1X
					status = cpu.bitReset(status, 0);	//set LCD mode flag: 10
					reqInt = cpu.bitTest(status, 5);	//if mode 2 interupt enabled: request LCD interupt
				}
			}
			break;

			case 2: //Scanline (accessing OAM)
			//CPU <cannot> access OAM memory (FE00h-FE9Fh) during this period

			if (_modeClock >= 80) {					//required clock cycles for accessing the OAM
				_LCDmode = 3;						//set mode as access VRAM mode
				status = cpu.bitSet(status, 1);		//set LCD mode flag: X1
				status = cpu.bitSet(status, 0);		//set LCD mode flag: 11
				_modeClock = 0;						//reset the clock
			}
			break;

			case 3: //Scanline (accessing VRAM & OAM)
			//CPU <cannot> access OAM and VRAM during this period. CGB Mode: Cannot access Palette Data (FF69,FF6B) either.

			if (_modeClock >= 172) {				//required clock cycles for accessing VRAM & OAM
				_LCDmode = 0;						//set mode as H-blank
				status = cpu.bitReset(status, 1);	//set LCD mode flag: 0X
				status = cpu.bitReset(status, 0);	//set LCD mode flag: 00
				reqInt = cpu.bitTest(status, 3);	//if mode 0 interupt enabled: request LCD interupt
				_modeClock = 0;						//reset the clock
			}
			break;

		}

		if (reqInt)
			cpu.requestInterupt(1);		//request LCD interupt

		if (cpu.memory.readByte(SCANLINE_NUM) == cpu.memory.readByte(SCANLINE_COMP)){
			status = cpu.bitSet(status, 2);		//set the coincidence flag
			if(cpu.bitTest(status, 6))			//check if coincidence interupt is enabled
				cpu.requestInterupt(1);			//request LCD interupt
			}
			else
			status = cpu.bitReset(status, 2);	//if not equal then reset coincidence flag

		cpu.memory.writeByte(LCD_STATUS, status);	// write LCDstatus back to memory

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

	private void renderSprites(){}

	private void renderTiles(){
		int scrollY = cpu.memory.readByte(SCROLL_Y);
		int scrollX = cpu.memory.readByte(SCROLL_X);
		int windowX = cpu.memory.readByte(WINDOW_X) - 7; //shifted 7 pixels
		int windowY = cpu.memory.readByte(WINDOW_Y);
		int scanLine = cpu.memory.readByte(SCANLINE_NUM);
		boolean signed = false;
		boolean inWindow = false;
		int tileData;
		int tileMemAddr;
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

		if (cpu.bitTest(cpu.memory.readByte(LCD_CONTR_REG), 5) && (scanLine >= windowY))
			inWindow = true;

		if (cpu.bitTest(cpu.memory.readByte(LCD_CONTR_REG), 4))
			tileData = 0x8000;
		else {
			signed = true;
			tileData = 0x8800;
		}

		if (inWindow)
			tileMemAddr = (cpu.bitTest(cpu.memory.readByte(LCD_CONTR_REG), 6)) ? 0x9C00 : 0x9800;
		else
			tileMemAddr = (cpu.bitTest(cpu.memory.readByte(LCD_CONTR_REG), 3)) ? 0x9C00 : 0x9800;

		if (inWindow)
			yPos = scanLine - windowY;
		else
			yPos = scanLine + scrollY;

		tileRow = yPos / 8;

		for(int pixel = 0; pixel < 160; pixel++){

			xPos = scrollX + pixel;

			if (inWindow && (pixel >= windowX))
				xPos = pixel - windowX;

			tileCol = xPos / 8;

			tileAddr = tileMemAddr + tileCol + (32 * tileRow); 

			if (signed)
				tileDataAddr = ((byte) cpu.memory.readByte(tileAddr)) + 128;
			else
				tileDataAddr = (short) cpu.memory.readByte(tileAddr);

			tileDataAddr &= 0xFF;

			tileLine = yPos % 8;
			data1 = cpu.memory.readByte(tileData + (tileDataAddr*TILE_SIZE) + (tileLine*2));
			data2 = cpu.memory.readByte(tileData + (tileDataAddr*TILE_SIZE) + (tileLine*2) + 1);

			colourBit = xPos % 8;

			colourBit -= 7;
			colourBit *= -1;
			
			int colourNum = cpu.bitGet(data2,colourBit);
			colourNum <<= 1;
			colourNum |= cpu.bitGet(data1,colourBit);

			Color col = getColour(colourNum);

			tileSet[pixel][scanLine] = col.getRGB();

			/* Every two bits in the palette data byte represent a colour. 
			Bits 7-6 maps to colour id 11, bits 5-4 map to colour id 10, 
			bits 3-2 map to colour id 01 and bits 1-0 map to colour id 00.*/
		}
	}

	public short getScanLine(){
		return cpu.memory.portsIO[SCANLINE_NUM - 0xFF00];
	}

	public void incScanLine(){
		cpu.memory.portsIO[SCANLINE_NUM - 0xFF00]++;
	}

	public void resetScanLine(){
		cpu.memory.portsIO[SCANLINE_NUM - 0xFF00] = 0;
	}

	private Color getColour(int colourID){

		int colNum = 0;
		Color returnCol = Color.BLACK;
		int palette = cpu.memory.readByte(CLR_PLT_BKG);

		switch(colourID){
			case 0: colNum = palette & 0x3;				break;
			case 1: colNum = (palette & 0xC) >>> 2;		break;
			case 2: colNum = (palette & 0x30) >>> 4;	break;
			case 3: colNum = (palette & 0xC0) >>> 6;	break;
		}

		switch(colNum){
			case 0: returnCol = Color.WHITE; 		break; 
			case 1: returnCol = Color.LIGHT_GRAY;  	break; 
			case 2: returnCol = Color.DARK_GRAY;  	break; 
			case 3: returnCol = Color.BLACK; 		break; 
		}
		return returnCol; 
	}

	public void reset() {}

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
